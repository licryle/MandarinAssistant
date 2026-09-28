package fr.berliat.hskwidget.ui.application

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase

import fr.berliat.hskwidget.core.ExpectedUtils
import fr.berliat.hskwidget.core.ExpectedUtils.INTENT_SEARCH_WORD
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.StrictModeManager
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.data.store.PrefCompat.PrefCompatMigration
import fr.berliat.hskwidget.data.store.SupportDevStore
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider
import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.ui.navigation.NavigationManager

import io.github.vinceglb.filekit.PlatformFile

actual class AppViewModel(navigationManager: NavigationManager)
    : CommonAppViewModel(navigationManager) {

    override fun init() {
        // Enable StrictMode in Debug mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            StrictModeManager.init()
        }

        super.init()
    }

    override suspend fun finishInitialization() {
        // Patch the MainActivity-built delegates with live services.
        HSKAppServices.ankiDelegate.ankiStore = HSKAppServices.ankiStore
        HSKAppServices.ankiDelegate.appConfig = HSKAppServices.appPreferences

        FlashcardWidgetProvider.init { ExpectedUtils.context } // Depends on HSKAppServices

        // Init done
        super.finishInitialization()

        syncPlayPurchases()
    }

    fun syncPlayPurchases() {
        val supportDevStore = SupportDevStore.getInstance(ExpectedUtils.context)

        lateinit var listener : SupportDevStore.SupportDevListener
        listener = object : SupportDevStore.SupportDevListener {
            override fun onTotalSpentChange(totalSpent: Float) {
                appConfig.supportTotalSpent.value = totalSpent
                // We're done, bye
                supportDevStore.removeListener(listener = listener)
            }

            override fun onQueryFailure(result: BillingResult) { }

            override fun onPurchaseSuccess(purchase: Purchase) { }

            override fun onPurchaseHistoryUpdate(purchases: Map<SupportDevStore.SupportProduct, Int>) { }

            override fun onPurchaseAcknowledgedSuccess(purchase: Purchase) { }

            override fun onPurchaseFailure(purchase: Purchase?, billingResponseCode: Int) { }
        }

        supportDevStore.addListener(listener)

        supportDevStore.connect()
    }

    override fun handleAppUpdate() {
        if (PrefCompatMigration.shouldMigrate(ExpectedUtils.context, Utils.getAppVersion()))
            PrefCompatMigration.migrate(ExpectedUtils.context)

        super.handleAppUpdate()

        // Hack to fix an Android bug
        FlashcardWidgetProvider().updateAllFlashCardWidgets()
    }

    companion object {
        const val TAG = "AppViewModel"

        /**
         * Converts Android Intents to KMP AppIntents.
         */
        fun convertToAppIntent(intent: Intent): AppIntent? {
            // Handle Widget Configuration
            if (intent.action == ACTION_APPWIDGET_CONFIGURE) {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    return AppIntent.WidgetConfiguration(widgetId)
                }
            }

            // Handle Search Intent (Internal)
            if (intent.hasExtra(INTENT_SEARCH_WORD)) {
                val searchWord = intent.getStringExtra(INTENT_SEARCH_WORD)
                if (!searchWord.isNullOrEmpty()) {
                    return AppIntent.Search(SearchQuery.fromString(searchWord))
                }
            }

            // Handle Text Search Intent (Action Process Text)
            if (intent.action == Intent.ACTION_PROCESS_TEXT && intent.type == "text/plain") {
                val sharedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
                if (!sharedText.isNullOrEmpty()) {
                    return AppIntent.Search(SearchQuery.fromString(sharedText))
                }
            }

            // Handle Image OCR Intent (Action Send)
            if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
                intent.getParcelableExtraCompat(Intent.EXTRA_STREAM, Uri::class.java)?.let { imageUri ->
                    return AppIntent.ImageOCR(PlatformFile(imageUri))
                }
            }

            return null
        }

        private fun <T : Parcelable> Intent.getParcelableExtraCompat(key: String, clazz: Class<T>): T? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(key, clazz)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(key) as? T
            }
        }
    }
}
