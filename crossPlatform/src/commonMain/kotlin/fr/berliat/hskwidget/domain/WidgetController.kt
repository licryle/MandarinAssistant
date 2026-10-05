package fr.berliat.hskwidget.domain

import co.touchlab.kermit.Logger

import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.ExpectedLogging
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.model.WordListWithCount
import fr.berliat.hskwidget.data.store.ChineseWordsDatabase
import fr.berliat.hskwidget.data.store.WidgetPreferencesStore

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

expect class WidgetController : CommonWidgetController

expect suspend fun getWidgetControllerInstance(
    widgetStore: WidgetPreferencesStore,
    database: ChineseWordsDatabase
): WidgetController

open class CommonWidgetController(
    val widgetStore: WidgetPreferencesStore,
    val database: ChineseWordsDatabase
    ) {
    companion object {
        private const val TAG = "CommonWidgetController"
    }

    protected val widgetId = widgetStore.widgetId
    protected val simplified: StateFlow<String> = widgetStore.currentWord.asStateFlow()

    val widgetListDAO = HSKAppServices.database.widgetListDAO()
    val wordListDAO = HSKAppServices.database.wordListDAO()
    val annotatedWordDAO = HSKAppServices.database.annotatedChineseWordDAO()

    var currentWord : AnnotatedChineseWord? = null


    fun speakWord() {
        Utils.playWordInBackground(simplified.value)
    }

    suspend fun redraw() = redrawWidget(currentWord)

    suspend fun updateWord() = withContext(AppDispatchers.IO) {
        try {
            val allowedListIds = getAllowedLists().map { it.wordList.id }
            if (allowedListIds.isEmpty()) {
                Logger.w(tag = TAG, messageString = "updateWord: no allowed lists for widget $widgetId, keeping cached word")
                return@withContext
            }
            val newWord =
                annotatedWordDAO.getRandomWordFromLists(
                    allowedListIds,
                    arrayOf(simplified.value)
                )

            Logger.i(tag = TAG, messageString = "getNewWord: Got a new word, maybe: $newWord")

            // Persist it in preferences for cross-App convenience
            widgetStore.currentWord.value = newWord?.simplified ?: ""
            currentWord = newWord
            redrawWidget(newWord)
        } catch (e: Exception) {
            // Transient storage failures (e.g. SQLITE_IOERR_SHORT_READ 522 during
            // a DB file swap or on dying flash) must skip one reload, not crash.
            Logger.e(tag = TAG, messageString = "updateWord failed for widget $widgetId, keeping cached word", throwable = e)
            try { Logging.logCrashalytics(e) } catch (_: Exception) {}
            try {
                Logging.logAnalyticsError(TAG, "WidgetUpdateWordFailure", (e.message?.take(120) ?: e::class.simpleName.orEmpty()))
            } catch (_: Exception) {}
        }
    }

    protected open suspend fun redrawWidget(word: AnnotatedChineseWord?) {}

    fun openDictionary() {
        val query = SearchQuery.fromString(simplified.value).copy(
            ignoreAnnotation = true
        )

        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.WIDGET_OPEN_DICTIONARY,
            mapOf(
                "SOURCE" to "widget",
                "WIDGET_ID" to widgetId.toString(),
                "QUERY_LEN" to simplified.value.length.toString()
            )
        )
        Utils.openAppForSearchQuery(query)
    }

    suspend fun getAllowedLists(): List<WordListWithCount> = withContext(AppDispatchers.IO) {
        val widgetListIds = widgetListDAO.getListsForWidget(widgetId)
        val lists = wordListDAO.getAllLists()

        return@withContext lists.filter { widgetListIds.contains(it.wordList.id) }
    }
}