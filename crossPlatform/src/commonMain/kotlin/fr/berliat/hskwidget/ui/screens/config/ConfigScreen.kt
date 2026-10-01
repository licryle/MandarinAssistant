package fr.berliat.hskwidget.ui.screens.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.LocaleManager
import fr.berliat.hskwidget.ui.components.AppDivider
import fr.berliat.hskwidget.ui.screens.config.ankiSync.AnkiSyncView
import fr.berliat.hskwidget.ui.screens.config.backupCloud.BackupCloudView
import fr.berliat.hskwidget.ui.screens.config.backupDisk.BackupDiskView
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = remember { ConfigViewModel(
        appConfig = HSKAppServices.appPreferences,
        widgetProvider = FlashcardWidgetProvider(),
        ankiDelegate = HSKAppServices.ankiDelegate,
        gDriveBackup = HSKAppServices.gDriveBackup
    ) }
) {
    val scrollState = rememberScrollState()
    var refreshKey by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        key(refreshKey) {
            LocaleSelectionView(
                localeManager = LocaleManager,
                onLocaleChange = { code -> run {
                        refreshKey++
                        viewModel.onLanguageChange(code)
                    }
                }
            )

            AppDivider()

            BackupDiskView(viewModel = viewModel.backupDiskViewModel)

            AppDivider()

            BackupCloudView(viewModel = viewModel.backupCloudViewModel)

            if (viewModel.ankiSyncViewModel.isAvailableOnThisPlatform) {
                AppDivider()

                AnkiSyncView(viewModel = viewModel.ankiSyncViewModel)
            }

            AppDivider()
        }
    }
}
