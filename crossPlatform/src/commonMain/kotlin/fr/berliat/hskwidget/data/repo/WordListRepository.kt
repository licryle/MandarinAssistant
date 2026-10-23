package fr.berliat.hskwidget.data.repo

import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.data.dao.AnnotatedChineseWordDAO
import fr.berliat.hskwidget.data.dao.WordListDAO
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.model.WordList
import fr.berliat.hskwidget.data.model.WordListEntry
import fr.berliat.hskwidget.data.store.AnkiStore
import fr.berliat.hskwidget.domain.AnkiSyncWordListsService
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * WordListRepository is in charge for updating words into list, both on local and Anki storage.
 *
 * The suspend functions are a bit complex. Due to the fact that Anki API can only be called after
 * having the right permissions, and app is running. There are lots of checks to be done, and the
 * permissions one must happen in the fragment thread.
 *
 * So what happens is that all suspend modifying functions here do the local DAO work and return a
 * suspend method with all Anki work to be executed in the Fragment thread. Use an
 * AnkiDelegate for (very) easy use.
 *
 * To call any method touching Anki, those who return a suspend () -> Result<Unit>, use
 * delegateToAnki. Must be used in conjunction of a Fragment/Activity. This will automatically do
 * all checks for you and call the Anki methods.
 *
 * Here's a simplistic example:
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         ankiDelegate = AnkiDelegate(this)
 *         ankiDelegate.delegateToAnki(wordListRepo.insertWordToList(list, word))
 *     }
 *
 * If you use a viewModel, make sure to use the same workListRepo from your AnkiDelegateHelper.
 * In other words, don't instantiate WordListRepository yourself. Take it from the Helper, always.
 *
 * Beware of execution patterns, as the callbacks can mean Anki calls executing after whatever
 * element you change/delete.
 */
class WordListRepository(
    private val ankiStore: AnkiStore,
    private val wordListDAO: WordListDAO,
    private val annotatedChineseWordDAO: AnnotatedChineseWordDAO
) {
    companion object {
        private const val TAG = "WordListRepository"
    }

    /****** NOT TOUCHING ANKI *******/
    suspend fun getAllLists() = withContext(AppDispatchers.IO) { wordListDAO.getAllLists() }
    suspend fun getSystemLists() = withContext(AppDispatchers.IO) { wordListDAO.getSystemLists() }
    suspend fun getUserLists() = withContext(AppDispatchers.IO) { wordListDAO.getUserLists() }
    suspend fun getWordListsForWord(simplified: String) = withContext(AppDispatchers.IO) { wordListDAO.getWordListsForWord(simplified) }

    suspend fun isWordInList(wordList: WordList, simplified: String) : Boolean = withContext(AppDispatchers.IO) {
        val existingEntries = wordListDAO.getEntriesForWord(simplified)
        return@withContext (existingEntries.any { it.listId == wordList.id })
    }

    suspend fun isNameUnique(name: String, excludeId: Long = 0): Boolean = withContext(AppDispatchers.IO) {
        wordListDAO.countByName(name, excludeId) == 0
    }

    suspend fun createList(name: String) = withContext(AppDispatchers.IO) {
        val list = WordList(name)

        if (!isNameUnique(name)) {
            throw Exception("A word list with this name already exists")
        }

        wordListDAO.insertList(list)
    }

    suspend fun renameList(id: Long, newName: String) = withContext(AppDispatchers.IO) {
        if (!isNameUnique(newName, id)) {
            throw Exception("A word list with this name already exists")
        }

        wordListDAO.renameList(id, newName)
        wordListDAO.touchList(id)
    }

    /****** ANKI ALTERING METHODS *******/
    fun syncListsToAnki(): KClass<out AnkiSyncWordListsService> {
        return AnkiSyncWordListsService::class
    }

    suspend fun updateWordListAssociations(simplified: String, listIds: Set<Long>): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        // First remove all existing associations for this word
        val entries = wordListDAO.getEntriesForWord(simplified)

        val toDelete = entries.filter { ! listIds.contains(it.listId)  }
        val toAdd = listIds.filter { id -> entries.none { it.listId == id }  }

        // Then add new associations
        toAdd.forEach { listId ->
            wordListDAO.addWordToList(WordListEntry(listId, simplified))
            wordListDAO.touchList(listId)
        }

        toDelete.forEach{ entry ->
            wordListDAO.deleteWordFromList(entry.listId, simplified)
        }

        return@withContext suspend { // Now do the Anki bidding by removing notes from entries
            var nbErrors = 0
            for (toDel in toDelete) {
                if (!ankiStore.deleteCard(toDel)) {
                    nbErrors += 1
                }
            }

            val annotatedWord = annotatedChineseWordDAO.getFromSimplified(simplified)
            if (annotatedWord == null) {
                Result.failure(Exception("Word $simplified unknown, skipping Anki sync"))
            } else {
                for (toA in toAdd) {
                    val wordList = wordListDAO.getListById(toA)?.wordList ?: continue
                    val deck = getOrCreate(wordList)
                    val entry = WordListEntry(toA, simplified)
                    if (ankiStore.importOrUpdateCard(deck, entry, annotatedWord) == null) {
                        nbErrors += 1
                    }
                }

                if (nbErrors == 0) {
                    Result.success(Unit) // Indicate success if no errors
                } else {
                    // If there were errors, return a failure with the count
                    Result.failure(Exception("$nbErrors errors during update of ${entries.size} entries"))
                }
            }
        }
    }

    suspend fun deleteList(list: WordList): (suspend () -> Result<Unit>)? = withContext(AppDispatchers.IO) {
        val entries = wordListDAO.getListEntries(list.id)
        wordListDAO.deleteList(list)

        if (entries.isEmpty()) return@withContext null

        wordListDAO.deleteAllFromList(list.id)

        return@withContext suspend {
            var nbErrors = 0
            for (entry in entries) {
                if (!ankiStore.deleteCard(entry)) {
                    nbErrors += 1
                }
            }

            if (nbErrors == 0) {
                Result.success(Unit) // Indicate success if no errors
            } else {
                // If there were errors, return a failure with the count
                Result.failure(Exception("$nbErrors errors during update of ${entries.size} entries"))
            }
        }
    }

    suspend fun insertWordToList(wordList: WordList, word: AnnotatedChineseWord): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        val entries = wordListDAO.getEntriesForWord(word.simplified)
        var entry = entries.find { it.listId == wordList.id }

        if (entry == null) {
            entry = WordListEntry(wordList.id, word.simplified)
            wordListDAO.insertWordToList(entry)
        }

        if (entry.ankiNoteId != WordList.ANKI_ID_EMPTY) {
            return@withContext null
        }

        return@withContext suspend { // Will execute only if Anki integration is active, allowed and ready to fire
            val deck = getOrCreate(wordList)

            if (ankiStore.importOrUpdateCard(deck, entry, word) != null) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Coulnd't insert ${word.simplified} into Anki"))
            }
        }
    }

    suspend fun removeWordFromList(wordList: WordList, simplified: String): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        if (!isWordInList(wordList, simplified)) return@withContext null

        val entries = wordListDAO.getEntriesForWord(simplified)
        val entry = entries.find { it.listId == wordList.id }
            ?: return@withContext null // raced with another change, nothing left to remove

        // Checked on first list of function
        wordListDAO.deleteWordFromList(entry.listId, entry.simplified)
        wordListDAO.touchList(entry.listId)

        return@withContext suspend { // Will execute only if Anki integration is active, allowed and ready to fire
            if (ankiStore.deleteCard(entry)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Couldn't delete entry $entry"))
            }
        }
    }

    suspend fun removeWordFromAllLists(simplified: String): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        if (wordListDAO.getEntriesForWord(simplified).isEmpty()) return@withContext null

        // saving entries for callback before deleting in DB
        val entries = wordListDAO.getEntriesForWord(simplified)
        wordListDAO.removeWordFromAllLists(simplified)

        return@withContext suspend { // Will execute only if Anki integration is active, allowed and ready to fire
            var nbErrors = 0
            for (entry in entries) {
                if (!ankiStore.deleteCard(entry)) {
                    nbErrors += 1
                }
            }

            // After all entries have been processed, check if there were errors
            if (nbErrors == 0) {
                Result.success(Unit) // Indicate success if no errors
            } else {
                // If there were errors, return a failure with the count
                Result.failure(Exception("$nbErrors errors during update of ${entries.size} entries"))
            }
        }
    }

    suspend fun addWordToList(wordList: WordList, word: AnnotatedChineseWord): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        var entry = wordListDAO.getEntriesForWord(word.simplified).find { it.listId == wordList.id }

        if (entry == null) {
            entry = WordListEntry(wordList.id, word.simplified)
            wordListDAO.addWordToList(entry)
            wordListDAO.touchList(wordList.id)
        }

        // Anki now
        if (entry.ankiNoteId != WordList.ANKI_ID_EMPTY) return@withContext null

        return@withContext suspend { // Will execute only if Anki integration is active, allowed and ready to fire
            // Create deck if needed (shouldn't)
            val deck = getOrCreate(wordList)

            if (ankiStore.importOrUpdateCard(deck, entry, word) == null) {
                Result.failure(Exception("Couldn't add ${word.simplified} to anki"))
            } else {
                Result.success(Unit)
            }
        }
    }

    suspend fun addWordToSysAnnotatedList(word: AnnotatedChineseWord): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        val wordList = getSystemLists()

        val annotList = wordList.find { it.name == WordList.SYSTEM_ANNOTATED_NAME }
        if (annotList == null) return@withContext null

        return@withContext addWordToList(annotList.wordList, word)
    }

    suspend fun addWordToSysExamList(word: AnnotatedChineseWord): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        val wordList = getSystemLists()

        val annotList = wordList.find { it.name == WordList.SYSTEM_EXAM_NAME }
        if (annotList == null) return@withContext null

        return@withContext addWordToList(annotList.wordList, word)
    }

    suspend fun removeWordFromSysAnnotatedList(simplified: String): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        val wordList = getSystemLists()

        val annotList = wordList.find { it.name == WordList.SYSTEM_ANNOTATED_NAME }
        if (annotList == null) return@withContext null

        return@withContext removeWordFromList(annotList.wordList, simplified)
    }

    suspend fun removeWordFromSysExamList(simplified: String): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        val wordList = getSystemLists()

        val examList = wordList.find { it.name == WordList.SYSTEM_EXAM_NAME }
        if (examList == null) return@withContext null

        return@withContext removeWordFromList(examList.wordList, simplified)
    }

    suspend fun touchAnnotatedList() : Boolean = withContext(AppDispatchers.IO) {
        val wordList = getSystemLists()

        val annotList = wordList.find { it.name == WordList.SYSTEM_ANNOTATED_NAME }
        if (annotList == null) return@withContext false

        return@withContext wordListDAO.touchList(annotList.id) > 0
    }

    suspend fun touchExamList() : Boolean = withContext(AppDispatchers.IO) {
        val wordList = getSystemLists()

        val examList = wordList.find { it.name == WordList.SYSTEM_EXAM_NAME }
        if (examList == null) return@withContext false

        return@withContext wordListDAO.touchList(examList.id) > 0
    }

    suspend fun updateInAllLists(simplified: String): (suspend () -> Result<Unit>)?
            = withContext(AppDispatchers.IO) {
        if (wordListDAO.getEntriesForWord(simplified).isEmpty()) return@withContext null

        return@withContext suspend { // Will execute only if Anki integration is active, allowed and ready to fire
            val entries = wordListDAO.getEntriesForWord(simplified)

            var nbErrors = 0
            for (entry in entries) {
                val wordList = wordListDAO.getListById(entry.listId)
                val word = annotatedChineseWordDAO.getFromSimplified(simplified)

                if (wordList == null || word == null) {
                    nbErrors += 1
                    continue
                }

                val deck = getOrCreate(wordList.wordList)
                if (ankiStore.importOrUpdateCard(deck, entry, word) == null) {
                    nbErrors += 1
                }
            }

            // After all entries have been processed, check if there were errors
            if (nbErrors == 0) {
                Result.success(Unit) // Indicate success if no errors
            } else {
                // If there were errors, return a failure with the count
                Result.failure(Exception("$nbErrors errors during update of ${entries.size} entries"))
            }
        }
    }

    suspend fun getOrCreate(deck: WordList): WordList = withContext(AppDispatchers.IO) {
        val decks = ankiStore.ankiDAO.getDeckList()?: emptyMap()

        if (deck.ankiDeckId == WordList.ANKI_ID_EMPTY || decks.none { it.key == deck.ankiDeckId }) {
            // So deckId is non-existent or a goner. Let's piggy back by name given we have a prefix
            val ankiDeckId : Long
            // Anki is StrInsensitive, let's also trim.
            val trimmed = deck.getAnkiDeckName().lowercase().trim()
            val sameNameDeck = decks.entries.find { it.value.lowercase().trim() == trimmed }
            ankiDeckId = sameNameDeck?.key
                ?: (ankiStore.ankiDAO.addNewDeck(deck.getAnkiDeckName())
                    ?: throw IllegalStateException("Couldn't fetch or create Deck in Anki."))

            if (deck.ankiDeckId != ankiDeckId) {
                wordListDAO.updateAnkiDeckId(deck.id, ankiDeckId)

                // Return an updated item with the right id
                return@withContext WordList(deck.name, deck.id, deck.creationDate,
                    deck.lastModified, ankiDeckId, deck.listType)
            }
        }

        return@withContext deck
    }

    suspend fun buildListSystemAnnotated() = withContext(AppDispatchers.IO) {
        val list = wordListDAO.getSystemLists().filter { it.name == WordList.SYSTEM_ANNOTATED_NAME }
        if (list.isEmpty())
            wordListDAO.insertList(WordList(WordList.SYSTEM_ANNOTATED_NAME, listType = WordList.ListType.SYSTEM))

        rebuildListSystemAnnotated()
    }

    suspend fun rebuildListSystemAnnotated() = withContext(AppDispatchers.IO) {
        val list = wordListDAO.getSystemLists().filter { it.name == WordList.SYSTEM_ANNOTATED_NAME }
        if (list.isEmpty()) return@withContext
        val listId = list.first().id

        wordListDAO.deleteAllFromList(listId)

        val words = annotatedChineseWordDAO.getAllAnnotated()
        if (words.isNotEmpty()) {
            val entries = words.map { WordListEntry(listId, it.simplified) }
            wordListDAO.insertAllWords(entries)
        }

        wordListDAO.touchList(listId)
    }

    suspend fun buildListSystemExam() = withContext(AppDispatchers.IO) {
        val list = wordListDAO.getSystemLists().filter { it.name == WordList.SYSTEM_EXAM_NAME }
        if (list.isEmpty())
            wordListDAO.insertList(WordList(WordList.SYSTEM_EXAM_NAME, listType = WordList.ListType.SYSTEM))

        rebuildListSystemExam()
    }

    suspend fun rebuildListSystemExam() = withContext(AppDispatchers.IO) {
        val list = wordListDAO.getSystemLists().filter { it.name == WordList.SYSTEM_EXAM_NAME }
        if (list.isEmpty()) return@withContext
        val listId = list.first().id

        wordListDAO.deleteAllFromList(listId)

        val words = annotatedChineseWordDAO.getAllAtExam()
        if (words.isNotEmpty()) {
            val entries = words.map { WordListEntry(listId, it.simplified) }
            wordListDAO.insertAllWords(entries)
        }

        wordListDAO.touchList(listId)
    }
}