package fr.berliat.hskwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope

import com.google.api.client.googleapis.media.MediaHttpUploader

import fr.berliat.googledrivebackup.GoogleDriveBackup
import fr.berliat.hskwidget.core.CachedResources
import fr.berliat.hskwidget.core.ExpectedUtils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.domain.HSKAnkiDelegate
import fr.berliat.hskwidget.ui.application.AppIntentBus
import fr.berliat.hskwidget.ui.application.AppView
import fr.berliat.hskwidget.ui.application.AppViewModel
import fr.berliat.hskwidget.ui.navigation.NavigationManager
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        AppViewModel.convertToAppIntent(intent)?.let {
            AppIntentBus.emit(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FlashcardWidgetProvider.preventUnnecessaryAppWidgetUpdates(applicationContext)

        // App context first: everything downstream takes applicationContext,
        // never the Activity (no static Activity reference).
        ExpectedUtils.init(applicationContext)
        FileKit.init(this)

        // Launcher-bound delegates: cheap to construct; attachActivity()
        // registers their result launchers (must precede STARTED).
        val gDrive = GoogleDriveBackup(CachedResources.appName).apply {
            transferChunkSize = MediaHttpUploader.MINIMUM_CHUNK_SIZE * 2
            attachActivity(this@MainActivity)
        }
        HSKAppServices.registerGoogleBackup(gDrive)

        HSKAppServices.registerAnkiDelegators(
            HSKAnkiDelegate(applicationContext).apply {
                attachActivity(this@MainActivity)
            }
        )

        viewModel = AppViewModel(NavigationManager)
        viewModel.init()

        lifecycleScope.launch {
            viewModel.finishWidgetConfig.collect { widgetId ->
                val resultIntent = Intent()
                resultIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }

        AppViewModel.convertToAppIntent(intent)?.let {
            AppIntentBus.emit(it)
        }

        setContent {
            val darkTheme = isSystemInDarkTheme()
            // Once per theme change, not per recomposition.
            LaunchedEffect(darkTheme) { configureSystemBars(darkTheme) }

            AppView(viewModel = viewModel)
        }
    }

    private fun configureSystemBars(isDarkTheme: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
    }
}
