package fr.berliat.hskwidget.data.store

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrefixedPreferencesStoreTest {

    private lateinit var fakeDataStore: FakeDataStore

    @BeforeTest
    fun setup() {
        fakeDataStore = FakeDataStore()
        // Ensure clean caches for each test
        PrefixedPreferencesStore.instances.clear()
        PrefixedPreferencesStore.dataStoreInstances.clear()
        PrefixedPreferencesStore.dataStoreFiles.clear()
    }

    @AfterTest
    fun tearDown() {
        PrefixedPreferencesStore.instances.clear()
        PrefixedPreferencesStore.dataStoreInstances.clear()
        PrefixedPreferencesStore.dataStoreFiles.clear()
    }

    @Test
    fun testGetInstanceCaching() = runTest {
        val i1 = PrefixedPreferencesStore.getInstance(fakeDataStore, "p1", backgroundScope)
        val i2 = PrefixedPreferencesStore.getInstance(fakeDataStore, "p1", backgroundScope)
        val i3 = PrefixedPreferencesStore.getInstance(fakeDataStore, "p2", backgroundScope)
        assertSame(i1, i2, "Should return same instance for same prefix")
        assertNotSame(i1, i3, "Should return different instances for different prefixes")
    }

    @Test
    fun testPrefixKeyLogic() = runTest {
        val store = PrefixedPreferencesStore.getInstance(fakeDataStore, "myPref", backgroundScope)
        assertEquals("myPref_myKey", store.prefixKey("myKey"))
        
        val emptyStore = PrefixedPreferencesStore.getInstance(fakeDataStore, "", backgroundScope)
        assertEquals("myKey", emptyStore.prefixKey("myKey"))
    }

    @Test
    fun testClearRemovesOnlyPrefixedKeys() = runTest {
        val store1 = PrefixedPreferencesStore.getInstance(fakeDataStore, "prefix1", backgroundScope)
        store1.registerStringPref("myPref", "")

        val store2 = PrefixedPreferencesStore.getInstance(fakeDataStore, "prefix2", backgroundScope)
        store2.registerStringPref("myPref", "")

        // Pre-populate DataStore
        fakeDataStore.edit {
            it[stringPreferencesKey("prefix1_myPref")] = "value1"
            it[stringPreferencesKey("prefix2_myPref")] = "value2"
            it[stringPreferencesKey("unrelated")] = "unrelatedValue"
        }

        store1.clear()

        val prefs = fakeDataStore.data.first()
        assertNull(prefs[stringPreferencesKey("prefix1_myPref")], "prefix1_myPref should be cleared")
        assertEquals("value2", prefs[stringPreferencesKey("prefix2_myPref")], "prefix2_myPref should remain")
        assertEquals("unrelatedValue", prefs[stringPreferencesKey("unrelated")], "unrelated key should remain")
    }

    @Test
    fun testClearWithOverlappingPrefixes() = runTest {
        val parentStore = PrefixedPreferencesStore.getInstance(fakeDataStore, "user", backgroundScope)
        val childStore = PrefixedPreferencesStore.getInstance(fakeDataStore, "user_profile", backgroundScope)

        parentStore.registerStringPref("name", "none")
        childStore.registerStringPref("bio", "none")

        fakeDataStore.edit {
            it[stringPreferencesKey("user_name")] = "John"
            it[stringPreferencesKey("user_profile_bio")] = "Developer"
        }

        parentStore.clear()

        val prefs = fakeDataStore.data.first()
        assertNull(prefs[stringPreferencesKey("user_name")], "Parent pref should be cleared")
        assertEquals("Developer", prefs[stringPreferencesKey("user_profile_bio")], "Child pref should NOT be cleared")
    }
    
    @Test
    fun testValuePersistence() = runTest {
        val store = PrefixedPreferencesStore.getInstance(fakeDataStore, "a_prefix", backgroundScope)
        val myStringPref = store.registerStringPref("a_string", "default")
        store.ensureAllLoaded()

        assertEquals("default", myStringPref.value)

        myStringPref.value = "new_value"
        
        // In-memory update is immediate with FakeDataStore if we don't need to wait for disk
        assertEquals("new_value", myStringPref.value)

        // Clear instance cache to force recreation of PrefixedPreferencesStore
        PrefixedPreferencesStore.instances.clear()

        val newStore = PrefixedPreferencesStore.getInstance(fakeDataStore, "a_prefix", backgroundScope)
        val newMyStringPref = newStore.registerStringPref("a_string", "default")
        newStore.ensureAllLoaded()

        assertEquals("new_value", newMyStringPref.value)
    }

    @Test
    fun testEnsureAllLoaded() = runTest {
        // Pre-populate datastore
        fakeDataStore.edit {
            it[stringPreferencesKey("loadTest_pref1")] = "value1"
            it[stringPreferencesKey("loadTest_pref2")] = "value2"
        }

        val store = PrefixedPreferencesStore.getInstance(fakeDataStore, "loadTest", backgroundScope)
        val pref1 = store.registerStringPref("pref1", "default1")
        val pref2 = store.registerStringPref("pref2", "default2")

        // Values should be defaults initially (before loading)
        assertEquals("default1", pref1.value)
        assertEquals("default2", pref2.value)

        store.ensureAllLoaded()

        // After loading, values should be updated from DataStore
        assertEquals("value1", pref1.value)
        assertEquals("value2", pref2.value)
    }

    @Test
    fun testOverwriteWith() = runTest {
        val otherDataStore = FakeDataStore()
        val currentStore = PrefixedPreferencesStore.getInstance(fakeDataStore, "live", backgroundScope)
        val otherStore = PrefixedPreferencesStore.getInstance(otherDataStore, "backup", backgroundScope)

        val pref1 = currentStore.registerStringPref("pref1", "default1")
        val pref2 = currentStore.registerIntPref("pref2", 0)

        val otherPref1 = otherStore.registerStringPref("pref1", "other1")
        val otherPref2 = otherStore.registerIntPref("pref2", 100)

        // Set values in "other" store
        otherPref1.value = "newValue1"
        otherPref2.value = 42

        // Overwrite
        currentStore.overwriteWith(otherStore)

        assertEquals("newValue1", pref1.value, "String pref should be overwritten")
        assertEquals(42, pref2.value, "Int pref should be overwritten")
    }

    @Test
    fun testOverwriteWithDifferentPrefixes() = runTest {
        val otherDataStore = FakeDataStore()
        // Source has a prefix, Target has NO prefix
        val targetStore = PrefixedPreferencesStore.getInstance(fakeDataStore, "", backgroundScope)
        val sourceStore = PrefixedPreferencesStore.getInstance(otherDataStore, "old_prefix", backgroundScope)

        val targetPref = targetStore.registerStringPref("my_key", "target_default")
        val sourcePref = sourceStore.registerStringPref("my_key", "source_default")

        sourcePref.value = "migrated_value"

        targetStore.overwriteWith(sourceStore)

        assertEquals("migrated_value", targetPref.value, "Value should migrate correctly regardless of prefixes")
    }

    @Test
    fun testCopyAll() = runTest {
        val sourceDataStore = FakeDataStore()
        val targetDataStore = FakeDataStore()

        val key1 = stringPreferencesKey("widget_1_word")
        val key2 = stringPreferencesKey("widget_2_word")

        sourceDataStore.edit {
            it[key1] = "Hello"
            it[key2] = "World"
        }

        PrefixedPreferencesStore.copyAll(sourceDataStore, targetDataStore)

        val targetPrefs = targetDataStore.data.first()
        assertEquals("Hello", targetPrefs[key1])
        assertEquals("World", targetPrefs[key2])
    }

    @Test
    fun testCopyAllTriggersReflow() = runTest {
        val sourceDataStore = FakeDataStore()
        val targetDataStore = FakeDataStore()
        
        // 1. Setup a live preference store observing the target DataStore
        val store = PrefixedPreferencesStore.getInstance(targetDataStore, "widget_1", backgroundScope)
        val pref = store.registerStringPref("word", "initial")
        store.ensureAllLoaded()
        
        assertEquals("initial", pref.value)

        // 2. Prepare data in source DataStore
        sourceDataStore.edit {
            it[stringPreferencesKey("widget_1_word")] = "migrated"
        }

        // 3. Perform copyAll
        PrefixedPreferencesStore.copyAll(sourceDataStore, targetDataStore)
        
        // Yield to allow background collection coroutines to process the DataStore change
        yield()

        // 4. Verify the PreferenceState reflowed automatically
        assertEquals("migrated", pref.value, "PreferenceState should have automatically updated via reflow")
    }

    @Test
    fun testOverwriteWithTriggersReflow() = runTest {
        val otherDataStore = FakeDataStore()
        val currentStore = PrefixedPreferencesStore.getInstance(fakeDataStore, "live", backgroundScope)
        val otherStore = PrefixedPreferencesStore.getInstance(otherDataStore, "backup", backgroundScope)

        val pref = currentStore.registerStringPref("my_key", "initial")
        currentStore.ensureAllLoaded()

        val otherPref = otherStore.registerStringPref("my_key", "other")
        otherPref.value = "migrated"

        // Overwrite
        currentStore.overwriteWith(otherStore)
        
        // Yield to allow background collection coroutines to process the DataStore change
        yield()

        assertEquals("migrated", pref.value, "PreferenceState should have automatically reflowed after overwriteWith")
    }
}
