package fr.berliat.hskwidget.ui.screens.config.backupCloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import fr.berliat.googledrivebackup.GoogleDriveBackup
import fr.berliat.googledrivebackup.GoogleDriveState

import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.domain.DatabaseHelper

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.dbrestore_failure_fileformat
import fr.berliat.hskwidget.dbrestore_failure_import
import fr.berliat.hskwidget.dbrestore_start
import fr.berliat.hskwidget.dbrestore_success
import fr.berliat.hskwidget.domain.GoogleBackupFlowState
import fr.berliat.hskwidget.domain.GoogleBackupService
import fr.berliat.hskwidget.ui.navigation.NavigationManager
import fr.berliat.hskwidget.ui.navigation.Screen
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.path

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.getString

class BackupCloudViewModel (
    val appConfig: AppPreferencesStore, val gDriveBackup: GoogleDriveBackup) :
    ViewModel() {
    val cloudLastBackup = appConfig.dbBackupCloudLastSuccess.asStateFlow()
    val isBusy = gDriveBackup.state.map { it == GoogleDriveState.Busy }
        .stateIn(
            scope = CoroutineScope(Dispatchers.Default),
            started = SharingStarted.Lazily,
            initialValue = false
        )
    private val _transferState = MutableStateFlow<BackupCloudTransferEvent?>(value = null)
    val transferState: StateFlow<BackupCloudTransferEvent?> = _transferState

    val restoreFileFrom = MutableStateFlow<Instant?>(null)

    private val _requestNotificationPermission = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestNotificationPermission: SharedFlow<Unit> = _requestNotificationPermission.asSharedFlow()

    private val cloudRestoreFile = PlatformFile(FileKit.cacheDir.path + "/" + Utils.getRandomString(10))

    init {
        viewModelScope.launch {
            GoogleBackupFlowState.globalTransferState.collect { event ->
                _transferState.value = event
            }
        }
        viewModelScope.launch {
            GoogleBackupFlowState.globalRestoreFileFrom.collect { instant ->
                restoreFileFrom.value = instant
            }
        }
    }

    fun backup() {
        gDriveBackup.login {
            _requestNotificationPermission.tryEmit(Unit)
            GoogleBackupService.startBackup()
        }

        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_BACKUPCLOUD_BACKUP)
    }

    fun restore() {
        gDriveBackup.login {
            _requestNotificationPermission.tryEmit(Unit)
            GoogleBackupService.startRestore()
        }
    }

    fun confirmRestoration() {
        HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.dbrestore_start)

        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_BACKUPCLOUD_RESTORE)
        viewModelScope.launch(AppDispatchers.IO) {
            try {
                val fileToRestore = GoogleBackupFlowState.globalCloudRestoreFile ?: cloudRestoreFile
                DatabaseHelper.getInstance().replaceLiveUserDataFromFile(fileToRestore)
                HSKAppServices.snackbar.show(SnackbarType.SUCCESS, Res.string.dbrestore_success)

                // Backup was successful, let's redirect to the dictionary & trigger widget updates, hoping any matches
                NavigationManager.navigate(Screen.Dictionary())
                withContext(AppDispatchers.Main) {
                    FlashcardWidgetProvider().updateAllFlashCardWidgets()
                }
            } catch (e: IllegalStateException) {
                HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.dbrestore_failure_fileformat)
                Logging.logAnalyticsError(
                    "BACKUP_RESTORE",
                    getString(Res.string.dbrestore_failure_fileformat),
                    e.toString()
                )
            } catch (e: Exception) {
                HSKAppServices.snackbar.show(
                    SnackbarType.ERROR,
                    Res.string.dbrestore_failure_import,
                    listOf(e.message ?: e.toString())
                )
                Logging.logAnalyticsError(
                    "BACKUP_RESTORE",
                    getString(Res.string.dbrestore_failure_import, e.message ?: e.toString()),
                    e.toString()
                )
            }
        }
    }

    fun cancel() = gDriveBackup.cancel()
}
