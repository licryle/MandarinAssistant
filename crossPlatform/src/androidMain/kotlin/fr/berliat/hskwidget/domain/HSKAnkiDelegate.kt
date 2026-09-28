package fr.berliat.hskwidget.domain

import android.content.Context

import fr.berliat.ankidroidhelper.AnkiDelegate
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.anki_must_start
import fr.berliat.hskwidget.anki_not_installed
import fr.berliat.hskwidget.anki_operation_cancelled
import fr.berliat.hskwidget.anki_operation_failed
import fr.berliat.hskwidget.anki_operation_success
import fr.berliat.hskwidget.anki_permission_denied
import fr.berliat.hskwidget.core.ExpectedUtils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.data.store.AnkiStore
import fr.berliat.hskwidget.data.store.AppPreferencesStore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlin.reflect.KClass

actual class HSKAnkiDelegate(context: Context = ExpectedUtils.context,
                             handler: HandlerInterface? = null,
                             var appConfig: AppPreferencesStore? = null,
                             var ankiStore: AnkiStore? = null)  : AnkiDelegate(context, handler) {
    actual suspend fun modifyAnki(ankiAction: (suspend () -> Result<Unit>)?) {
        super.delegateToAnki(ankiAction)
    }
    actual suspend fun modifyAnkiViaService(serviceClass: KClass<out AnkiSyncWordListsService>) {
        super.delegateToAnkiService(serviceClass)
    }

    override fun onAnkiRequestPermissionsResult(granted: Boolean) {
        appConfig?.ankiSaveNotes?.value = granted
        super.onAnkiRequestPermissionsResult(granted)
        if (!granted) {
            HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.anki_permission_denied)
        }
    }

    override suspend fun startAnkiDroid(): Boolean {
        HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.anki_must_start)

        val result = super.startAnkiDroid()
        if (!result) {
            HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.anki_not_installed)
        }

        return result
    }

    override suspend fun safelyModifyAnkiDbIfAllowed(ankiDbAction: suspend () -> Result<Unit>): Result<Unit>
            = withContext(Dispatchers.IO) {
        if (!(appConfig?.ankiSaveNotes?.value ?: false))
            return@withContext Result.failure(AnkiOperationsFailures.AnkiFailure_Off)

        return@withContext super.safelyModifyAnkiDbIfAllowed(ankiDbAction)
    }

    override suspend fun ensureAnkiDroidIsRunning() = withContext(Dispatchers.IO) {
        if (!(ankiStore?.isStoreReady() ?: false)) {
            super.ensureAnkiDroidIsRunning()
        }
    }

    /*********** CallBacks ***********/
    override fun onAnkiOperationFailed(context: Context?, e: Throwable) {
        if (!(e is AnkiOperationsFailures.AnkiFailure_Deferred
            || e is AnkiOperationsFailures.AnkiFailure_Off
            || context == null
        )) {
            HSKAppServices.snackbar.show(SnackbarType.ERROR,
                Res.string.anki_operation_failed,
                listOf(e.message ?: ""))
        }

        super.onAnkiOperationFailed(context, e)
    }

    override fun onAnkiOperationSuccess(context: Context?) {
        HSKAppServices.snackbar.show(SnackbarType.SUCCESS, Res.string.anki_operation_success)

        super.onAnkiOperationSuccess(context)
    }

    override fun onAnkiOperationCancelled(context: Context?) {
        HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.anki_operation_cancelled)

        super.onAnkiOperationCancelled(context)
    }

    override fun onAnkiNotInstalled() {
        HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.anki_not_installed)
        appConfig?.ankiSaveNotes?.value = false

        super.onAnkiNotInstalled()
    }
}