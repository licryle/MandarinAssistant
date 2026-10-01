package fr.berliat.hskwidget.ui.screens.config.backupDisk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.domain.DatabaseDiskBackup
import fr.berliat.hskwidget.domain.DatabaseHelper

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.config_backup_directory_failed_selection
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.dbrestore_failure_import
import fr.berliat.hskwidget.dbrestore_failure_nofileselected
import fr.berliat.hskwidget.dbrestore_start
import fr.berliat.hskwidget.dbrestore_success
import fr.berliat.hskwidget.ui.navigation.NavigationManager
import fr.berliat.hskwidget.ui.navigation.Screen
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.name

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.jetbrains.compose.resources.getString


class BackupDiskViewModel(
    val appConfig: AppPreferencesStore = HSKAppServices.appPreferences): ViewModel() {
    val backupDiskActive = appConfig.dbBackUpDiskActive.asStateFlow()
    val backupDiskMaxFiles = appConfig.dbBackUpDiskMaxFiles.asStateFlow()

    val backupDiskFolder: StateFlow<PlatformFile?> = appConfig.dbBackUpDiskDirectory
        .asStateFlow()
        .map { path -> DatabaseDiskBackup.getPlatformFileFromBookmarkOrNull(path) } // Ensuring the view only sees an accessible directory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DatabaseDiskBackup.getPlatformFileFromBookmarkOrNull(appConfig.dbBackUpDiskDirectory.value)
        )

    init {
        // Only auto-disable when no folder was ever chosen. A stale/inaccessible
        // bookmark keeps active=true so startup can offer a one-tap re-pick.
        if (appConfig.dbBackUpDiskDirectory.value == null) {
            appConfig.dbBackUpDiskActive.value = false
        }
    }

    fun toggleBackupDiskActive(active: Boolean) {
        if (!active) {
            appConfig.dbBackUpDiskActive.value = false

            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_BACKUP_OFF)
            return
        }

        if (backupDiskFolder.value == null) {
            selectBackupFolder()
        } else {
            appConfig.dbBackUpDiskActive.value = true

            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_BACKUP_ON)
        }
    }

    fun setBackupDiskMaxFiles(value: Int) {
        appConfig.dbBackUpDiskMaxFiles.value = value
    }

    fun selectRestoreFile() {
        viewModelScope.launch(Dispatchers.Main) {
            DatabaseDiskBackup.selectBackupFile(
                onSuccess = { file ->
                    HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.dbrestore_start)
                    viewModelScope.launch(AppDispatchers.IO) {
                        val dbHelper = DatabaseHelper.getInstance()
                        val copiedFile = FileKit.cacheDir / file.name

                        try {
                            file.copyTo(FileKit.cacheDir / file.name)
                            val sourceDb = DatabaseHelper.createRoomDatabaseFromFile(copiedFile)
                            DatabaseHelper.replaceUserDataInDB(dbHelper.liveDatabase, sourceDb)
                            copiedFile.delete()
                            DatabaseHelper.postReplaceUserDataInDB()

                            withContext(Dispatchers.Main) {
                                HSKAppServices.snackbar.show(SnackbarType.SUCCESS, Res.string.dbrestore_success)

                                NavigationManager.navigate(Screen.Dictionary())
                            }

                            // Backup was successful, let's trigger widget updates, hoping any matches
                            FlashcardWidgetProvider().updateAllFlashCardWidgets()

                            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_BACKUP_RESTORE)
                        } catch (e: Exception) {
                            val errorDetails = e.message ?: e.toString()
                            withContext(Dispatchers.Main) {
                                HSKAppServices.snackbar.show(
                                    SnackbarType.ERROR,
                                    Res.string.dbrestore_failure_import,
                                    listOf(errorDetails)
                                )
                            }
                            Logging.logAnalyticsError(
                                TAG,
                                "BackupDiskRestorationFailed",
                                errorDetails)
                        }
                    }
                },
                onFail = { e ->
                    viewModelScope.launch(Dispatchers.Main) {
                        HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.dbrestore_failure_nofileselected)

                        Logging.logAnalyticsError(
                            "BACKUP_RESTORE",
                            getString(Res.string.dbrestore_failure_nofileselected),
                            e.message ?: ""
                        )
                    }
                }
            )
        }
    }

    fun selectBackupFolder() {
        viewModelScope.launch(Dispatchers.Main) {
            DatabaseDiskBackup.selectFolder(
                onSuccess = { folder ->
                    // persist permissions in Platform && DataStore
                    viewModelScope.launch {
                        DatabaseDiskBackup.persistSelectedFolder(appConfig, folder)
                    }
                    Logging.logAnalyticsEvent(
                        Logging.ANALYTICS_EVENTS.BACKUPDISK_FOLDER_SELECT_SUCCESS
                    )
                },
                onFail = {
                    HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.config_backup_directory_failed_selection)
                    Logging.logAnalyticsEvent(
                        Logging.ANALYTICS_EVENTS.BACKUPDISK_FOLDER_SELECT_FAIL
                    )
                }
            )
        }
    }

    companion object {
        private const val TAG = "BackupDiskViewModel"
    }
}