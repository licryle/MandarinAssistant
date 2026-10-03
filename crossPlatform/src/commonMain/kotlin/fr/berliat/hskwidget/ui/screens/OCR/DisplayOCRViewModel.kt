package fr.berliat.hskwidget.ui.screens.OCR

import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras

import co.touchlab.kermit.Logger

import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.HSKOCR
import fr.berliat.hskwidget.data.dao.AnnotatedChineseWordDAO
import fr.berliat.hskwidget.data.dao.ChineseWordFrequencyDAO
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.repo.ChineseWordFrequencyRepo
import fr.berliat.hskwidget.data.store.AppPreferencesStore

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.ocr_display_no_text_found
import fr.berliat.hskwidget.ocr_display_ocr_failed
import fr.berliat.hskwidget.ocr_display_smallest_text
import fr.berliat.hskwidget.ocr_display_word_not_found
import fr.berliat.hskwidget.ui.theme.AppTypographies

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

import kotlin.time.Duration.Companion.milliseconds

class DisplayOCRViewModel(
    savedStateHandle: SavedStateHandle,
    private val appPreferences: AppPreferencesStore,
    private val annotatedChineseWordDAO: AnnotatedChineseWordDAO,
    chineseWordFrequencyDAO: ChineseWordFrequencyDAO,
    private val segmenter : HSKTextSegmenter) : ViewModel() {

    @Serializable
    data class UiState(
        val text: String = "",
        val clickedWords: Map<String, String> = emptyMap(),
        val selectedWord: AnnotatedChineseWord? = null,

        // Configuration (User Preferences)
        val showPinyins: Boolean = false,
        val separatorEnabled: Boolean = false,
        val textSize: Float = 14f,

        // UI Status (Indicators)
        val isProcessing: Boolean = false,
        val isSegmenterReady: Boolean = false
    )

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val frequencyWordsRepo: ChineseWordFrequencyRepo = ChineseWordFrequencyRepo(
        chineseWordFrequencyDAO,
        annotatedChineseWordDAO
    )

    fun setText(s: String) { _uiState.update { it.copy(text = s) } }

    private val _error = MutableStateFlow<StringResource?>(null)
    val error: StateFlow<StringResource?> = _error
    fun setError(e: StringResource) { _error.value = e }

    val wordSeparator = WORD_SEPARATOR

    init {
        viewModelScope.launch(AppDispatchers.IO) {
            while (!segmenter.isReady()) {
                println("Still waiting for segmenter to come online")

                // Wait before checking again (important — don’t busy loop!)
                delay(100.milliseconds)
            }

            _uiState.update { it.copy(isSegmenterReady = true) }
        }

        viewModelScope.launch(AppDispatchers.IO) {
            // Combine flows from AppPreferences and update UiState
            combine(
                appPreferences.readerShowAllPinyins.asStateFlow(),
                appPreferences.readerSeparateWords.asStateFlow(),
                appPreferences.readerTextSize.asStateFlow()
            ) { showPinyins, separatorEnabled, textSize ->
                // This block runs whenever any of the three underlying preferences change
                _uiState.update { current ->
                    current.copy(
                        showPinyins = showPinyins,
                        separatorEnabled = separatorEnabled,
                        textSize = textSize.value
                    )
                }
            }.collect() // Start collecting the combined flow
        }
    }

    fun resetText() {
        _uiState.update {
            it.copy(
                text = "",
                selectedWord = null,
                clickedWords = emptyMap())
        }
    }

    fun augmentWordFrequencyAppeared(words: List<Pair<String, String>>) {
        viewModelScope.launch(AppDispatchers.IO) {
            frequencyWordsRepo.incrementAppeared(words.groupingBy { it.first }.eachCount())
        }
    }

    fun augmentWordFrequencyConsulted(hanzi: String) {
        viewModelScope.launch(AppDispatchers.IO) {
            frequencyWordsRepo.incrementConsulted(hanzi)
        }
    }

    suspend fun fetchWord(hanzi: String): AnnotatedChineseWord? = withContext(AppDispatchers.IO) {
        Logger.d(tag = TAG, messageString = "Searching for $hanzi")

        try {
            val word = annotatedChineseWordDAO.getFromSimplified(hanzi)
            Logger.d(tag = TAG, messageString = "Search returned for $hanzi")

            return@withContext word
        } catch (e: Exception) {
            // Code for handling the exception
            Logger.e(tag = TAG, messageString = "$e")
            return@withContext null
        }
    }

    fun fetchWordForDisplay(simplified: String) {
        viewModelScope.launch(AppDispatchers.IO) {
            // Switch to the IO dispatcher to perform background work
            val annotatedWord = fetchWord(simplified)

            if (annotatedWord == null) {
                HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.ocr_display_word_not_found)

                withContext(Dispatchers.Main) {
                    Utils.copyToClipBoard(simplified)
                }

                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.OCR_WORD_NOTFOUND)
            } else {
                _uiState.update { it.copy(selectedWord = annotatedWord) }
                val pinyin = ((annotatedWord.word?.pinyins ?: annotatedWord.annotation?.pinyins) ?: "").toString()

                _uiState.update { it.copy(clickedWords = it.clickedWords + (annotatedWord.simplified to pinyin)) }

                augmentWordFrequencyConsulted(annotatedWord.simplified)
                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.OCR_WORD_FOUND)
            }
        }
    }

    fun updateTextSize(increment: Float) {
        val textSize = (appPreferences.readerTextSize.value.value + increment).coerceAtLeast(
            AppTypographies.smallestHanziFontSize.value)

        if (textSize == AppTypographies.smallestHanziFontSize.value) {
            viewModelScope.launch {
                HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.ocr_display_smallest_text)
            }
        }

        Logger.d(tag = TAG, messageString = "updateTextSize to $textSize")
        appPreferences.readerTextSize.value = textSize.sp

        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.OCR_TEXT_SIZE_CHANGE)
    }

    fun toggleShowPinyins(showPinyins: Boolean) {
        appPreferences.readerShowAllPinyins.value = showPinyins
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.OCR_TOGGLE_PINYIN,
            mapOf("ENABLED" to showPinyins.toString())
        )
    }

    fun toggleSeparator(showSeparator: Boolean) {
        appPreferences.readerSeparateWords.value = showSeparator
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.OCR_TOGGLE_SEPARATOR,
            mapOf("ENABLED" to showSeparator.toString())
        )
    }

    fun copyToClipboard(word: AnnotatedChineseWord) {
        Utils.copyToClipBoard(word.simplified)
    }

    fun speakWord(word: AnnotatedChineseWord) {
        Utils.playWordInBackground(word.simplified)
    }

    fun recognizeText(imagePath: PlatformFile) {
        Logger.d(tag = TAG, messageString = "recognizeText starting for ${imagePath.path}")
        viewModelScope.launch(AppDispatchers.IO) {
            _uiState.update { it.copy(isProcessing = true) }
            if (!imagePath.exists()) {
                Logger.w(tag = TAG, messageString = "Image file missing: ${imagePath.path}")
                _error.value = Res.string.ocr_display_ocr_failed
                _uiState.update { it.copy(isProcessing = false) }
                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.OCR_RECOGNIZE_FAIL,
                    mapOf("REASON" to "missing_file")
                )
                return@launch
            }
            HSKOCR().process(imagePath, { text ->
                Logger.d(tag = TAG, messageString = "Recognized text length: ${text?.length ?: 0}")

                if (text == null) {
                    Logger.w(tag = TAG, messageString = "No text found in image")
                    _error.value = Res.string.ocr_display_no_text_found
                    Logging.logAnalyticsEvent(
                        Logging.ANALYTICS_EVENTS.OCR_RECOGNIZE_FAIL,
                        mapOf("REASON" to "no_text")
                    )
                } else {
                    Logger.i(tag = TAG, messageString = "Text recognition success. Appending text.")
                    var newText = text
                    if (_uiState.value.text.isNotEmpty()) newText = "\n\n" + text


                    _uiState.update { it.copy(text = it.text + newText) }
                    Logging.logAnalyticsEvent(
                        Logging.ANALYTICS_EVENTS.OCR_RECOGNIZE_SUCCESS,
                        mapOf("TEXT_LEN" to text.length.toString())
                    )
                }
                _uiState.update { it.copy(isProcessing = false) }
            }, { e ->
                Logger.e(tag = TAG, messageString = "Text recognition failed: ${e.message}", throwable = e)
                _error.value = Res.string.ocr_display_ocr_failed
                _uiState.update { it.copy(isProcessing = false) }

                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.OCR_RECOGNIZE_FAIL,
                    mapOf("REASON" to "error")
                )
                Logging.logAnalyticsError(
                    "OCR_DISPLAY",
                    "TextRecognitionFailed",
                    e.message ?: ""
                )
            })
        }
    }

    companion object {
        private const val TAG = "DisplayOCRViewModel"

        const val WORD_SEPARATOR = "·"

        val FACTORY = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                return DisplayOCRViewModel(
                    savedStateHandle = extras.createSavedStateHandle(),
                    appPreferences = HSKAppServices.appPreferences,
                    annotatedChineseWordDAO = HSKAppServices.database.annotatedChineseWordDAO(),
                    chineseWordFrequencyDAO = HSKAppServices.database.chineseWordFrequencyDAO(),
                    segmenter = HSKAppServices.HSKSegmenter
                ) as T
            }
        }
    }
}