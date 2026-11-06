package fr.berliat.hskwidget.data.store

import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.data.type.ClassLevel
import fr.berliat.hskwidget.data.type.ClassType
import fr.berliat.hskwidget.domain.SearchQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesStoreTest {

    private lateinit var fakeDataStore: FakeDataStore

    @BeforeTest
    fun setup() {
        fakeDataStore = FakeDataStore()
        // Clear singleton instances to ensure a fresh start and avoid scope leakage
        AppPreferencesStore.instances.clear()
        PrefixedPreferencesStore.instances.clear()
        PrefixedPreferencesStore.dataStoreFiles.clear()
    }

    @Test
    fun testInstantConverter() = runTest(UnconfinedTestDispatcher()) {
        val store = AppPreferencesStore.getInstance(fakeDataStore, scope = backgroundScope)
        val instant = Instant.fromEpochMilliseconds(123456789L)
        store.dbBackupCloudLastSuccess.value = instant
        advanceUntilIdle()

        val savedLong = fakeDataStore.latestPreferences[longPreferencesKey("database_backup_cloud_lastsuccess")]
        assertEquals(123456789L, savedLong)
        assertEquals(instant, store.dbBackupCloudLastSuccess.value)
    }

    @Test
    fun testClassLevelConverter() = runTest(UnconfinedTestDispatcher()) {
        val store = AppPreferencesStore.getInstance(fakeDataStore, scope = backgroundScope)
        store.lastAnnotatedClassLevel.value = ClassLevel.Intermediate2
        advanceUntilIdle()

        val savedString = fakeDataStore.latestPreferences[stringPreferencesKey("class_level")]
        assertEquals("Intermediate2", savedString)
        assertEquals(ClassLevel.Intermediate2, store.lastAnnotatedClassLevel.value)
    }

    @Test
    fun testClassTypeConverter() = runTest(UnconfinedTestDispatcher()) {
        val store = AppPreferencesStore.getInstance(fakeDataStore, scope = backgroundScope)
        store.lastAnnotatedClassType.value = ClassType.Listening
        advanceUntilIdle()

        val savedString = fakeDataStore.latestPreferences[stringPreferencesKey("class_type")]
        assertEquals("Listening", savedString)
        assertEquals(ClassType.Listening, store.lastAnnotatedClassType.value)
    }

    @Test
    fun testTextSizeConverter() = runTest(UnconfinedTestDispatcher()) {
        val store = AppPreferencesStore.getInstance(fakeDataStore, scope = backgroundScope)
        val size = 42f.sp
        store.readerTextSize.value = size
        advanceUntilIdle()

        val savedFloat = fakeDataStore.latestPreferences[floatPreferencesKey("reader_text_size")]
        assertEquals(42f, savedFloat)
        assertEquals(size, store.readerTextSize.value)
    }

    @Test
    fun testSearchQueryConverter() = runTest(UnconfinedTestDispatcher()) {
        val store = AppPreferencesStore.getInstance(fakeDataStore, scope = backgroundScope)
        val query = SearchQuery.fromString("test query")
        store.searchQuery.value = query
        advanceUntilIdle()

        val savedString = fakeDataStore.latestPreferences[stringPreferencesKey("search_query")]
        assertEquals(query.toString(), savedString)
        assertEquals(query.query, store.searchQuery.value.query)
    }

    @Test
    fun testDictionaryLocaleConverter() = runTest(UnconfinedTestDispatcher()) {
        val store = AppPreferencesStore.getInstance(fakeDataStore, scope = backgroundScope)
        
        // Test non-null
        store.dictionaryLocale.value = Locale.FRENCH
        advanceUntilIdle()
        var savedString = fakeDataStore.latestPreferences[stringPreferencesKey("dictionary_locale")]
        assertEquals("fr", savedString)
        assertEquals(Locale.FRENCH, store.dictionaryLocale.value)

        // Test null
        store.dictionaryLocale.value = null
        advanceUntilIdle()
        savedString = fakeDataStore.latestPreferences[stringPreferencesKey("dictionary_locale")]
        assertEquals("", savedString)
        assertEquals(null, store.dictionaryLocale.value)
    }

    @Test
    fun testReloadFromDiskRefreshesDictionaryLocale() = runTest(UnconfinedTestDispatcher()) {
        val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "app-preferences-test-${Random.nextLong()}"
        val file = tempDir / "app.preferences_pb"
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        FileSystem.SYSTEM.createDirectories(tempDir)

        try {
            // The app process has persisted French.
            FileSystem.SYSTEM.write(file) {
                PreferencesSerializer.writeTo(
                    mutablePreferencesOf(stringPreferencesKey("dictionary_locale") to Locale.FRENCH.code),
                    this
                )
            }

            val dataStore = PreferenceDataStoreFactory.createWithPath(
                scope = dataStoreScope,
                produceFile = { file }
            )
            PrefixedPreferencesStore.dataStoreFiles[dataStore] = file

            val store = AppPreferencesStore.getInstance(dataStore, scope = backgroundScope)
            advanceUntilIdle()
            assertEquals(Locale.FRENCH, store.dictionaryLocale.value)

            // Another process (the app) changes the dictionary language.
            FileSystem.SYSTEM.write(file) {
                PreferencesSerializer.writeTo(
                    mutablePreferencesOf(stringPreferencesKey("dictionary_locale") to Locale.CN_HSK3.code),
                    this
                )
            }

            assertTrue(store.reloadFromDisk())
            assertEquals(Locale.CN_HSK3, store.dictionaryLocale.value)
        } finally {
            dataStoreScope.cancel()
            AppPreferencesStore.instances.clear()
            PrefixedPreferencesStore.instances.clear()
            PrefixedPreferencesStore.dataStoreInstances.clear()
            PrefixedPreferencesStore.dataStoreFiles.clear()
            FileSystem.SYSTEM.deleteRecursively(tempDir, mustExist = false)
        }
    }
}
