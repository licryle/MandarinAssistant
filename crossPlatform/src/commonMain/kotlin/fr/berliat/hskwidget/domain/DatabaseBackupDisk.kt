package fr.berliat.hskwidget.domain

import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.YYMMDDHHMMSS
import fr.berliat.hskwidget.core.toSafeFileName
import fr.berliat.hskwidget.data.store.AppPreferencesStore

import io.github.vinceglb.filekit.BookmarkData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.createdAt
import io.github.vinceglb.filekit.lastModified
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.releaseBookmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okio.FileNotFoundException
import kotlin.time.ExperimentalTime

object DatabaseDiskBackup {
    /**
     * Clean old backups, keeping only [maxBackups] most recent files.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun cleanOldBackups(destinationFolder: PlatformFile, maxBackups: Int) {
        if (! destinationFolder.isDirectory()) throw IllegalStateException("Not a folder")

        withContext(Dispatchers.Default) {
            val files = destinationFolder.list()
                .filter { it.name.endsWith(DatabaseHelper.DATABASE_FILENAME) }
                .sortedByDescending { it.createdAt() ?: it.lastModified() }

            if (files.size > maxBackups) {
                files.drop(maxBackups).forEach { try { it.delete() } catch (_: Exception) {} }
            }
        }
    }

    fun isDirectoryAccessible(dir: PlatformFile?): Boolean {
        try {
            return dir != null
                    && dir.exists()
        } catch (_: Exception) { // Security exception on Android
            return false
        }
    }

    /**
     * Perform backup to [destinationFolder].
     */
    suspend fun backUp(
        destinationFolder: BookmarkData,
        onSuccess: () -> Unit,
        onFail: (Throwable) -> Unit
    ) = withContext(AppDispatchers.IO){
        try {
            val snapshot = DatabaseHelper.getInstance().snapshotLiveUserDataToFile()
            val timestamp = Clock.System.now()
            val filename = "${timestamp.YYMMDDHHMMSS()}_${DatabaseHelper.DATABASE_FILENAME}".toSafeFileName()

            val backupFile = PlatformFile.fromBookmarkData(destinationFolder) / filename
            snapshot!!.atomicMove(backupFile)

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) {
                onFail(e)
            }
        }
    }

    /**
     * Retrieve previously set folder.
     */
    suspend fun getFolder(
        bookMark: BookmarkData,
        onSuccess: (PlatformFile) -> Unit,
        onFail: () -> Unit
    ) {
        val accessibleFolder = getPlatformFileFromBookmarkOrNull(bookMark)
        if (accessibleFolder != null)
            onSuccess(accessibleFolder)
        else {
            selectFolder(onSuccess, onFail)
        }
    }

    fun getPlatformFileFromBookmarkOrNull(bookmark: BookmarkData?): PlatformFile? {
        if (bookmark == null) return null

        try {
            val file = PlatformFile.fromBookmarkData(bookmark)
            return if (isDirectoryAccessible(file)) {
                file
            } else {
                null
            }
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Select a folder.
     */
    suspend fun selectFolder(
        onSuccess: (PlatformFile) -> Unit,
        onFail: () -> Unit
    ) {
        val dir = FileKit.openDirectoryPicker()
        if (dir == null) {
            onFail()
        } else {
            onSuccess(dir)
        }
    }

    /**
     * Persist a newly picked folder: release the old bookmark, store the new
     * one, and re-enable disk backup. Single owner of this logic so the
     * settings screen and the startup one-tap repair can't drift apart.
     */
    suspend fun persistSelectedFolder(appConfig: AppPreferencesStore, folder: PlatformFile) {
        val oldBookmark = appConfig.dbBackUpDiskDirectory.value
        if (oldBookmark != null) {
            try {
                PlatformFile.fromBookmarkData(oldBookmark).releaseBookmark()
            } catch (_: Exception) {
            }
        }
        appConfig.dbBackUpDiskDirectory.value = folder.bookmarkData()
        appConfig.dbBackUpDiskActive.value = true
        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_BACKUP_ON)
    }

    /**
     * Select a backup file.
     */
    suspend fun selectBackupFile(
        onSuccess: (PlatformFile) -> Unit,
        onFail: (FileNotFoundException) -> Unit
    ) {
        val file = FileKit.openFilePicker()
        if (file == null || ! file.exists()) {
            onFail(FileNotFoundException("Didn't select file"))
        } else {
            onSuccess(file)
        }
    }
}
