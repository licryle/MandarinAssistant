package fr.berliat.hskwidget.ui.screens.dictionary

import androidx.compose.ui.unit.TextUnit
import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.data.dao.AnnotatedChineseWordDAO

import co.touchlab.kermit.Logger

import kotlinx.coroutines.CancellationException
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
import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.ui.theme.AppTypographies
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withContext


class DictionarySearchViewModel(private val prefsStore: AppPreferencesStore = HSKAppServices.appPreferences,
                          private val annotatedChineseWordDAO: AnnotatedChineseWordDAO = HSKAppServices.database.annotatedChineseWordDAO(),
                          private val widgetProvider: FlashcardWidgetProvider = FlashcardWidgetProvider()
) {
    private val TAG = "DictionarySearchViewModel"

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

    /**
     * Monotonic id of the latest search. A completion from an older search is
     * ignored, so a cancelled job can never overwrite the results (or move the
     * page cursor) of the search that replaced it.
     */
    private var searchGeneration = 0

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
        // Snapshot everything the query needs *now*. The DAO call must not read
        // the live search field later: the user may have typed again while this
        // search was in flight.
        val query = searchQuery.value
        val querySnapshot = query.query.trim()
        val annotatedOnly = prefsStore.searchFilterHasAnnotation.value
        val locale = Locale.resolve(prefsStore.dictionaryLocale.value)
        val baseParams = mapOf(
            "QUERY_LEN" to querySnapshot.length.toString(),
            "HAS_ANNOTATION_FILTER" to annotatedOnly.toString(),
            "IN_LIST" to (query.inListName != null).toString(),
            "LOCALE" to locale.code,
            "EXACT_WORD" to query.exactWord.toString(),
            "SOURCE" to source.toString()
        )

        // Reset synchronously, before any coroutine runs: an old job that is
        // cancelled while awaiting the DAO could otherwise finish its
        // `currentPage++` (or clear the loading flag) after the new search
        // started, making it read page 1 of a fresh query -> empty results.
        val generation = ++searchGeneration
        currentSearchJob?.cancel()
        currentPage = 0
        _searchedQuery.value = null

        currentSearchJob = CoroutineScope(AppDispatchers.IO).launch {
            _isLoading.value = true

            var results: List<AnnotatedChineseWord> = emptyList()
            var wordExists: Boolean? = null
            var failure: Throwable? = null
            try {
                results = fetchResultsForPage(query, locale, annotatedOnly, page = 0)
                wordExists = if (querySnapshot.isEmpty()) {
                    null
                } else {
                    annotatedChineseWordDAO.getFromSimplified(querySnapshot) != null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                Logger.e(
                    tag = TAG,
                    messageString = "Dictionary search failed",
                    throwable = e
                )
            }

            withContext(Dispatchers.Main) {
                // A newer search took over while this one was running.
                if (generation != searchGeneration) return@withContext

                currentPage = 1
                _hasMoreResults.value = results.size == itemsPerPage
                _searchResults.value = results
                _wordExists.value = wordExists
                _searchedQuery.value = querySnapshot
                _isLoading.value = false

                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.DICT_SEARCH,
                    baseParams + mapOf("RESULT_COUNT" to results.size.toString())
                )
                if (failure == null && results.isEmpty()) {
                    Logging.logAnalyticsEvent(
                        Logging.ANALYTICS_EVENTS.DICT_EMPTY_RESULT,
                        baseParams
                    )
                }
            }
        }
    }

    fun loadMore() {
        // Never paginate while the first page is still loading: the page cursor
        // belongs to the search that produced the currently displayed results.
        if (_isLoading.value || _isLoadingMore.value) return

        val query = searchQuery.value
        val annotatedOnly = prefsStore.searchFilterHasAnnotation.value
        val locale = Locale.resolve(prefsStore.dictionaryLocale.value)
        val generation = searchGeneration
        val page = currentPage

        _isLoadingMore.value = true
        CoroutineScope(AppDispatchers.IO).launch {
            val newResults = try {
                fetchResultsForPage(query, locale, annotatedOnly, page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(
                    tag = TAG,
                    messageString = "Dictionary load more failed",
                    throwable = e
                )
                emptyList()
            }

            withContext(Dispatchers.Main) {
                // The query changed while this page was loading: drop the page,
                // the new search already reset the loading flag.
                if (generation != searchGeneration) return@withContext

                currentPage = page + 1
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

    private suspend fun fetchResultsForPage(
        query: SearchQuery,
        locale: Locale,
        annotatedOnly: Boolean,
        page: Int
    ): List<AnnotatedChineseWord> {
        val listName = query.inListName
        val filterAnnotation = annotatedOnly && !query.ignoreAnnotation

        if (query.exactWord) {
            return fetchExactWordPage(query.query.trim(), listName, locale, filterAnnotation, page)
        }

        return if (listName != null) {
            // Search within the specified word list
            annotatedChineseWordDAO.searchFromWordList(listName, query.query, locale, filterAnnotation, page, itemsPerPage)
        } else {
            annotatedChineseWordDAO.searchFromStrLike(query.query, locale, filterAnnotation, atExam = null, page, itemsPerPage)
        }
    }

    /** exactWord: exact simplified lookup, no examples/collocations/definitions noise. */
    private suspend fun fetchExactWordPage(
        query: String,
        listName: String?,
        locale: Locale,
        annotatedOnly: Boolean,
        page: Int
    ): List<AnnotatedChineseWord> {
        if (page > 0 || query.isEmpty()) return emptyList()

        val hit = annotatedChineseWordDAO.getFromSimplified(query) ?: return emptyList()
        if (annotatedOnly && !hit.hasAnnotation()) return emptyList()
        if (listName != null) {
            val inList = annotatedChineseWordDAO.searchFromWordList(listName, query, locale, annotatedOnly, 0, 1)
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
