package fr.berliat.hskwidget.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import fr.berliat.hskwidget.core.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Two-way bound preference state: updates to `.value` automatically persist to DataStore but makes
 * the value available to listeners instantly.
 */
class PreferenceState<S, T>(
    private val store: DataStore<Preferences>,
    internal val key: Preferences.Key<S>,
    private val initialValue: T,
    converter: PreferenceConverter<S, T>? = null,
    coroutineScope: CoroutineScope? = null
) {
    private val conv = converter ?: PreferenceConverter({ it as T }, { it as S })
    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + AppDispatchers.IO)
    
    private val _flow = MutableStateFlow(initialValue)
    // Expose the preference as a read-only StateFlow.
    fun asStateFlow(): StateFlow<T> = _flow.asStateFlow()

    private val isLoaded = CompletableDeferred<Unit>()
    
    // Guard against stale store updates overwriting newer optimistic updates
    private var lastOptimisticValue: T? = null
    
    // Use a conflated channel to ensure we only process the latest write request
    private val writeChannel = Channel<T>(Channel.CONFLATED)

    suspend fun ensureLoaded() = isLoaded.await()

    /**
     * Replaces the cached value with one read outside DataStore, without writing it back.
     * Used by [PrefixedPreferencesStore.reloadFromBackingFile] when a widget process re-reads
     * the shared preferences file.
     */
    internal fun refreshFrom(prefs: Preferences) {
        val valueFromStore = prefs[key]?.let { conv.fromStore(it) } ?: initialValue

        // A pending optimistic write made by this process is newer than the disk snapshot.
        val optimistic = lastOptimisticValue
        if (optimistic == null || valueFromStore == optimistic) {
            _flow.value = valueFromStore
            lastOptimisticValue = null
        }

        if (!isLoaded.isCompleted) {
            isLoaded.complete(Unit)
        }
    }

    init {
        // 1. Observe Store: Update local state when DataStore changes
        scope.launch {
            store.data
                .map { prefs -> prefs[key]?.let { conv.fromStore(it) } ?: initialValue }
                .distinctUntilChanged()
                .collect { valueFromStore ->
                    val optimistic = lastOptimisticValue
                    // Only apply store update if it matches our last optimistic set or if no set is pending
                    if (optimistic == null || valueFromStore == optimistic) {
                        _flow.value = valueFromStore
                        lastOptimisticValue = null
                    }
                    
                    if (!isLoaded.isCompleted) {
                        isLoaded.complete(Unit)
                    }
                }
        }

        // 2. Handle Writes: Persist changes to DataStore sequentially
        scope.launch {
            for (v in writeChannel) {
                store.edit { it[key] = conv.toStore(v) }
            }
        }
    }

    /**
     * Internal helper to overwrite this state's value with another state's value
     * without knowing the specific type T at compile time.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun overwriteWith(other: PreferenceState<*, *>) {
        this.value = other.value as T
    }

    var value: T
        get() = _flow.value
        set(v) {
            if (_flow.value != v) {
                // Optimistic update for immediate UI feedback
                _flow.value = v
                lastOptimisticValue = v
                // Queue the write operation
                writeChannel.trySend(v)
            }
        }
}
