package fr.berliat.hskwidget.ui.screens.config

import androidx.lifecycle.ViewModel

import fr.berliat.googledrivebackup.GoogleDriveBackup

import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.domain.HSKAnkiDelegate
import fr.berliat.hskwidget.ui.screens.config.ankiSync.AnkiSyncViewModel
import fr.berliat.hskwidget.ui.screens.config.backupCloud.BackupCloudViewModel
import fr.berliat.hskwidget.ui.screens.config.backupDisk.BackupDiskViewModel
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

class ConfigViewModel(
    private val appConfig: AppPreferencesStore = HSKAppServices.appPreferences,
    private val widgetProvider: FlashcardWidgetProvider = FlashcardWidgetProvider(),
    ankiDelegate: HSKAnkiDelegate = HSKAppServices.ankiDelegate,
    gDriveBackup: GoogleDriveBackup
): ViewModel() {
    fun onLanguageChange(newCode: String? = null) {
        if (appConfig.dictionaryLocale.value == null) {
            widgetProvider.redrawAllFlashCardWidgets()
        }
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.CONFIG_LOCALE_CHANGE,
            mapOf(
                "APP_LOCALE" to (newCode ?: "unknown"),
                "DICT_LOCALE" to (appConfig.dictionaryLocale.value?.code ?: "app")
            )
        )
    }

    val backupDiskViewModel = BackupDiskViewModel()
    val backupCloudViewModel = BackupCloudViewModel(appConfig, gDriveBackup)
    val ankiSyncViewModel = AnkiSyncViewModel(ankiDelegate)
}
