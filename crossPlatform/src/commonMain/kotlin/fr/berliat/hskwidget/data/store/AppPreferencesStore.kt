package fr.berliat.hskwidget.data.store

import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*

import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.data.type.ClassLevel
import fr.berliat.hskwidget.data.type.ClassType
import fr.berliat.hskwidget.domain.SearchQuery

import kotlinx.datetime.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope

/**
 * Global singleton for managing app preferences via DataStore.
 *
 * This class provides a type-safe, two-way bound interface to read and write
 * preferences, supporting both primitive and complex types with automatic
 * conversion via `PreferenceConverter`.
 *
 * Key features:
 * - **Singleton access:** Use `AppPreferencesStore.getInstance(store)` to get the global instance.
 * - **Two-way bound state:** Each preference is represented as a `PreferenceState` with `.value` for
 *   synchronous in-memory access and automatic asynchronous persistence to DataStore.
 * - **StateFlow support:** Use `.asStateFlow()` on any `PreferenceState` to observe changes in Compose
 *   or coroutines.
 * - **Type conversion:** Complex types (e.g., `Path`, `Instant`, `ClassLevel`) are handled via an
 *   optional `PreferenceConverter<S, T>` providing `fromStore` and `toStore` transformations.
 * - **Coroutine-friendly:** Reads and writes happen asynchronously without blocking the main thread.
 *
 * Example usage:
 * ```
 * val prefs = AppPreferencesStore.getInstance(store)
 *
 * // Read/write value directly
 * val isActive = prefs.dbBackUpActive.value
 * prefs.dbBackUpActive.value = true
 *
 * // Observe changes in Compose
 * val searchQuery by prefs.searchQuery.asStateFlow().collectAsState()
 * ```
 */

class AppPreferencesStore private constructor(
    store: DataStore<Preferences>,
    scope: CoroutineScope? = null
): PrefixedPreferencesStore(store, "", scope)  {
    companion object {
        private val mutex = Mutex()
        internal val instances = mutableMapOf<DataStore<Preferences>, AppPreferencesStore>()

        suspend fun getInstance(
            store: DataStore<Preferences>,
            scope: CoroutineScope? = null
        ): AppPreferencesStore {
            instances[store]?.let { return it }

            return mutex.withLock {
                instances[store] ?: AppPreferencesStore(store, scope).also { instance ->
                    instance.ensureAllLoaded()
                    instances[store] = instance
                }
            }
        }
    }

    // --- Boolean preferences ---
    val dbBackUpDiskActive = registerBooleanPref("database_backup_disk_active", false)
    val ankiSaveNotes = registerBooleanPref("anki_save_notes", false)
    val searchFilterHasAnnotation = registerBooleanPref("search_filter_hasAnnotation", false)
    val readerSeparateWords = registerBooleanPref("reader_separate_word", false)
    val readerShowAllPinyins = registerBooleanPref("reader_show_pinyins", false)

    // --- Int preferences ---
    val appVersionCode = registerIntPref("appVersionCode", 0)
    val dbBackUpDiskMaxFiles = registerIntPref("database_backup_disk_max_files", 2)

    // --- Long preferences ---
    val ankiModelId = registerLongPref("anki_model_id", -1L)

    // --- Float preferences ---
    val supportTotalSpent = registerFloatPref("support_total_spent", -1f)

    // --- Derived complex types ---
    val dbBackupCloudLastSuccess = registerLongPref(
        "database_backup_cloud_lastsuccess",
        Instant.fromEpochMilliseconds(0L),
        PreferenceConverter({ Instant.fromEpochMilliseconds(it) }, { it.toEpochMilliseconds() })
    )
    val dbBackUpDiskDirectory = registerStringPref(
        "database_backup_disk_directory", null,
        FileKitBookmarkPreferenceConverter()
    )
    val lastAnnotatedClassLevel = registerStringPref("class_level", ClassLevel.NotFromClass,
        PreferenceConverter({ ClassLevel.from(it) }, { it.name })
    )
    val lastAnnotatedClassType = registerStringPref("class_type", ClassType.NotFromClass,
        PreferenceConverter({ ClassType.from(it) }, { it.name })
    )
    val readerTextSize = registerFloatPref("reader_text_size", 30f.sp,
        PreferenceConverter({ it.sp }, { it.value })
    )
    val dictionaryTextSize = registerFloatPref("dictionary_text_size", 16f.sp,
        PreferenceConverter({ it.sp }, { it.value })
    )
    val dictionaryLocale = registerStringPref("dictionary_locale", null,
        PreferenceConverter({ code -> if (code.isEmpty()) null else Locale.fromCode(code) }, { locale -> locale?.code ?: "" })
    )
    val searchQuery = registerStringPref("search_query", SearchQuery.fromString(""),
        PreferenceConverter({ SearchQuery.fromString(it) }, { it.toString() }))
}
