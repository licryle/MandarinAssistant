package fr.berliat.hskwidget.ui.screens.dictionary

import androidx.compose.ui.unit.TextUnit
import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.data.dao.AnnotatedChineseWordDAO

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.ui.theme.AppTypographies
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext


class DictionarySearchViewModel(private val prefsStore: AppPreferencesStore = HSKAppServices.appPreferences,
                          private val annotatedChineseWordDAO: AnnotatedChineseWordDAO = HSKAppServices.database.annotatedChineseWordDAO(),
                          private val widgetProvider: FlashcardWidgetProvider = FlashcardWidgetProvider()
) {
    val searchQuery = prefsStore.searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AnnotatedChineseWord>>(emptyList())
    val searchResults: StateFlow<List<AnnotatedChineseWord>> = _searchResults.asStateFlow()

    private val _hasMoreResults = MutableStateFlow(false)
    val hasMoreResults: StateFlow<Boolean> = _hasMoreResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _wordExists = MutableStateFlow<Boolean?>(null)
    val wordExists: StateFlow<Boolean?> = _wordExists.asStateFlow()

    /**
     * The query that produced the current [searchResults] and [wordExists].
     * Null while a search is in flight. Screens use this to tell "no result"
     * apart from "the result is still from the previous query".
     */
    private val _searchedQuery = MutableStateFlow<String?>(null)
    val searchedQuery: StateFlow<String?> = _searchedQuery.asStateFlow()

    val hasAnnotationFilter: StateFlow<Boolean> = prefsStore.searchFilterHasAnnotation.asStateFlow()

    val dictionaryLocale: StateFlow<Locale?> = prefsStore.dictionaryLocale.asStateFlow()
    val textLocale: StateFlow<Locale> = prefsStore.dictionaryLocale.asStateFlow()
        .map { Locale.resolve(it) }
        .stateIn(CoroutineScope(AppDispatchers.Main), SharingStarted.Eagerly, Locale.resolve(prefsStore.dictionaryLocale.value))


    val textSize: StateFlow<TextUnit> = prefsStore.dictionaryTextSize.asStateFlow()

    private var currentPage = 0
    private val itemsPerPage = 30
    private var currentSearchJob: Job? = null

    fun toggleHasAnnotation(value: Boolean) {
        prefsStore.searchFilterHasAnnotation.value = value

        Logging.logAnalyticsEvent(if (value) Logging.ANALYTICS_EVENTS.DICT_ANNOTATION_ON else Logging.ANALYTICS_EVENTS.DICT_ANNOTATION_OFF)
    }

    fun updateTextSize(increment: Float) {
        val newSize = (prefsStore.dictionaryTextSize.value.value + increment).coerceAtLeast(
            AppTypographies.smallestHanziFontSize.value)
        prefsStore.dictionaryTextSize.value = newSize.sp

        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.DICT_TEXT_SIZE_CHANGE)
    }

    fun updateDictionaryLocale(locale: Locale?) {
        val from = prefsStore.dictionaryLocale.value?.code ?: "app"
        prefsStore.dictionaryLocale.value = locale
        performSearch(IntentSources.IN_APP)
        widgetProvider.redrawAllFlashCardWidgets()

        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.DICT_CHANGE_LANG,
            mapOf("FROM" to from, "TO" to (locale?.code ?: "app"))
        )
    }

    fun performSearch(source: IntentSources) {
        val querySnapshot = searchQuery.value.query.trim()
        val inList = searchQuery.value.inListName != null
        val annotatedOnly = prefsStore.searchFilterHasAnnotation.value
        val localeCode = Locale.resolve(prefsStore.dictionaryLocale.value).code
        val baseParams = mapOf(
            "QUERY_LEN" to querySnapshot.length.toString(),
            "HAS_ANNOTATION_FILTER" to annotatedOnly.toString(),
            "IN_LIST" to inList.toString(),
            "LOCALE" to localeCode,
            "EXACT_WORD" to searchQuery.value.exactWord.toString(),
            "SOURCE" to source.toString()
        )
        currentSearchJob?.cancel()
        // Results below still belong to the previous query until this one lands.
        _searchedQuery.value = null
        currentSearchJob = CoroutineScope(AppDispatchers.IO).launch {
            _isLoading.value = true
            currentPage = 0

            val results = fetchResultsForPage()
            val wordExists = if (querySnapshot.isEmpty()) {
                null
            } else {
                annotatedChineseWordDAO.getFromSimplified(querySnapshot) != null
            }

            withContext(Dispatchers.Main) {
                _hasMoreResults.value = results.size == itemsPerPage
                _searchResults.value = results
                _wordExists.value = wordExists
                _searchedQuery.value = querySnapshot
                _isLoading.value = false

                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.DICT_SEARCH,
                    baseParams + mapOf("RESULT_COUNT" to results.size.toString())
                )
                if (results.isEmpty()) {
                    Logging.logAnalyticsEvent(
                        Logging.ANALYTICS_EVENTS.DICT_EMPTY_RESULT,
                        baseParams
                    )
                }
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        val page = currentPage
        CoroutineScope(AppDispatchers.IO).launch {
            val newResults = fetchResultsForPage()

            withContext(Dispatchers.Main) {
                _hasMoreResults.value = newResults.size == itemsPerPage
                _searchResults.value += newResults
                _isLoadingMore.value = false

                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.DICT_LOAD_MORE,
                    mapOf(
                        "PAGE" to page.toString(),
                        "ADDED_COUNT" to newResults.size.toString(),
                        "TOTAL_COUNT" to _searchResults.value.size.toString()
                    )
                )
            }
        }
    }

    private suspend fun fetchResultsForPage(): List<AnnotatedChineseWord> {
        val searchQuery = searchQuery.value
        val listName = searchQuery.inListName
        val annotatedOnly = prefsStore.searchFilterHasAnnotation.value
        val locale = Locale.resolve(prefsStore.dictionaryLocale.value)

        if (searchQuery.exactWord) {
            return fetchExactWordPage(searchQuery.query.trim(), listName, annotatedOnly)
        }

        val results = if (listName != null) {
            // Search within the specified word list
            annotatedChineseWordDAO.searchFromWordList(listName, searchQuery.query, locale, annotatedOnly && !searchQuery.ignoreAnnotation, currentPage, itemsPerPage)
        } else {
            annotatedChineseWordDAO.searchFromStrLike(searchQuery.query, locale, annotatedOnly && !searchQuery.ignoreAnnotation, atExam = null, currentPage, itemsPerPage)
        }

        currentPage++
        return results
    }

    /** exactWord: exact simplified lookup, no examples/collocations/definitions noise. */
    private suspend fun fetchExactWordPage(query: String, listName: String?, annotatedOnly: Boolean): List<AnnotatedChineseWord> {
        if (currentPage > 0 || query.isEmpty()) return emptyList()
        currentPage++

        val hit = annotatedChineseWordDAO.getFromSimplified(query) ?: return emptyList()
        if (annotatedOnly && !hit.hasAnnotation()) return emptyList()
        if (listName != null) {
            val inList = annotatedChineseWordDAO.searchFromWordList(listName, query, Locale.resolve(prefsStore.dictionaryLocale.value), annotatedOnly, 0, 1)
            if (inList.none { it.simplified == hit.simplified }) return emptyList()
        }
        return listOf(hit)
    }

    fun speakWord(word: String) {
        Utils.playWordInBackground(word)
    }

    fun copyWord(word: String) {
        Utils.copyToClipBoard(word)
    }

    fun listsAssociationChanged() {
        if (searchQuery.value.inListName != null) performSearch(IntentSources.IN_APP)
    }
}
