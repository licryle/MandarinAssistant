package fr.berliat.hskwidget.ui.application

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger

import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.AppServices
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.domain.DatabaseDiskBackup
import fr.berliat.hskwidget.domain.DatabaseHelper
import fr.berliat.hskwidget.ui.navigation.Screen
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServicesPriority
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.data.store.PrefixedPreferencesStore
import fr.berliat.hskwidget.config_backup_directory_choose
import fr.berliat.hskwidget.config_backup_directory_failed_selection
import fr.berliat.hskwidget.database_update_list_system
import fr.berliat.hskwidget.dbbackup_failure_folderpermission
import fr.berliat.hskwidget.dbbackup_failure_write
import fr.berliat.hskwidget.dbbackup_success
import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.domain.BackupFolderAccess
import fr.berliat.hskwidget.ui.navigation.NavigationManager
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider
import io.github.vinceglb.filekit.FileKit
import androidx.compose.material3.SnackbarDuration

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

expect class AppViewModel: CommonAppViewModel

open class CommonAppViewModel(val navigationManager: NavigationManager): ViewModel() {
    var _appConfig: AppPreferencesStore? = null
    val appConfig
        get() = _appConfig!!

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    // Used for listening/applying the widget configuration change.
    private val _finishWidgetConfig = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val finishWidgetConfig: SharedFlow<Int> = _finishWidgetConfig

    // Queue for actions that need to be executed after initialization
    private val pendingActions = mutableListOf<() -> Unit>()
	private val pendingActionsMutex = Mutex()

    open fun init() {
        Logging.setupFileLogging()
        HSKAppServices.init(HSKAppServicesPriority.PartialApp)
        // Launch a coroutine that reacts to changes
        viewModelScope.launch(AppDispatchers.IO) {
            HSKAppServices.status.collect { status ->
                when (status) {
                    is AppServices.Status.Failed -> {
                        Logger.e(tag = TAG, messageString = "HSKAppServices init failed", throwable = status.error)
                    }
                    is AppServices.Status.Ready -> {
                        if (status.upToPrio >= HSKAppServicesPriority.PartialApp) {
                            readyUp()
                            if (status.upToPrio >= HSKAppServicesPriority.FullApp) {
                                // Already ready from a previous launch still in memory
                                executePendingActions()
                            } else {
                                finishInitialization()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        // Collect intents from the bus
        viewModelScope.launch {
            AppIntentBus.intents.collect { intent ->
                handleAppIntent(intent)
            }
        }
    }

    private fun readyUp() {
        _appConfig = HSKAppServices.appPreferences
        _isReady.value = true
    }

    protected open suspend fun finishInitialization() {
        HSKAppServices.init(HSKAppServicesPriority.FullApp)

        handleDbOperations()

        if (didUpdateApp()) handleAppUpdate()

        executePendingActions()
    }

    protected open suspend fun executePendingActions() {
        // Process any pending actions
		pendingActionsMutex.withLock {
            pendingActions.forEach { it.invoke() }
            pendingActions.clear()
        }
    }

    protected open fun handleAppUpdate() {
        viewModelScope.launch(AppDispatchers.IO) {
            var actualVersion = appConfig.appVersionCode.value
            if (actualVersion < 48 && Utils.getAppVersion() >= 48) {
                // Migrate datastore files folders
                val oldAppPrefFile = FileKit.filesDir.resolve("app.preferences_pb")
                if (oldAppPrefFile.exists()) {
                    val oldDataStore = PrefixedPreferencesStore.getDataStore(oldAppPrefFile)
                    // We create a temporary store instance with the old DataStore
                    val oldStore = AppPreferencesStore.getInstance(oldDataStore)

                    actualVersion = oldStore.appVersionCode.value
                    // Overwrite our current live config with the old values
                    appConfig.overwriteWith(oldStore)

                    oldAppPrefFile.delete()
                }

                val oldWidgetPrefFile = FileKit.filesDir.resolve("widgets.preferences_pb")
                if (oldWidgetPrefFile.exists()) {
                    val oldDataStore = PrefixedPreferencesStore.getDataStore(oldWidgetPrefFile)
                    val targetDataStore = PrefixedPreferencesStore.getDataStore(Utils.getAppDatabasePath() / "widgets.preferences_pb")

                    // Full copy of the DataStore content (to catch all widget IDs)
                    PrefixedPreferencesStore.copyAll(oldDataStore, targetDataStore)

                    oldWidgetPrefFile.delete()
                }
            }

            // Todo: Can it be cleaned-up ?
            //  Update from Database asset happens in DatabaseHelper: createRoomDatabaseBuilderLive()
            if (DatabaseHelper.shouldUpdateDatabaseFromAsset(actualVersion)) {
                HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.database_update_list_system)
                DatabaseHelper.postReplaceUserDataInDB()
                FlashcardWidgetProvider().updateAllFlashCardWidgets()
            }

            appConfig.appVersionCode.value = Utils.getAppVersion()
        }
    }

    fun didUpdateApp(): Boolean {
        return appConfig.appVersionCode.value != Utils.getAppVersion()
    }

    private fun handleDbOperations() {
        viewModelScope.launch(AppDispatchers.IO) {
            DatabaseHelper.cleanTempDatabaseFiles()
        }

        handleBackupDisk()
    }

    private fun handleBackupDisk() {
        if (! appConfig.dbBackUpDiskActive.value) return

        val bookMark = appConfig.dbBackUpDiskDirectory.value
        if (bookMark == null) {
            showBackupPermissionFixSnackbar()
            return
        }
        try {
            BackupFolderAccess.fromBookmarkData(bookMark)
        } catch (_: Exception) {
            Logging.logAnalyticsError(TAG, "BackupDiskStaleBookmark", "Stored bookmark unresolvable")
            showBackupPermissionFixSnackbar()
            return
        }
        viewModelScope.launch(AppDispatchers.IO) {
            DatabaseDiskBackup.getFolder(
                bookMark,
                onSuccess = { backupFolder ->
                    viewModelScope.launch(AppDispatchers.IO) {
                        DatabaseDiskBackup.backUp(
                            bookMark,
                            onSuccess = {
                                HSKAppServices.snackbar.show(SnackbarType.SUCCESS, Res.string.dbbackup_success)
                                viewModelScope.launch(AppDispatchers.IO) {
                                    try {
                                        DatabaseDiskBackup.cleanOldBackups(
                                            backupFolder,
                                            appConfig.dbBackUpDiskMaxFiles.value
                                        )
                                    } catch (_: Exception) {
                                        showBackupPermissionFixSnackbar()
                                    }
                                }
                            },
                            onFail = {
                                if (DatabaseDiskBackup.getPlatformFileFromBookmarkOrNull(bookMark) == null)
                                    showBackupPermissionFixSnackbar()
                                else
                                    HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.dbbackup_failure_write)
                            }
                        )
                    }
                },
                onFail = {
                    showBackupPermissionFixSnackbar()
                }
            )
        }
    }

    private fun showBackupPermissionFixSnackbar() {
        // Boot-time emission: the host collector starts a frame after isReady,
        // so wait for it instead of emitting into the void (replay = 0 drops it).
        viewModelScope.launch {
            HSKAppServices.snackbar.awaitCollector()
            HSKAppServices.snackbar.show(
                SnackbarType.ERROR,
                Res.string.dbbackup_failure_folderpermission,
                duration = SnackbarDuration.Long,
                actionLabelRes = Res.string.config_backup_directory_choose,
                onAction = { reselectBackupFolder() }
            )
        }
    }

    private fun reselectBackupFolder() {
        viewModelScope.launch(Dispatchers.Main) {
            DatabaseDiskBackup.selectFolder(
                onSuccess = { folder ->
                    viewModelScope.launch {
                        DatabaseDiskBackup.persistSelectedFolder(appConfig, folder)
                        handleBackupDisk()
                    }
                },
                onFail = {
                    HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.config_backup_directory_failed_selection)
                }
            )
        }
    }

    fun handleAppIntent(intent: AppIntent) {
        executeWhenReady {
            when (intent) {
                is AppIntent.Search -> search(intent.query, intent.source)
                is AppIntent.SearchTTS -> searchTTS(intent.query, intent.source)
                is AppIntent.WidgetConfiguration -> configureWidget(intent.widgetId)
                is AppIntent.ImageOCR -> ocrImage(intent.file, intent.source)
                is AppIntent.OCRCapture -> ocrCapture()
            }
        }
    }

    fun search(query: SearchQuery, source: IntentSources) {
        navigationManager.navigate(Screen.Dictionary(query.toString(), source))
    }

    fun searchTTS(query: SearchQuery, source: IntentSources) {
        search(query, source)
        Utils.playWordInBackground(query.query)
    }

    fun configureWidget(widgetId: Int) {
        navigationManager.navigate(Screen.Widgets(widgetId, true))
    }

    fun ocrImage(imageFile: PlatformFile, source: IntentSources) {
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.OCR_CAPTURE,
            mapOf("SOURCE" to source.toString())
        )
        navigationManager.navigate(Screen.OCRDisplay("", imageFile.path))
    }

    fun ocrCapture() {
        navigationManager.navigate(Screen.OCRCapture())
    }

    open fun finalizeWidgetConfiguration(widgetId: Int) {
        viewModelScope.launch {
            _finishWidgetConfig.emit(widgetId)
        }
    }

    /**
     * Execute an action either immediately (if initialized) or queue it for later execution.
     * This prevents race conditions when handling intents before services are ready.
     */
    protected fun executeWhenReady(action: () -> Unit) {
		viewModelScope.launch(AppDispatchers.IO) {
            if (isReady.value) {
                action.invoke()
            } else {
                pendingActionsMutex.withLock {
                    pendingActions.add(action)
                }
			}
		}
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}
