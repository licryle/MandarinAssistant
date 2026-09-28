package fr.berliat.hskwidget.domain

import fr.berliat.googledrivebackup.BackupEvent
import fr.berliat.googledrivebackup.GoogleDriveBackupFile
import fr.berliat.googledrivebackup.RestoreEvent
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.fromKBToMB
import fr.berliat.hskwidget.googledrive_backup_progress_message
import fr.berliat.hskwidget.googledrive_restore_progress_message
import fr.berliat.hskwidget.ui.screens.config.backupCloud.BackupCloudTransferEvent
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.getString

expect object GoogleBackupService {
    fun startBackup()
    fun startRestore()
}

object GoogleBackupSharedLogic {
    suspend fun runBackupInternal(
        onProgress: (progressPercent: Float, message: String) -> Unit = { _, _ -> }
    ) {
        val gDriveBackup = HSKAppServices.gDriveBackup
        try {
            GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.BackupStarted)

            val flow = gDriveBackup.backup(
                onlyKeepMostRecent = true,
                prepareFiles = {
                    val gDriveBackupSnapshot = DatabaseHelper.getInstance().snapshotLiveUserDataToFile()
                    if (gDriveBackupSnapshot == null) {
                        null
                    } else {
                        listOf(
                            GoogleDriveBackupFile.UploadFile(
                                "database.sqlite",
                                SystemFileSystem.source(gDriveBackupSnapshot.toKotlinxIoPath()).buffered(),
                                "application/octet-stream",
                                gDriveBackupSnapshot.size()
                            )
                        )
                    }
                }
            )

            flow.takeUntilInclusive { event ->
                event is BackupEvent.Success || event is BackupEvent.Failed || event is BackupEvent.Cancelled
            }.collect { event ->
                when (event) {
                    is BackupEvent.Cancelled -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.BackupCancelled)
                    }
                    is BackupEvent.Failed -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.BackupFailed(event.exception))
                    }
                    is BackupEvent.Progress -> {
                        val progressPercent = if (event.bytesTotal > 0) (event.bytesSent * 100f / event.bytesTotal) else 0f
                        val mbSent = event.bytesSent.fromKBToMB()
                        val mbTotal = event.bytesTotal.fromKBToMB()
                        val formattedMsg = getString(Res.string.googledrive_backup_progress_message, mbSent, mbTotal)

                        onProgress(progressPercent, formattedMsg)
                        GoogleBackupFlowState.globalTransferState.emit(
                            BackupCloudTransferEvent.BackupProgress(
                                fileIndex = event.fileIndex,
                                fileCount = event.fileCount,
                                bytesReceived = event.bytesSent,
                                bytesTotal = event.bytesTotal
                            )
                        )
                    }
                    is BackupEvent.Started -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.BackupStarted)
                    }
                    is BackupEvent.Success -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.BackupSuccess)
                        HSKAppServices.appPreferences.dbBackupCloudLastSuccess.value = Clock.System.now()
                        gDriveBackup.deletePreviousBackups()
                    }
                }
            }
        } catch (e: Exception) {
            GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.BackupFailed(e))
        }
    }

    suspend fun runRestoreInternal(
        onProgress: (progressPercent: Float, message: String) -> Unit = { _, _ -> }
    ) {
        val gDriveBackup = HSKAppServices.gDriveBackup
        try {
            GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.RestorationStarted)

            val targetFile = PlatformFile(FileKit.cacheDir.path + "/" + fr.berliat.hskwidget.core.Utils.getRandomString(10))

            val flow = gDriveBackup.restore(
                listOf(
                    GoogleDriveBackupFile.DownloadFile(
                        "database.sqlite",
                        SystemFileSystem.sink(targetFile.toKotlinxIoPath()).buffered()
                    )
                ),
                onlyMostRecent = true
            )

            flow.takeUntilInclusive { event ->
                event is RestoreEvent.Success || event is RestoreEvent.Failed || event is RestoreEvent.Cancelled || event is RestoreEvent.Empty
            }.collect { event ->
                when (event) {
                    is RestoreEvent.Cancelled -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.RestorationCancelled)
                    }
                    is RestoreEvent.Failed -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.RestorationFailed(event.exception))
                    }
                    is RestoreEvent.Empty -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.RestorationFailed(Exception("No Backup File")))
                    }
                    is RestoreEvent.Progress -> {
                        val progressPercent = if (event.bytesTotal > 0) (event.bytesReceived * 100f / event.bytesTotal) else 0f
                        val mbSent = event.bytesReceived.fromKBToMB()
                        val mbTotal = event.bytesTotal.fromKBToMB()
                        val formattedMsg = getString(Res.string.googledrive_restore_progress_message, mbSent, mbTotal)

                        onProgress(progressPercent, formattedMsg)
                        GoogleBackupFlowState.globalTransferState.emit(
                            BackupCloudTransferEvent.RestorationProgress(
                                fileIndex = event.fileIndex,
                                fileCount = event.fileCount,
                                bytesReceived = event.bytesReceived,
                                bytesTotal = event.bytesTotal
                            )
                        )
                    }
                    is RestoreEvent.Started -> {
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.RestorationStarted)
                    }
                    is RestoreEvent.Success -> {
                        val time = if (event.files.isNotEmpty()) event.files[0].modifiedTime else Instant.fromEpochSeconds(0)
                        GoogleBackupFlowState.globalRestoreFileFrom.value = time
                        GoogleBackupFlowState.globalCloudRestoreFile = targetFile
                        GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.RestorationSuccess)
                    }
                }
            }
        } catch (e: Exception) {
            GoogleBackupFlowState.globalTransferState.emit(BackupCloudTransferEvent.RestorationFailed(e))
        }
    }
}

fun <T> Flow<T>.takeUntilInclusive(predicate: (T) -> Boolean): Flow<T> = transformWhile { value ->
    emit(value)
    !predicate(value)
}
