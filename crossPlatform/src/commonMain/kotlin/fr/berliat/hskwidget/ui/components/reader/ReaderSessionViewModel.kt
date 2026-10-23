package fr.berliat.hskwidget.ui.components.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.data.dao.AnnotatedChineseWordDAO
import fr.berliat.hskwidget.data.dao.ChineseWordFrequencyDAO
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.repo.ChineseWordFrequencyRepo
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.ocr_display_word_not_found
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ReaderSessionViewModel"

/**
 * Shared "click word -> fetch details" session used by the embedded reader.
 *
 * Owns [selectedWord] + [clickedWords] so Dictionary's no-result reader and
 * OCR's reader behave the same. Frequency counting mirrors DisplayOCRViewModel.
 */
class ReaderSessionViewModel(
    private val annotatedChineseWordDAO: AnnotatedChineseWordDAO = HSKAppServices.database.annotatedChineseWordDAO(),
    chineseWordFrequencyDAO: ChineseWordFrequencyDAO = HSKAppServices.database.chineseWordFrequencyDAO(),
    private val appPreferences: AppPreferencesStore = HSKAppServices.appPreferences
) : ViewModel() {

    private val _selectedWord = MutableStateFlow<AnnotatedChineseWord?>(null)
    val selectedWord: StateFlow<AnnotatedChineseWord?> = _selectedWord.asStateFlow()

    private val _clickedWords = MutableStateFlow<Map<String, String>>(emptyMap())
    val clickedWords: StateFlow<Map<String, String>> = _clickedWords.asStateFlow()

    /** Unified reader text size (readerTextSize pref) shared by OCR and Dictionary reader mode. */
    val textSize: StateFlow<Float> = appPreferences.readerTextSize.asStateFlow()
        .map { it.value }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appPreferences.readerTextSize.value.value)

    /** Single owner for reader UI state: the screen reads these, the chips write via toggles below. */
    val separatorEnabled: StateFlow<Boolean> = appPreferences.readerSeparateWords.asStateFlow()
    val showPinyins: StateFlow<Boolean> = appPreferences.readerShowAllPinyins.asStateFlow()

    private val frequencyRepo = ChineseWordFrequencyRepo(
        chineseWordFrequencyDAO,
        annotatedChineseWordDAO
    )

    fun clearSelection() {
        _selectedWord.value = null
    }

    fun resetForText(@Suppress("UNUSED_PARAMETER") newText: String) {
        // Simplest correct behaviour: a new query means a new session.
        // Keeps stale cards from persisting across searches.
        _selectedWord.value = null
        _clickedWords.value = emptyMap()
    }

    fun augmentAppeared(words: List<Pair<String, String>>) {
        viewModelScope.launch(AppDispatchers.IO) {
            frequencyRepo.incrementAppeared(words.groupingBy { it.first }.eachCount())
        }
    }

    fun fetchWordForDisplay(simplified: String) {
        viewModelScope.launch(AppDispatchers.IO) {
            val annotatedWord = try {
                annotatedChineseWordDAO.getFromSimplified(simplified)
            } catch (e: Exception) {
                Logger.e(tag = TAG, messageString = "$e")
                null
            }

            if (annotatedWord == null) {
                HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.ocr_display_word_not_found)
                withContext(Dispatchers.Main) {
                    Utils.copyToClipBoard(simplified)
                }
                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.OCR_WORD_NOTFOUND)
            } else {
                _selectedWord.value = annotatedWord
                val pinyin = ((annotatedWord.word?.pinyins ?: annotatedWord.annotation?.pinyins) ?: "").toString()
                _clickedWords.update { it + (annotatedWord.simplified to pinyin) }
                try {
                    frequencyRepo.incrementConsulted(annotatedWord.simplified)
                } catch (e: Exception) {
                    Logger.e(tag = TAG, messageString = "incrementConsulted failed: $e")
                }
                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.OCR_WORD_FOUND)
            }
        }
    }

    fun speakWord(word: AnnotatedChineseWord) {
        Utils.playWordInBackground(word.simplified)
    }

    fun copyWord(word: AnnotatedChineseWord) {
        Utils.copyToClipBoard(word.simplified)
    }
}
