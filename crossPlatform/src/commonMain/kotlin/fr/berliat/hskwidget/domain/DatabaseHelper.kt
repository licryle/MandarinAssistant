package fr.berliat.hskwidget.domain

import androidx.room3.RoomDatabase
import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.migration.Migration
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

import co.touchlab.kermit.Logger
import fr.berliat.hskwidget.Res

import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.data.store.ChineseWordsDatabase
import fr.berliat.hskwidget.database_update_failure
import fr.berliat.hskwidget.database_update_start
import fr.berliat.hskwidget.database_update_success

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.toKotlinxIoPath

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DatabaseBuilderWithPath(
    val file: PlatformFile,
    val builder: RoomDatabase.Builder<ChineseWordsDatabase>
)

class DatabaseUpdatingException(message: String) : Exception(message)

class DatabaseHelper private constructor() {
    val liveDatabase: ChineseWordsDatabase
        get() {
            if (updateProgress.value != null) throw DatabaseUpdatingException("Database is currently being updated from asset.")
            return _db!!
        }

    private var _db: ChineseWordsDatabase? = null
    lateinit var DATABASE_LIVE_PATH : PlatformFile
        private set
    lateinit var DATABASE_LIVE_DIR : PlatformFile
        private set

    companion object {
        private val _updateProgress = MutableStateFlow<Float?>(null)
        val updateProgress = _updateProgress.asStateFlow()

        private var INSTANCE: DatabaseHelper? = null
        private val mutex = Mutex()
        const val DATABASE_FILENAME = "Mandarin_Assistant.db"
        const val DATABASE_ASSET_PATH = "databases/$DATABASE_FILENAME"
        private const val TAG = "ChineseWordsDatabase"
        const val TEMP_FILE_PREFIX = "DB_TMP_"
        // Staged-swap files live in the same directory as the live DB so the
        // final rename is atomic (same filesystem). Never stage in cacheDir.
        const val STAGING_SUFFIX = ".new"
        const val BACKUP_SUFFIX = ".bak"
        const val JOURNAL_FILENAME = "Mandarin_Assistant.db.update.json"

        suspend fun getDatabaseLiveDir() = Utils.getAppDatabasePath()
        suspend fun getDatabaseLiveFile() = getDatabaseLiveDir() / DATABASE_FILENAME
        suspend fun getStagingFile() = getDatabaseLiveDir() / (DATABASE_FILENAME + STAGING_SUFFIX)
        suspend fun getBackupFile() = getDatabaseLiveDir() / (DATABASE_FILENAME + BACKUP_SUFFIX)
        suspend fun getJournalFile() = getDatabaseLiveDir() / JOURNAL_FILENAME

        @Serializable
        data class UserDataCounts(
            val annotations: Int = 0,
            val listEntries: Int = 0,
            val userLists: Int = 0,
            val widgets: Int = 0,
            val freq: Int = 0,
            val dictionary: Int = 0
        )

        @Serializable
        data class DbUpdateJournal(
            val state: String = "STAGED", // STAGED | SWAPPED
            val expected: UserDataCounts = UserDataCounts()
        ) {
            companion object {
                const val STAGED = "STAGED"
                const val SWAPPED = "SWAPPED"
            }
        }

        private val journalJson = Json { ignoreUnknownKeys = true }

        private suspend fun readJournal(file: PlatformFile): DbUpdateJournal? = withContext(AppDispatchers.IO) {
            try {
                if (!file.exists()) return@withContext null
                val text = SystemFileSystem.source(file.toKotlinxIoPath()).buffered().use { it.readString() }
                journalJson.decodeFromString<DbUpdateJournal>(text)
            } catch (_: Exception) { null }
        }

        private suspend fun writeJournal(file: PlatformFile, journal: DbUpdateJournal) = withContext(AppDispatchers.IO) {
            val path = file.toKotlinxIoPath()
            if (SystemFileSystem.exists(path)) {
                try { SystemFileSystem.delete(path) } catch (_: Exception) {}
            }
            SystemFileSystem.sink(path, append = false).buffered().use { sink ->
                sink.writeString(journalJson.encodeToString(journal))
            }
        }

        private suspend fun deleteJournal(file: PlatformFile) = withContext(AppDispatchers.IO) {
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
        }

        private suspend fun deleteSidecars(file: PlatformFile) = withContext(AppDispatchers.IO) {
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                try {
                    val sidecar = PlatformFile(file.path + suffix)
                    if (sidecar.exists()) sidecar.delete()
                } catch (_: Exception) {}
            }
        }

        private suspend fun deleteFileAndSidecars(file: PlatformFile) = withContext(AppDispatchers.IO) {
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
            deleteSidecars(file)
        }

        private suspend fun collectUserCounts(db: ChineseWordsDatabase): UserDataCounts = withContext(AppDispatchers.IO) {
            UserDataCounts(
                annotations = try { db.chineseWordAnnotationDAO().getCount() } catch (_: Exception) { -1 },
                listEntries = try { db.wordListDAO().getUserListEntriesCount() } catch (_: Exception) { -1 },
                userLists = try { db.wordListDAO().getUserListsCount() } catch (_: Exception) { -1 },
                widgets = try { db.widgetListDAO().getCount() } catch (_: Exception) { -1 },
                freq = try { db.chineseWordFrequencyDAO().getCount() } catch (_: Exception) { -1 },
                dictionary = try { db.chineseWordDAO().getCount() } catch (_: Exception) { -1 }
            )
        }

        /** Staging must hold the fresh dictionary plus all non-filterable user data.
         *  List entries / widget links may legitimately shrink (orphans of removed
         *  words are dropped by replaceUserDataInDB), so those are upper bounds. */
        private fun stagingMatchesExpected(staging: UserDataCounts, expected: UserDataCounts): Boolean {
            if (staging.dictionary <= 0) return false
            if (staging.annotations != expected.annotations) return false
            if (staging.userLists != expected.userLists) return false
            if (staging.freq != expected.freq) return false
            if (staging.listEntries > expected.listEntries) return false
            if (staging.widgets > expected.widgets) return false
            if (staging.annotations < 0 || staging.listEntries < 0 || staging.userLists < 0 ||
                staging.widgets < 0 || staging.freq < 0) return false
            return true
        }

        private suspend fun verifyDbFile(file: PlatformFile): UserDataCounts? = withContext(AppDispatchers.IO) {
            try {
                if (!file.exists()) return@withContext null
                var db: ChineseWordsDatabase? = null
                try {
                    db = buildDatabase(createRoomDatabaseBuilderFromFile(file))
                    val counts = collectUserCounts(db)
                    if (counts.dictionary < 0) return@withContext null
                    counts
                } finally {
                    try { db?.close() } catch (_: Exception) {}
                }
            } catch (_: Exception) { null }
        }

        /** Snapshot [source] into [dest] with VACUUM INTO so the backup is a
         *  consistent image even with WAL present. Dest is in liveDir (durable). */
        suspend fun vacuumInto(source: ChineseWordsDatabase, dest: PlatformFile) = withContext(AppDispatchers.IO) {
            deleteFileAndSidecars(dest)
            source.useWriterConnection { connection ->
                try { connection.executeSQL("PRAGMA wal_checkpoint(TRUNCATE)") } catch (_: Exception) {}
                connection.executeSQL("VACUUM INTO '${dest.path.replace("'", "''")}'")
            }
            if (!dest.exists()) throw IllegalStateException("Backup snapshot failed at ${dest.path}")
        }

        suspend fun getInstance(): DatabaseHelper = withContext(AppDispatchers.IO) {
            INSTANCE?.let { return@withContext it } // for optimization

            mutex.withLock {
                INSTANCE?.let { return@withContext it } // for safety

                val instance = DatabaseHelper()
                instance._db = createRoomDatabaseLive()
                instance.DATABASE_LIVE_DIR = getDatabaseLiveDir()
                instance.DATABASE_LIVE_PATH = getDatabaseLiveFile()

                // safely assign singleton
                INSTANCE = INSTANCE ?: instance
            }

            return@withContext INSTANCE!!
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE chinese_word ADD COLUMN collocations TEXT DEFAULT ''")
            }
        }

        /**
         * Dictionary data is supplied by the versioned asset.  Definitions are deliberately
         * not migrated from the old bundled dictionary: only user-owned tables are retained.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chinese_word_new` (
                        `simplified` TEXT NOT NULL,
                        `traditional` TEXT,
                        `hsk_level` TEXT,
                        `pinyins` TEXT,
                        `popularity` INTEGER,
                        `examples` TEXT DEFAULT '',
                        `collocations` TEXT DEFAULT '',
                        `modality` TEXT DEFAULT 'N/A',
                        `type` TEXT DEFAULT 'N/A',
                        `synonyms` TEXT DEFAULT '',
                        `antonym` TEXT DEFAULT '',
                        `searchable_text` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`simplified`)
                    )
                """.trimIndent())
                connection.execSQL("""
                    INSERT INTO `chinese_word_new`
                    (`simplified`, `traditional`, `hsk_level`, `pinyins`, `popularity`, `examples`, `collocations`, `modality`, `type`, `synonyms`, `antonym`, `searchable_text`)
                    SELECT `simplified`, `traditional`, `hsk_level`, `pinyins`, `popularity`, `examples`, `collocations`, `modality`, `type`, `synonyms`, `antonym`, ''
                    FROM `chinese_word`
                """.trimIndent())
                connection.execSQL("DROP TABLE `chinese_word`")
                connection.execSQL("ALTER TABLE `chinese_word_new` RENAME TO `chinese_word`")

                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chinese_word_annotation_new` (
                        `a_simplified` TEXT NOT NULL,
                        `a_pinyins` TEXT,
                        `notes` TEXT,
                        `class_type` TEXT,
                        `class_level` TEXT,
                        `themes` TEXT,
                        `first_seen` INTEGER,
                        `is_exam` INTEGER,
                        `a_searchable_text` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`a_simplified`)
                    )
                """.trimIndent())
                connection.execSQL("""
                    INSERT INTO `chinese_word_annotation_new`
                    (`a_simplified`, `a_pinyins`, `notes`, `class_type`, `class_level`, `themes`, `first_seen`, `is_exam`, `a_searchable_text`)
                    SELECT `a_simplified`, `a_pinyins`, `notes`, `class_type`, `class_level`, `themes`, `first_seen`, `is_exam`, ''
                    FROM `chinese_word_annotation`
                """.trimIndent())
                connection.execSQL("DROP TABLE `chinese_word_annotation`")
                connection.execSQL("ALTER TABLE `chinese_word_annotation_new` RENAME TO `chinese_word_annotation`")

                connection.execSQL("""
                    CREATE TABLE IF NOT EXISTS `word_definition` (
                        `simplified` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `definition` TEXT NOT NULL,
                        PRIMARY KEY(`simplified`, `language`),
                        FOREIGN KEY(`simplified`) REFERENCES `chinese_word`(`simplified`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_word_definition_language_definition` ON `word_definition` (`language`, `definition`)")

                // Recreate FTS tables and triggers to ensure they are linked to the new content tables
                connection.execSQL("DROP TABLE IF EXISTS `chinese_word_fts`")
                connection.execSQL("CREATE VIRTUAL TABLE `chinese_word_fts` USING FTS5(`searchable_text`, `simplified`, `traditional`, tokenize=`unicode61`, content=`chinese_word`)")
                connection.execSQL("INSERT INTO `chinese_word_fts`(`rowid`, `searchable_text`, `simplified`, `traditional`) SELECT `rowid`, `searchable_text`, `simplified`, `traditional` FROM `chinese_word`")

                connection.execSQL("DROP TABLE IF EXISTS `word_definition_fts`")
                connection.execSQL("CREATE VIRTUAL TABLE `word_definition_fts` USING FTS5(`simplified`, `language`, `definition`, tokenize=`unicode61`, content=`word_definition`)")
                connection.execSQL("INSERT INTO `word_definition_fts`(`rowid`, `simplified`, `language`, `definition`) SELECT `rowid`, `simplified`, `language`, `definition` FROM `word_definition`")

                connection.execSQL("DROP TABLE IF EXISTS `chinese_word_annotation_fts`")
                connection.execSQL("CREATE VIRTUAL TABLE `chinese_word_annotation_fts` USING FTS5(`a_searchable_text`, `a_simplified`, `notes`, `themes`, tokenize=`unicode61`, content=`chinese_word_annotation`)")
                connection.execSQL("INSERT INTO `chinese_word_annotation_fts`(`rowid`, `a_searchable_text`, `a_simplified`, `notes`, `themes`) SELECT `rowid`, `a_searchable_text`, `a_simplified`, `notes`, `themes` FROM `chinese_word_annotation`")

                // Re-register Room's FTS sync triggers
                val triggers = listOf(
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_fts_BEFORE_UPDATE BEFORE UPDATE ON `chinese_word` BEGIN DELETE FROM `chinese_word_fts` WHERE `rowid`=OLD.`rowid`; END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_fts_BEFORE_DELETE BEFORE DELETE ON `chinese_word` BEGIN DELETE FROM `chinese_word_fts` WHERE `rowid`=OLD.`rowid`; END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_fts_AFTER_UPDATE AFTER UPDATE ON `chinese_word` BEGIN INSERT INTO `chinese_word_fts`(`rowid`, `searchable_text`, `simplified`, `traditional`) VALUES (NEW.`rowid`, NEW.`searchable_text`, NEW.`simplified`, NEW.`traditional`); END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_fts_AFTER_INSERT AFTER INSERT ON `chinese_word` BEGIN INSERT INTO `chinese_word_fts`(`rowid`, `searchable_text`, `simplified`, `traditional`) VALUES (NEW.`rowid`, NEW.`searchable_text`, NEW.`simplified`, NEW.`traditional`); END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_word_definition_fts_BEFORE_UPDATE BEFORE UPDATE ON `word_definition` BEGIN DELETE FROM `word_definition_fts` WHERE `rowid`=OLD.`rowid`; END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_word_definition_fts_BEFORE_DELETE BEFORE DELETE ON `word_definition` BEGIN DELETE FROM `word_definition_fts` WHERE `rowid`=OLD.`rowid`; END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_word_definition_fts_AFTER_UPDATE AFTER UPDATE ON `word_definition` BEGIN INSERT INTO `word_definition_fts`(`rowid`, `simplified`, `language`, `definition`) VALUES (NEW.`rowid`, NEW.`simplified`, NEW.`language`, NEW.`definition`); END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_word_definition_fts_AFTER_INSERT AFTER INSERT ON `word_definition` BEGIN INSERT INTO `word_definition_fts`(`rowid`, `simplified`, `language`, `definition`) VALUES (NEW.`rowid`, NEW.`simplified`, NEW.`language`, NEW.`definition`); END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_annotation_fts_BEFORE_UPDATE BEFORE UPDATE ON `chinese_word_annotation` BEGIN DELETE FROM `chinese_word_annotation_fts` WHERE `rowid`=OLD.`rowid`; END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_annotation_fts_BEFORE_DELETE BEFORE DELETE ON `chinese_word_annotation` BEGIN DELETE FROM `chinese_word_annotation_fts` WHERE `rowid`=OLD.`rowid`; END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_annotation_fts_AFTER_UPDATE AFTER UPDATE ON `chinese_word_annotation` BEGIN INSERT INTO `chinese_word_annotation_fts`(`rowid`, `a_searchable_text`, `a_simplified`, `notes`, `themes`) VALUES (NEW.`rowid`, NEW.`a_searchable_text`, NEW.`a_simplified`, NEW.`notes`, NEW.`themes`); END",
                    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chinese_word_annotation_fts_AFTER_INSERT AFTER INSERT ON `chinese_word_annotation` BEGIN INSERT INTO `chinese_word_annotation_fts`(`rowid`, `a_searchable_text`, `a_simplified`, `notes`, `themes`) VALUES (NEW.`rowid`, NEW.`a_searchable_text`, NEW.`a_simplified`, NEW.`notes`, NEW.`themes`); END"
                )
                triggers.forEach { connection.execSQL(it) }
            }
        }

        private fun buildDatabase(databaseBuilder: DatabaseBuilderWithPath): ChineseWordsDatabase {
            val sqlDriver = BundledSQLiteDriver()
            Logger.d(tag=TAG, messageString = "buildDatabase entering - ${databaseBuilder.file}")
            val finalBuilder = databaseBuilder.builder
                .setDriver(sqlDriver)
                .setQueryCoroutineContext(AppDispatchers.IO)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)

            val db = finalBuilder.build()
            db._databaseFile = databaseBuilder.file

            Logger.d(tag=TAG, messageString = "buildDatabase exiting - ${databaseBuilder.file}")
            return db
        }

        suspend fun createRoomDatabaseLive() : ChineseWordsDatabase =
            buildDatabase(createRoomDatabaseBuilderLive())

        suspend fun createRoomDatabaseFromFile(file: PlatformFile) : ChineseWordsDatabase =
            buildDatabase(createRoomDatabaseBuilderFromFile(file))

        suspend fun createRoomDatabaseFromAsset() : ChineseWordsDatabase =
            buildDatabase(createRoomDatabaseBuilderFromAsset())

        /** ToDo: could there be a better way to handle DB updates? In AppViewModel it's too late,
         * and here, well, it's in a weird disconnected place.
         */
        private suspend fun createRoomDatabaseBuilderLive(): DatabaseBuilderWithPath = withContext(
            AppDispatchers.IO
        ) {
            val liveFile = getDatabaseLiveFile()
            val stagingFile = getStagingFile()
            val backupFile = getBackupFile()
            val journalFile = getJournalFile()

            // Always run crash recovery first, before any update decision and
            // before cache cleanup can destroy evidence.
            recoverPendingUpdate(liveFile, stagingFile, backupFile, journalFile)

            if (!liveFile.exists()) {
                copyDatabaseAssetFile(liveFile)
                if (!liveFile.exists()) throw IllegalStateException(
                    "Failed to provision live database from asset at ${liveFile.path}"
                )
            } else if (shouldUpdateDatabaseFromAsset(HSKAppServices.appPreferences.appVersionCode.value)) {
                performStagedUpdate(liveFile, stagingFile, backupFile, journalFile)
            }

            return@withContext createRoomDatabaseBuilderFromFile(liveFile)
        }

        /**
         * Blue-green update: live is never overwritten in place. The new asset is
         * prepared in [stagingFile] (same directory, same filesystem), user data is
         * imported there and verified, then staging is atomically moved over live.
         * A durable [.bak] snapshot plus a small state journal make every crash
         * window recoverable via [recoverPendingUpdate]. The backup is kept until
         * the next update overwrites it.
         */
        private suspend fun performStagedUpdate(
            liveFile: PlatformFile,
            stagingFile: PlatformFile,
            backupFile: PlatformFile,
            journalFile: PlatformFile
        ) = withContext(AppDispatchers.IO) {
            _updateProgress.value = 0f
            var original: ChineseWordsDatabase? = null
            var source: ChineseWordsDatabase? = null
            var newDb: ChineseWordsDatabase? = null
            try {
                HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.database_update_start)

                // 1. Consistent durable backup of the current live DB (VACUUM INTO
                // handles WAL). This is the fallback for every later step.
                original = buildDatabase(createRoomDatabaseBuilderFromFile(liveFile))
                vacuumInto(original, backupFile)
                val expected = collectUserCounts(original)
                if (expected.annotations < 0 || expected.userLists < 0 || expected.freq < 0) {
                    throw IllegalStateException("Could not read user data from live database")
                }
                if (expected.annotations == 0 && expected.listEntries == 0 &&
                    expected.widgets == 0 && expected.freq == 0
                ) {
                    Logger.i(tag = TAG, messageString = "Live DB holds no user data, aborting update")
                    throw IllegalStateException("Database is empty")
                }
                _updateProgress.value = 20f
                try { original.close() } catch (_: Exception) {}
                original = null

                // 2. Stage the fresh asset next to live (never over live).
                deleteFileAndSidecars(stagingFile)
                copyDatabaseAssetFile(stagingFile, overwrite = true)
                if (!stagingFile.exists()) throw IllegalStateException(
                    "Failed to stage database asset at ${stagingFile.path}"
                )
                _updateProgress.value = 45f

                // 3. Import user data from the durable backup into staging.
                source = buildDatabase(createRoomDatabaseBuilderFromFile(backupFile))
                newDb = buildDatabase(createRoomDatabaseBuilderFromFile(stagingFile))
                replaceUserDataInDB(newDb, source)
                try { source.close() } catch (_: Exception) {}
                source = null
                _updateProgress.value = 70f

                // 4. Verify staging before it ever becomes live.
                val stagingCounts = collectUserCounts(newDb)
                if (!stagingMatchesExpected(stagingCounts, expected)) {
                    throw IllegalStateException(
                        "Staged database failed verification " +
                            "(annotations=${stagingCounts.annotations} vs ${expected.annotations}, " +
                            "lists=${stagingCounts.userLists} vs ${expected.userLists})"
                    )
                }
                try { newDb.close() } catch (_: Exception) {}
                newDb = null
                _updateProgress.value = 85f

                // 5. Publish: journal first, then atomic move, then verify live.
                writeJournal(journalFile, DbUpdateJournal(DbUpdateJournal.STAGED, expected))
                completeSwap(liveFile, stagingFile, journalFile,
                    DbUpdateJournal(DbUpdateJournal.STAGED, expected))

                _updateProgress.value = 100f
                HSKAppServices.snackbar.show(SnackbarType.SUCCESS, Res.string.database_update_success)
            } catch (e: Exception) {
                HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.database_update_failure, listOf(e.message ?: ""))
                Logging.logAnalyticsError(TAG, "UpdateDatabaseFromAssetFailure", e.message ?: "")
                // Intentionally keep .bak + journal for recovery/retry. Only
                // discard staging if it never verified (live untouched then).
                try {
                    if (readJournal(journalFile) == null) deleteFileAndSidecars(stagingFile)
                } catch (_: Exception) {}
            } finally {
                try { newDb?.close() } catch (_: Exception) {}
                try { source?.close() } catch (_: Exception) {}
                try { original?.close() } catch (_: Exception) {}
                _updateProgress.value = null
                // Cache-only cleanup. LiveDir .new/.bak/journal are recovery
                // evidence and must survive failures.
                cleanTempDatabaseFiles()
            }
        }


        /** Best-effort crash recovery. Runs before any update decision and before
         *  cache cleanup touches anything. Never deletes the backup on failure. */
        private suspend fun recoverPendingUpdate(
            liveFile: PlatformFile,
            stagingFile: PlatformFile,
            backupFile: PlatformFile,
            journalFile: PlatformFile
        ) = withContext(AppDispatchers.IO) {
            val journal = readJournal(journalFile)
            if (journal == null) {
                // Stray staging without journal => live was never touched. Drop it.
                if (stagingFile.exists()) {
                    Logger.d(tag = TAG, messageString = "Recovery: discarding stray staging file")
                    deleteFileAndSidecars(stagingFile)
                }
                // Live missing but backup present => restore.
                if (!liveFile.exists() && backupFile.exists()) {
                    Logger.d(tag = TAG, messageString = "Recovery: live missing, restoring backup")
                    try {
                        deleteSidecars(liveFile)
                        backupFile.atomicMove(liveFile)
                    } catch (e: Exception) {
                        Logger.e(tag = TAG, messageString = "Recovery: backup restore failed", throwable = e)
                    }
                }
                return@withContext
            }

            Logger.d(tag = TAG, messageString = "Recovery: pending journal state=${journal.state}")
            when (journal.state) {
                DbUpdateJournal.STAGED -> {
                    // Swap never started (or never finished writing SWAPPED).
                    // Live still holds the old DB. Complete the swap if staging verifies.
                    val liveCounts = verifyDbFile(liveFile)
                    val stagingCounts = if (stagingFile.exists()) verifyDbFile(stagingFile) else null
                    if (stagingCounts != null && stagingMatchesExpected(stagingCounts, journal.expected)) {
                        try {
                            completeSwap(liveFile, stagingFile, journalFile, journal)
                        } catch (e: Exception) {
                            Logger.e(tag = TAG, messageString = "Recovery: staged swap failed", throwable = e)
                        }
                    } else {
                        Logger.d(tag = TAG, messageString = "Recovery: staging invalid, discarding")
                        deleteFileAndSidecars(stagingFile)
                        if (liveCounts == null && backupFile.exists() && verifyDbFile(backupFile) != null) {
                            try {
                                deleteFileAndSidecars(liveFile)
                                backupFile.copyTo(liveFile)
                            } catch (_: Exception) {}
                        } else {
                            deleteJournal(journalFile)
                        }
                    }
                }
                DbUpdateJournal.SWAPPED -> {
                    // Swap was attempted. Live should be the new DB.
                    val liveCounts = verifyDbFile(liveFile)
                    if (liveCounts != null && stagingMatchesExpected(liveCounts, journal.expected)) {
                        Logger.d(tag = TAG, messageString = "Recovery: swapped live verified")
                        deleteJournal(journalFile)
                    } else if (backupFile.exists() && verifyDbFile(backupFile) != null) {
                        Logger.d(tag = TAG, messageString = "Recovery: swapped live bad, restoring backup")
                        try {
                            deleteFileAndSidecars(liveFile)
                            backupFile.copyTo(liveFile)
                            deleteSidecars(liveFile)
                        } catch (e: Exception) {
                            Logger.e(tag = TAG, messageString = "Recovery: backup restore failed", throwable = e)
                        } finally {
                            // Keep journal only if live still bad; else clear so next boot retries fresh.
                            if (verifyDbFile(liveFile) != null) deleteJournal(journalFile)
                        }
                    } else {
                        Logger.e(tag = TAG, messageString = "Recovery: live and backup both unverifiable, keeping live as-is")
                    }
                    // Staging was moved over live; any leftover is garbage.
                    if (stagingFile.exists() && !liveFile.exists()) {
                        try { stagingFile.atomicMove(liveFile) } catch (_: Exception) {}
                    } else if (stagingFile.exists()) {
                        deleteFileAndSidecars(stagingFile)
                    }
                }
                else -> deleteJournal(journalFile)
            }
        }

        /** live -> replaced by staging. Callers must have closed every handle on
         *  both files. Backup already holds the pre-swap snapshot. */
        private suspend fun completeSwap(
            liveFile: PlatformFile,
            stagingFile: PlatformFile,
            journalFile: PlatformFile,
            journal: DbUpdateJournal
        ) = withContext(AppDispatchers.IO) {
            deleteSidecars(liveFile)
            deleteSidecars(stagingFile)
            writeJournal(journalFile, journal.copy(state = DbUpdateJournal.SWAPPED))
            try {
                stagingFile.atomicMove(liveFile)
            } catch (_: Exception) {
                // atomicMove may not overwrite: live content is safe in .bak, so
                // delete-then-move is an acceptable fallback (journal covers it).
                deleteFileAndSidecars(liveFile)
                stagingFile.atomicMove(liveFile)
            }
            deleteSidecars(liveFile)
            val liveCounts = verifyDbFile(liveFile)
                ?: throw IllegalStateException("Swapped live database failed verification")
            if (!stagingMatchesExpected(liveCounts, journal.expected)) {
                throw IllegalStateException(
                    "Swapped live user data mismatch (annotations=${liveCounts.annotations} vs ${journal.expected.annotations})"
                )
            }
            deleteJournal(journalFile)
            // .bak is intentionally kept as durable fallback until the next update overwrites it.
            Logger.i(tag = TAG, messageString = "Database swap complete and verified")
        }

        fun shouldUpdateDatabaseFromAsset(appVersion: Int): Boolean {
            if (appVersion == 0) return false // first launch, nothing to update

            val updateDbVersions = listOf(32, 37, 48, 64, 65)

            return updateDbVersions.any { updateVersion ->
                appVersion < updateVersion && Utils.getAppVersion() >= updateVersion
            }
        }

        private suspend fun createRoomDatabaseBuilderFromAsset(): DatabaseBuilderWithPath = withContext(
            AppDispatchers.IO
        ) {
            val tempFile = FileKit.cacheDir / (TEMP_FILE_PREFIX + Utils.getRandomString(10))
            copyDatabaseAssetFile(tempFile)
            return@withContext createRoomDatabaseBuilderFromFile(tempFile)
        }

        suspend fun cleanTempDatabaseFiles() = withContext(AppDispatchers.IO) {
            val dir = FileKit.cacheDir
            val filesToDelete = dir.list().filter { file ->
                // Return true for files that match the pattern
                file.name.contains(TEMP_FILE_PREFIX)
            }

            // Delete the matching files
            filesToDelete.forEach { file ->
                try {
                    file.delete()
                    Logger.d(
                        tag = TAG,
                        messageString = "Deleted Temp DB file: ${file.absolutePath()}"
                    )
                } catch (_: Exception) {
                    Logger.d(
                        tag = TAG,
                        messageString = "Failed to delete temp DB file: ${file.absolutePath()}"
                    )
                }
            }
        }


        suspend fun postReplaceUserDataInDB() {
            Logger.d(tag = TAG, messageString = "Starting to rebuild the Annotated & Exam lists")
            HSKAppServices.wordListRepo.buildListSystemExam()
            HSKAppServices.wordListRepo.buildListSystemAnnotated()
        }

        suspend fun replaceUserDataInDB(
            dbToUpdate: ChineseWordsDatabase,
            updateWith: ChineseWordsDatabase
        ) {
            withContext(AppDispatchers.IO) {
                Logger.d(tag = TAG, messageString = "Initiating Database Restoration: reading file")
                val importedAnnotations = updateWith.chineseWordAnnotationDAO().getAll()
                val importedListEntries = updateWith.wordListDAO().getUserListEntries()
                val importedLists = updateWith.wordListDAO().getUserLists()
                val importedWidgets = updateWith.widgetListDAO().getAllEntries()
                val importedFreq = updateWith.chineseWordFrequencyDAO().getAll()
                val systemLists = updateWith.wordListDAO().getSystemLists()
                if (importedAnnotations.isEmpty() && importedListEntries.isEmpty()
                    && importedWidgets.isEmpty() && importedFreq.isEmpty()
                ) {
                    Logger.i(tag = TAG, messageString = "Backup is empty or incompatible, aborting")
                    throw IllegalStateException("Database is empty")
                }

                // All deletes + re-inserts run in a single transaction so an
                // interruption can't leave the live DB half-wiped.
                dbToUpdate.useWriterConnection { connection ->
                    connection.immediateTransaction {
                        // Impoooort
                        Logger.d(tag = TAG, messageString = "Starting to import Annotations to local DB")
                        dbToUpdate.chineseWordAnnotationDAO().deleteAll()
                        dbToUpdate.chineseWordAnnotationDAO().insertAll(importedAnnotations)

                        Logger.d(tag = TAG, messageString = "Starting to import Word_List to local DB")
                        dbToUpdate.wordListDAO().deleteAllUserEntries()
                        dbToUpdate.wordListDAO().deleteAllUserLists()

                        val oldToNewListIdMap = mutableMapOf<Long, Long>()
                        val targetSystemLists = dbToUpdate.wordListDAO().getSystemLists()
                        systemLists.forEach { sourceSystemList ->
                            targetSystemLists.find { target -> target.name == sourceSystemList.name }?.let { matchingTarget ->
                                oldToNewListIdMap[sourceSystemList.id] = matchingTarget.id
                            }
                        }

                        importedLists.forEach { listWithCount ->
                            val oldId = listWithCount.id
                            val newId = dbToUpdate.wordListDAO().insertList(listWithCount.wordList.copy(id = 0))
                            oldToNewListIdMap[oldId] = newId
                        }

                        val validWords = dbToUpdate.chineseWordDAO().getAllSimplifiedWords().toSet() +
                                dbToUpdate.chineseWordAnnotationDAO().getAllSimplifiedAnnotations().toSet()

                        val remappedAndValidListEntries = importedListEntries.mapNotNull { entry ->
                            val newListId = oldToNewListIdMap[entry.listId] ?: return@mapNotNull null
                            if (!validWords.contains(entry.simplified)) return@mapNotNull null
                            entry.copy(listId = newListId)
                        }

                        dbToUpdate.wordListDAO().insertAllWords(remappedAndValidListEntries)

                        Logger.d(tag = TAG, messageString = "Starting to update the AnkiDeckIds on System lists")
                        systemLists.forEach { sourceSystemList ->
                            val targetListId = oldToNewListIdMap[sourceSystemList.id] ?: return@forEach
                            try {
                                dbToUpdate.wordListDAO().updateAnkiDeckId(targetListId, sourceSystemList.ankiDeckId)
                            } catch (e: Exception) {
                                Logger.d(tag = TAG, messageString = "Couldn't update the AnkiDeckIds on list $targetListId", throwable = e)
                            }
                        }

                        // Can't do the word lists population here, so pushing it to the App main process.

                        Logger.d(tag = TAG, messageString = "Starting to import WordFrequency to local DB")
                        dbToUpdate.chineseWordFrequencyDAO().deleteAll()
                        dbToUpdate.chineseWordFrequencyDAO().insertAll(importedFreq)

                        Logger.d(tag = TAG, messageString = "Starting to import WidgetList to local DB")
                        dbToUpdate.widgetListDAO().deleteAllWidgets()

                        val finalImportedWidgets = importedWidgets.mapNotNull { widget ->
                            val newListId = oldToNewListIdMap[widget.listId] ?: return@mapNotNull null
                            widget.copy(listId = newListId)
                        }

                        dbToUpdate.widgetListDAO().insertListsToWidget(finalImportedWidgets)
                    }
                }

                Logger.i(tag = TAG, messageString = "Database import done")
            }
        }
    }

    suspend fun replaceLiveUserDataFromFile(updateFrom: PlatformFile) {
        // only copy to cache if not already in cache
        var finalFile = updateFrom
        if (! updateFrom.absolutePath().contains(FileKit.cacheDir.path)) {
            finalFile = FileKit.cacheDir / (TEMP_FILE_PREFIX + updateFrom.name)
            updateFrom.copyTo(finalFile)
        }

        val sourceDb = createRoomDatabaseFromFile(finalFile)
        try {
            replaceUserDataInDB(liveDatabase, sourceDb)
            postReplaceUserDataInDB()
        } finally {
            try { sourceDb.close() } catch (_: Exception) {}
            try { finalFile.delete() } catch (_: Exception) {}
        }
    }

    suspend fun snapshotLiveUserDataToFile(): PlatformFile? = try {
        val newDb = _db!!.clone() ?: return null
        try {
            newDb.truncateToUserData()
            newDb.snapshotToFile()
        } finally {
            try { newDb.close() } catch (_: Exception) {}
        }
    } catch (_: Exception) { null }
}

expect suspend fun createRoomDatabaseBuilderFromFile(file: PlatformFile) : DatabaseBuilderWithPath

expect suspend fun copyDatabaseAssetFile(file: PlatformFile, overwrite: Boolean = false)
