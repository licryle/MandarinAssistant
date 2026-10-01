package fr.berliat.hskwidget.ui.screens.annotate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.Utils.incrementConsultedWord
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.model.ChineseWord
import fr.berliat.hskwidget.data.model.ChineseWordAnnotation
import fr.berliat.hskwidget.data.repo.WordListRepository
import fr.berliat.hskwidget.data.store.ChineseWordsDatabase
import fr.berliat.hskwidget.data.type.ClassLevel
import fr.berliat.hskwidget.data.type.ClassType
import fr.berliat.hskwidget.data.type.Pinyins
import fr.berliat.hskwidget.domain.KAnkiDelegator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class AnnotateViewModel(
    private val prefsStore: AppPreferencesStore = HSKAppServices.appPreferences,
    private val database: ChineseWordsDatabase = HSKAppServices.database,
    private val wordListRepo: WordListRepository = HSKAppServices.wordListRepo,
    private val ankiCaller : KAnkiDelegator
) : ViewModel() {
    val lastAnnotatedClassType: StateFlow<ClassType> = prefsStore.lastAnnotatedClassType.asStateFlow()
    val lastAnnotatedClassLevel: StateFlow<ClassLevel> = prefsStore.lastAnnotatedClassLevel.asStateFlow()

    suspend fun fetchAnnotatedWord(word: String): AnnotatedChineseWord {
        return if (word == "") {
            AnnotatedChineseWord.getBlank()
        } else {
            getAnnotatedChineseWord(word)
        }
    }
    suspend fun getAnnotatedChineseWord(simplifiedWord: String): AnnotatedChineseWord
            = withContext(AppDispatchers.IO) {
        val annot = HSKAppServices.database.annotatedChineseWordDAO().getFromSimplified(simplifiedWord)
        if (simplifiedWord.isNotBlank()) {
            Logging.logAnalyticsEvent(
                Logging.ANALYTICS_EVENTS.ANNOTATION_VIEW,
                mapOf(
                    "HAS_ANNOTATION" to ((annot != null && annot.hasAnnotation()).toString()),
                    "HAS_BASE_WORD" to ((annot != null && annot.hasWord()).toString())
                )
            )
        }
        return@withContext if (annot == null || !annot.hasAnnotation()) {
            AnnotatedChineseWord(
                annot?.word ?: ChineseWord.getBlank(simplifiedWord),
                ChineseWordAnnotation.getBlank(simplifiedWord)
            )
        } else {
            annot
        }
    }

    fun saveWord(annotatedWord: AnnotatedChineseWord, pinyins: String, notes: String, themes: String, isExam: Boolean, cType: ClassType, cLevel: ClassLevel, callback: ((AnnotatedChineseWord, Exception?) -> Unit)? = null) {
        var firstSeen = annotatedWord.annotation?.firstSeen
        if (firstSeen == null)
            firstSeen = Clock.System.now()

        val updatedAnnotation = ChineseWordAnnotation(
            simplified = annotatedWord.simplified.trim(),
            pinyins = Pinyins.fromString(pinyins),
            notes = notes,
            classType = cType,
            level = cLevel,
            themes = themes,
            firstSeen = firstSeen,  // Handle date logic
            isExam = isExam
        ).withSearchableText()

        val annotatedWord = AnnotatedChineseWord(annotatedWord.word, updatedAnnotation)
        updateAnnotation(annotatedWord) { err -> callback?.invoke(annotatedWord, err) }

        prefsStore.lastAnnotatedClassType.value = cType
        prefsStore.lastAnnotatedClassLevel.value = cLevel

        incrementConsultedWord(annotatedWord.simplified)
    }

    // Save or update annotation
    fun updateAnnotation(annotatedWord: AnnotatedChineseWord, callback: (Exception?) -> Unit) {
        viewModelScope.launch(AppDispatchers.IO) {
            var error: Exception? = null
            try {
                database.chineseWordAnnotationDAO().insertOrUpdate(annotatedWord.annotation!!)

                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.ANNOTATION_SAVE)

                ankiCaller(wordListRepo.addWordToSysAnnotatedList(annotatedWord))

                if (annotatedWord.annotation.isExam == true) {
                    ankiCaller(wordListRepo.addWordToSysExamList(annotatedWord))
                }

                val ankiOperation = wordListRepo.updateInAllLists(annotatedWord.simplified)
                ankiCaller(ankiOperation)
            } catch (e: Exception) {
                error = e
            }
            withContext(Dispatchers.Main) {
                callback(error)
            }
        }
    }

    // Delete annotation
    fun deleteAnnotation(simplified: String, callback: ((String, Exception?) -> Unit)? = null) {
        viewModelScope.launch(AppDispatchers.IO) {
            var error: Exception? = null
            try {
                val nbRowAffected = database.chineseWordAnnotationDAO().deleteBySimplified(simplified)
                if (nbRowAffected == 0) throw Exception("No records deleted")

                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.ANNOTATION_DELETE)

                wordListRepo.touchAnnotatedList()
                ankiCaller(wordListRepo.removeWordFromAllLists(simplified))
            } catch (e: Exception) {
                error = e
            }

            withContext(Dispatchers.Main) {
                callback?.invoke(simplified, error)
            }
        }
    }

    fun speakWord(word: String) {
        Utils.playWordInBackground(word)
    }

    fun copyWord(word: String) {
        Utils.copyToClipBoard(word)
    }
}