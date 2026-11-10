package fr.berliat.hskwidget.ui.application

import androidx.lifecycle.viewModelScope

import fr.berliat.googledrivebackup.GoogleDriveBackup
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.domain.HSKAnkiDelegate

import kotlinx.coroutines.launch

actual class AppViewModel : CommonAppViewModel() {

    override fun onInit() {
        // iOS has no activity handing us intents: the URL handler emits on the
        // bus, so this is the only consumer. Buffered channel, no replay.
        viewModelScope.launch {
            AppIntentBus.intents.collect { intent ->
                handleAppIntent(intent)
            }
        }
    }

    override suspend fun finishInitialization() {
        HSKAppServices.registerAnkiDelegators(HSKAnkiDelegate())

		val gDrive = GoogleDriveBackup(HSKAppServices.resources.appName)
		HSKAppServices.registerGoogleBackup(gDrive)

		super.finishInitialization()
    }
}
