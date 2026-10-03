package fr.berliat.hskwidget.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.berliat.hskwidget.core.AppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

typealias WidgetPreferencesStoreProvider = suspend (Int) -> WidgetPreferencesStore

class WidgetPreferencesStore private constructor(
    store: DataStore<Preferences>, 
    val widgetId : Int,
    scope: CoroutineScope? = null
): PrefixedPreferencesStore(store, "widget_$widgetId", scope) {
    companion object {
        private val mutex = Mutex()
        internal val instances = mutableMapOf<Pair<DataStore<Preferences>, Int>, WidgetPreferencesStore>()

        suspend fun getInstance(
            store: DataStore<Preferences>, 
            widgetId: Int,
            scope: CoroutineScope? = null
        ): WidgetPreferencesStore = withContext(AppDispatchers.IO) {
            instances[Pair(store, widgetId)]?.let { return@withContext it }

            return@withContext mutex.withLock {
                instances[Pair(store, widgetId)] ?: WidgetPreferencesStore(store, widgetId, scope).also { instance ->
                    instances[Pair(store, widgetId)] = instance
                }
            }
        }
    }

    val currentWord = registerPreference(::stringPreferencesKey, "current_word", "")
    val refreshInterval = registerPreference(::longPreferencesKey, "refresh_interval", 1800000L) // 30 min
}
