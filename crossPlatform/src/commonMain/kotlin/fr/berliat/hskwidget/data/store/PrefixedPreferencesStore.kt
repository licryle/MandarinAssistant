package fr.berliat.hskwidget.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

import co.touchlab.kermit.Logger
import fr.berliat.hskwidget.core.AppDispatchers

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

open class PrefixedPreferencesStore internal constructor(
    private val store: DataStore<Preferences>,
    private val prefix: String,
    private val scope: CoroutineScope? = null
) {
    internal val registeredPreferences = mutableMapOf<String, PreferenceState<*, *>>()

    companion object {
        private val mutex = Mutex()
        internal val instances = mutableMapOf<Pair<DataStore<Preferences>, String>, PrefixedPreferencesStore>()

        suspend fun getInstance(
            store: DataStore<Preferences>, 
            prefix: String, 
            scope: CoroutineScope? = null
        ): PrefixedPreferencesStore {
            val key = Pair(store, prefix)
            instances[key]?.let { return it }

            return mutex.withLock {
                instances[key] ?: PrefixedPreferencesStore(store, prefix, scope).also { instance ->
                    instances[key] = instance
                }
            }
        }

        private val dataStoreMutex = Mutex()
        internal val dataStoreInstances = mutableMapOf<String, DataStore<Preferences>>()
        /** Backing file of every DataStore created by [getDataStore], used by reloadFromDisk. */
        internal val dataStoreFiles = mutableMapOf<DataStore<Preferences>, Path>()

        suspend fun getDataStore(file: PlatformFile): DataStore<Preferences> {
			val fileAbsPath = file.absolutePath().
				removePrefix("file://").
                removePrefix("file:/").
                removePrefix("file:").
				removePrefix("/").
				removePrefix("//")
            val path = "/$fileAbsPath".toPath()

            dataStoreInstances[fileAbsPath]?.let { return it }

            return dataStoreMutex.withLock {
                dataStoreInstances[fileAbsPath] ?: PreferenceDataStoreFactory.createWithPath(
                    produceFile = { path }
                ).also { instance ->
                    dataStoreInstances[fileAbsPath] = instance
                    dataStoreFiles[instance] = path
                }
            }
        }

        /**
         * Copies all preferences from one DataStore to another.
         * Useful for full migrations when keys are unknown or dynamic (like widgets).
         */
        suspend fun copyAll(source: DataStore<Preferences>, target: DataStore<Preferences>) {
            val sourceData = source.data.first()
            target.edit { prefs ->
                sourceData.asMap().forEach { (key, value) ->
                    @Suppress("UNCHECKED_CAST")
                    prefs[key as Preferences.Key<Any>] = value
                }
            }
        }
    }

    /**
     * Clears all preferences registered in this store.
     * 
     * Note: This only clears keys that have been explicitly registered via the `register*` methods.
     * This prevents accidental deletion of keys belonging to other stores with overlapping 
     * prefixes (e.g., a "user" store won't clear "user_profile" keys).
     */
    suspend fun clear() {
        store.edit { prefs ->
            registeredPreferences.values.forEach { state ->
                prefs.remove(state.key)
            }
        }
    }

    /**
     * Overwrites the values of this store with the values from another store.
     * 
     * It maps registered preferences by their short names (without prefix).
     */
    suspend fun overwriteWith(other: PrefixedPreferencesStore) {
        // Ensure both are loaded
        this.ensureAllLoaded()
        other.ensureAllLoaded()

        registeredPreferences.forEach { (keyName, state) ->
            // Extract short name by removing our prefix
            val shortName = if (prefix.isEmpty()) keyName else keyName.removePrefix("${prefix}_")
            
            // Find corresponding state in other store
            val otherKeyName = other.prefixKey(shortName)
            val otherState = other.registeredPreferences[otherKeyName]

            if (otherState != null) {
                // We use dynamic dispatch via a helper in PreferenceState to bypass type safety 
                // since we're dealing with generic PreferenceState<*, *>
                state.overwriteWith(otherState)
            }
        }
    }

    fun prefixKey(key: String): String {
        return if (prefix.isEmpty()) key else "${prefix}_$key"
    }

    /**
     * Waits for all registered preferences to be loaded from the DataStore.
     */
    suspend fun ensureAllLoaded() = coroutineScope {
        registeredPreferences.values.map { async { it.ensureLoaded() } }.awaitAll()
    }

    /**
     * Re-reads this store's backing file from disk and refreshes the registered preferences.
     *
     * DataStore has no public reload API: `DataStore.data` always returns the in-memory
     * snapshot. iOS widget extensions run in a separate process and can outlive the app
     * process, so that snapshot can be stale even after the app persisted a new value.
     *
     * The file is decoded with DataStore's own [PreferencesSerializer], so the on-disk format
     * is not reimplemented. DataStore writes to a temporary file and atomically moves it into
     * place, so a reader never observes a partially written file: it sees either the previous
     * or the new complete snapshot. The only remaining race is that the app's write may not
     * have been flushed yet when this is called.
     *
     * @return true if the file was read and the preferences were refreshed; false if this store
     * has no tracked backing file or the read failed, in which case the cached values are kept.
     */
    protected suspend fun reloadFromBackingFile(): Boolean {
        val path = dataStoreMutex.withLock { dataStoreFiles[store] } ?: return false

        val prefs = try {
            withContext(AppDispatchers.IO) {
                FileSystem.SYSTEM.read(path) { PreferencesSerializer.readFrom(this) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(
                tag = "Preferences",
                messageString = "Could not reload $path from disk; keeping cached values",
                throwable = e
            )
            return false
        }

        registeredPreferences.values.forEach { it.refreshFrom(prefs) }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    fun <S, T> registerPreference(
        factory: (String) -> Preferences.Key<S>,
        name: String,
        default: T,
        converter: PreferenceConverter<S, T>? = null
    ): PreferenceState<S, T> {
        val keyName = prefixKey(name)
        return (registeredPreferences.getOrPut(keyName) {
            PreferenceState(store, factory(keyName), default, converter, scope)
        } as PreferenceState<S, T>)
    }

    fun <T> registerBooleanPref(name: String, default: T, converter: PreferenceConverter<Boolean, T>? = null) =
        registerPreference(::booleanPreferencesKey, name, default, converter)

    fun <T> registerIntPref(name: String, default: T, converter: PreferenceConverter<Int, T>? = null) =
        registerPreference(::intPreferencesKey, name, default, converter)

    fun <T> registerLongPref(name: String, default: T, converter: PreferenceConverter<Long, T>? = null) =
        registerPreference(::longPreferencesKey, name, default, converter)

    fun <T> registerFloatPref(name: String, default: T, converter: PreferenceConverter<Float, T>? = null) =
        registerPreference(::floatPreferencesKey, name, default, converter)

    fun <T> registerStringPref(name: String, default: T, converter: PreferenceConverter<String, T>? = null) =
        registerPreference(::stringPreferencesKey, name, default, converter)

    fun <T> registerStringSetPref(name: String, default: T, converter: PreferenceConverter<Set<String>, T>? = null) =
        registerPreference(::stringSetPreferencesKey, name, default, converter)
}
