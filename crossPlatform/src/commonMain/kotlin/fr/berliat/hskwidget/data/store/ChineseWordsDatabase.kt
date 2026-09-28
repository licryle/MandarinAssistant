package fr.berliat.hskwidget.data.store

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import co.touchlab.kermit.Logger
import fr.berliat.hskwidget.core.Utils

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div

import fr.berliat.hskwidget.data.dao.AnnotatedChineseWordDAO
import fr.berliat.hskwidget.data.dao.ChineseWordAnnotationDAO
import fr.berliat.hskwidget.data.dao.ChineseWordDAO
import fr.berliat.hskwidget.data.dao.ChineseWordFrequencyDAO
import fr.berliat.hskwidget.data.dao.WidgetListDAO
import fr.berliat.hskwidget.data.dao.WordListDAO
import fr.berliat.hskwidget.data.dao.WordDefinitionDAO
import fr.berliat.hskwidget.data.model.ChineseWord
import fr.berliat.hskwidget.data.model.ChineseWordAnnotation
import fr.berliat.hskwidget.data.model.ChineseWordFTS
import fr.berliat.hskwidget.data.model.WordDefinitionFTS
import fr.berliat.hskwidget.data.model.ChineseWordAnnotationFTS
import fr.berliat.hskwidget.data.model.ChineseWordFrequency
import fr.berliat.hskwidget.data.model.WidgetListEntry
import fr.berliat.hskwidget.data.model.WordList
import fr.berliat.hskwidget.data.model.WordListEntry
import fr.berliat.hskwidget.data.model.WordDefinition
import fr.berliat.hskwidget.data.type.AnnotatedChineseWordsConverter
import fr.berliat.hskwidget.data.type.DefinitionsConverter
import fr.berliat.hskwidget.data.type.InstantConverter
import fr.berliat.hskwidget.data.type.ListTypeConverter
import fr.berliat.hskwidget.data.type.ModalityConverter
import fr.berliat.hskwidget.data.type.Pinyins
import fr.berliat.hskwidget.data.type.WordTypeConverter
import fr.berliat.hskwidget.domain.DatabaseHelper
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.path

@Database(
    entities = [ChineseWordAnnotation::class, ChineseWord::class, WordDefinition::class, ChineseWordFrequency::class,
        WordList::class, WordListEntry::class, WidgetListEntry::class,
        ChineseWordFTS::class, WordDefinitionFTS::class, ChineseWordAnnotationFTS::class],
    version = ChineseWordsDatabase.DATABASE_VERSION, exportSchema = true)
@ColumnTypeConverters(
    Pinyins::class,
    WordTypeConverter::class,
    ModalityConverter::class,
    InstantConverter::class,
    DefinitionsConverter::class,
    AnnotatedChineseWordsConverter::class,
    ListTypeConverter::class)

@ConstructedBy(ChineseWordsDatabaseConstructor::class)
abstract class ChineseWordsDatabase: RoomDatabase() {
    companion object {
        const val DATABASE_VERSION = 3
        private const val TAG = "ChineseWordsDatabase"
    }
    abstract fun annotatedChineseWordDAO(): AnnotatedChineseWordDAO
    abstract fun chineseWordAnnotationDAO(): ChineseWordAnnotationDAO
    abstract fun chineseWordDAO(): ChineseWordDAO
    abstract fun wordDefinitionDAO(): WordDefinitionDAO
    abstract fun chineseWordFrequencyDAO(): ChineseWordFrequencyDAO
    abstract fun wordListDAO(): WordListDAO
    abstract fun widgetListDAO(): WidgetListDAO

    var _databaseFile: PlatformFile? = null
    val databaseFile
        get() = _databaseFile!!

    suspend fun snapshotToFile(): PlatformFile? = try {
        val dest = FileKit.cacheDir / (DatabaseHelper.TEMP_FILE_PREFIX + Utils.getRandomString(10))
        dest.delete(false)

        DatabaseHelper.vacuumInto(this, dest)

        dest
    } catch (e: Exception) {
        Logger.e(tag = TAG, messageString = "snapshotToFile() failed", throwable = e)
        null
    }

    suspend fun truncateToUserData() {
        useWriterConnection { connection ->
            connection.immediateTransaction {
                try { wordDefinitionDAO().deleteAll() } catch (_: Exception) {}
                try { chineseWordDAO().deleteAll() } catch (_: Exception) {}
                try { wordListDAO().deleteAllSystemEntries() } catch (_: Exception) {}

                try { connection.executeSQL("INSERT INTO chinese_word_fts(chinese_word_fts) VALUES('delete-all')") } catch (_: Exception) {}
                try { connection.executeSQL("INSERT INTO word_definition_fts(word_definition_fts) VALUES('delete-all')") } catch (_: Exception) {}
            }
        }
    }

    suspend fun clone(): ChineseWordsDatabase? = try {
        DatabaseHelper.createRoomDatabaseFromFile(snapshotToFile()!!)
    } catch (e: Exception) {
        Logger.e(tag = TAG, messageString = "snapshotToFile() failed", throwable = e)
        null
    }

    suspend fun rebuildFTSIndexes() {
        useWriterConnection { connection ->
            connection.executeSQL("INSERT INTO chinese_word_fts(chinese_word_fts) VALUES('rebuild')")
            connection.executeSQL("INSERT INTO word_definition_fts(word_definition_fts) VALUES('rebuild')")
            connection.executeSQL("INSERT INTO chinese_word_annotation_fts(chinese_word_annotation_fts) VALUES('rebuild')")
        }
    }
}

expect object ChineseWordsDatabaseConstructor : RoomDatabaseConstructor<ChineseWordsDatabase> {
    override fun initialize(): ChineseWordsDatabase
}
