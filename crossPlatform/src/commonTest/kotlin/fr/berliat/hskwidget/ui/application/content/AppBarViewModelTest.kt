package fr.berliat.hskwidget.ui.application.content

import androidx.compose.ui.text.input.TextFieldValue
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.data.store.FakeDataStore
import fr.berliat.hskwidget.data.store.PrefixedPreferencesStore
import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.ui.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppBarViewModelTest {

    @BeforeTest
    fun setup() {
        AppPreferencesStore.instances.clear()
        PrefixedPreferencesStore.instances.clear()
    }

    private suspend fun TestScope.newViewModel(
        current: Screen = Screen.Dictionary(),
        navLog: MutableList<Screen> = mutableListOf()
    ): Pair<AppBarViewModel, AppPreferencesStore> {
        val prefs = AppPreferencesStore.getInstance(FakeDataStore(), backgroundScope)
        val viewModel = AppBarViewModel(
            prefsStore = prefs,
            currentScreen = MutableStateFlow(current),
            navigate = navLog::add
        )
        return viewModel to prefs
    }

    @Test
    fun typingOnDictionaryUpdatesPrefsInPlaceWithoutLosingTrailingChars() = runTest {
        val navLog = mutableListOf<Screen>()
        val (viewModel, prefs) = newViewModel(Screen.Dictionary(), navLog)

        // Type with a pause mid-word: each chunk passes the debounce, and no
        // intermediate write may clobber the trailing characters.
        viewModel.onQueryChange(TextFieldValue("nih"))
        advanceTimeBy(400)
        viewModel.onQueryChange(TextFieldValue("nihao"))
        advanceTimeBy(400)

        assertEquals("nihao", prefs.searchQuery.value.query)
        assertEquals("nihao", viewModel.localText.value.text)
        assertTrue(navLog.isEmpty())
    }

    @Test
    fun typingOffDictionaryNavigatesAndLeavesPrefsUntouched() = runTest {
        val navLog = mutableListOf<Screen>()
        val (viewModel, prefs) = newViewModel(Screen.Lists, navLog)

        viewModel.onQueryChange(TextFieldValue("nihao"))
        advanceTimeBy(400)

        assertEquals(SearchQuery(), prefs.searchQuery.value)
        assertEquals<List<Screen>>(listOf(Screen.Dictionary("nihao")), navLog)
    }

    @Test
    fun externalQueryChangeSyncsField() = runTest {
        val (viewModel, prefs) = newViewModel()

        prefs.searchQuery.value = SearchQuery(query = "nihao")
        advanceUntilIdle()

        assertEquals("nihao", viewModel.localText.value.text)
    }
}
