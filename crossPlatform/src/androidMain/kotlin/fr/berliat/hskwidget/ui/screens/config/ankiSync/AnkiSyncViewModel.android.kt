package fr.berliat.hskwidget.ui.screens.config.ankiSync

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import co.touchlab.kermit.Logger

import fr.berliat.ankidroidhelper.AnkiDelegate
import fr.berliat.ankidroidhelper.AnkiSyncServiceDelegate
import fr.berliat.hskwidget.core.YYMMDDHHMMSS
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.domain.HSKAnkiDelegate

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

actual class AnkiSyncViewModel actual constructor(
    val ankiDelegate: HSKAnkiDelegate,
    val appConfig: AppPreferencesStore)
    : ViewModel(), AnkiDelegate.HandlerInterface {
    actual val isAvailableOnThisPlatform = true

    actual val ankiActive = appConfig.ankiSaveNotes.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress(SyncState.NOT_STARTED, 0, 0, ""))
    actual val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    var ankiSyncServiceDelegate: AnkiSyncServiceDelegate? = null

    private val _requestNotificationPermission = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestNotificationPermission: SharedFlow<Unit> = _requestNotificationPermission.asSharedFlow()

    init {
        ankiDelegate.replaceListener(this)
    }

    override fun onAnkiOperationSuccess() {
        Logger.i(tag = TAG, messageString = "onAnkiOperationSuccess: completed full import into Anki")

        _syncProgress.value = SyncProgress(SyncState.SUCCESS, 0, 0,
            Clock.System.now().YYMMDDHHMMSS())
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.ANKI_SYNC_SUCCESS
        )
    }

    override fun onAnkiOperationCancelled() {
        Logger.i(tag = TAG, messageString = "onAnkiOperationCancelled: full Anki import cancelled by user")

        _syncProgress.value = SyncProgress(SyncState.CANCELLED, 0, 0, "")
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.ANKI_SYNC_CANCEL
        )
    }

    override fun onAnkiOperationFailed(e: Throwable) {
        Logger.i(tag = TAG, messageString = "onAnkiOperationFailed: failed full import into Anki")

        _syncProgress.value = SyncProgress(SyncState.FAILED, 0, 0, e.message ?: "")

        Logging.logAnalyticsError(
            "ANKI_SYNC",
            "FullAnkiImportFailed",
            e.message ?: ""
        )
    }

    override fun onAnkiSyncProgress(
        current: Int,
        total: Int,
        message: String
    ) {
        Logger.i(tag = TAG, messageString = "onAnkiImportProgress: $current / $total")

        _syncProgress.value = SyncProgress(
            SyncState.IN_PROGRESS,
            current,
            total,
            message
        )
    }

    override fun onAnkiRequestPermissionGranted() {
        appConfig.ankiSaveNotes.value = true
    }

    override fun onAnkiRequestPermissionDenied() {
        appConfig.ankiSaveNotes.value = false

        _syncProgress.value = SyncProgress(SyncState.NOT_STARTED, 0, 0, "")
    }

    override fun onAnkiServiceStarting(serviceDelegate: AnkiSyncServiceDelegate) {
        ankiSyncServiceDelegate = serviceDelegate

        requestNotificationPermissionCheck()

        _syncProgress.value = SyncProgress(SyncState.STARTING, 0, 0, "")
    }

    actual fun toggleAnkiActive(enabled: Boolean) {
        appConfig.ankiSaveNotes.value = enabled

        if (enabled) {
            importsAllNotesToAnkiDroid()
            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_ANKI_SYNC_ON)
            Logging.logAnalyticsEvent(
                Logging.ANALYTICS_EVENTS.ANKI_SYNC_START
            )
        } else {
            cancelSync()
            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.CONFIG_ANKI_SYNC_OFF)
        }
    }

    private fun importsAllNotesToAnkiDroid() {
        viewModelScope.launch {
            appConfig.ankiSaveNotes.asStateFlow().filter { it }.first() // ensure the toggle is ON as we just saved it
            ankiDelegate.modifyAnkiViaService(HSKAppServices.wordListRepo.syncListsToAnki())
        }
    }

    private fun requestNotificationPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Signal the UI to perform the check and possibly launch the dialog
            _requestNotificationPermission.tryEmit(Unit)
        }
    }

    actual fun cancelSync() {
        ankiSyncServiceDelegate?.cancelCurrentOperation()
    }

    companion object {
        private const val TAG = "AnkiSyncViewModel"
    }
}
