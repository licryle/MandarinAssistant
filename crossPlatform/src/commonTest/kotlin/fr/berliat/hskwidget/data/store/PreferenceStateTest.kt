package fr.berliat.hskwidget.data.store

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceStateTest {

    private lateinit var fakeDataStore: FakeDataStore
    private val testKey = stringPreferencesKey("test_key")

    @BeforeTest
    fun setup() {
        fakeDataStore = FakeDataStore()
    }

    @Test
    fun testInitialValueLoaded() = runTest(UnconfinedTestDispatcher()) {
        val initialValue = "default"
        val state = PreferenceState(fakeDataStore, testKey, initialValue, coroutineScope = backgroundScope)

        state.ensureLoaded()
        assertEquals(initialValue, state.value)
    }

    @Test
    fun testValueUpdatesPersistToStore() = runTest(UnconfinedTestDispatcher()) {
        val state = PreferenceState(fakeDataStore, testKey, "default", coroutineScope = backgroundScope)
        state.ensureLoaded()

        state.value = "new_value"

        // With UnconfinedTestDispatcher, the optimistic update is immediate
        assertEquals("new_value", state.value)

        // advanceUntilIdle will ensure the write loop (which suspends on DataStore.edit) completes
        advanceUntilIdle()

        // Check the value in DataStore
        assertEquals("new_value", fakeDataStore.latestPreferences[testKey])
    }

    @Test
    fun testStoreUpdatesReflectInState() = runTest(UnconfinedTestDispatcher()) {
        val state = PreferenceState(fakeDataStore, testKey, "default", coroutineScope = backgroundScope)
        state.ensureLoaded()

        // Simulate external change in DataStore
        fakeDataStore.edit { it[testKey] = "external_change" }

        // Wait for the observation loop in PreferenceState to catch the change
        advanceUntilIdle()

        assertEquals("external_change", state.value)
    }

    @Test
    fun testWithConverter() = runTest(UnconfinedTestDispatcher()) {
        val intKey = androidx.datastore.preferences.core.intPreferencesKey("int_key")
        val converter = PreferenceConverter<Int, String>(
            fromStore = { it.toString() },
            toStore = { it.toInt() }
        )

        val state = PreferenceState(fakeDataStore, intKey, "0", converter, coroutineScope = backgroundScope)
        state.ensureLoaded()

        state.value = "42"
        advanceUntilIdle()

        assertEquals(42, fakeDataStore.latestPreferences[intKey])
    }

    @Test
    fun testOverwriteWith() = runTest(UnconfinedTestDispatcher()) {
        val targetState = PreferenceState(fakeDataStore, testKey, "initial", coroutineScope = backgroundScope)
        
        val otherDataStore = FakeDataStore()
        val otherKey = stringPreferencesKey("other_key")
        val sourceState = PreferenceState(otherDataStore, otherKey, "migrated", coroutineScope = backgroundScope)
        
        targetState.ensureLoaded()
        sourceState.ensureLoaded()
        
        assertEquals("initial", targetState.value)
        
        // Execute the overwrite
        targetState.overwriteWith(sourceState)
        
        assertEquals("migrated", targetState.value, "Target state should now hold the value from source state")
        
        // Ensure it also persisted to target store
        advanceUntilIdle()
        assertEquals("migrated", fakeDataStore.latestPreferences[testKey])
    }

    @Test
    fun testRefreshFromExternalSnapshotDoesNotWrite() = runTest(UnconfinedTestDispatcher()) {
        val state = PreferenceState(fakeDataStore, testKey, "default", coroutineScope = backgroundScope)
        state.ensureLoaded()
        assertEquals("default", state.value)

        state.refreshFrom(mutablePreferencesOf(testKey to "external"))

        assertEquals("external", state.value)
        assertNull(fakeDataStore.latestPreferences[testKey], "refreshFrom must not persist the value")
    }
}
