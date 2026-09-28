package fr.berliat.hskwidget.core

import fr.berliat.googledrivebackup.GoogleDriveBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hskwidget.data.repo.WordListRepository
import fr.berliat.hskwidget.data.store.AnkiStore
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.data.store.ChineseWordsDatabase
import fr.berliat.hskwidget.data.store.PrefixedPreferencesStore
import fr.berliat.hskwidget.data.store.WidgetPreferencesStore
import fr.berliat.hskwidget.data.store.WidgetPreferencesStoreProvider
import fr.berliat.hskwidget.domain.DatabaseHelper
import fr.berliat.hskwidget.domain.HSKAnkiDelegate
import fr.berliat.hskwidget.domain.KAnkiDelegator
import fr.berliat.hskwidget.domain.KAnkiServiceDelegator

import io.github.vinceglb.filekit.div

sealed class HSKAppServicesPriority(priority: UInt): AppServices.Priority(priority) {
    constructor(prio : AppServices.Priority) : this(prio.priority)

    object Widget: HSKAppServicesPriority(Highest)
    object PartialApp: HSKAppServicesPriority(Standard)
    object FullApp: HSKAppServicesPriority(Standard.priority + 1u)
}

// --- Singleton instance
object HSKAppServices : AppServices() {
    init {
        registerMostServices()
    }

    fun registerAnkiDelegators(ankiDelegate: HSKAnkiDelegate) {
        if (isRegistered("ankiDelegate")) return
        registerNow("ankiDelegate", HSKAppServicesPriority.FullApp) { ankiDelegate }
        registerNow("ankiDelegator",HSKAppServicesPriority.FullApp) { ankiDelegate::modifyAnki }
        registerNow("ankiServiceDelegator", HSKAppServicesPriority.FullApp) { ankiDelegate::modifyAnkiViaService }
    }

    fun registerGoogleBackup(gDrive: GoogleDriveBackup) {
        if (isRegistered("gDriveBackup")) return
        registerNow("gDriveBackup", HSKAppServicesPriority.PartialApp) { gDrive }
    }

    // P0
    val snackbar: SnackbarManager get() = get("snackbar")

    // P0
    val database: ChineseWordsDatabase get() = get("database")
    val resources: CachedResources get() = get("resources")

    // P2
    val appPreferences: AppPreferencesStore get() = get("appPreferences")
    val widgetsPreferencesProvider: WidgetPreferencesStoreProvider get() = get("widgetsPreferencesProvider")
    val ankiStore: AnkiStore get() = get("ankiStore")

    val ankiDelegate: HSKAnkiDelegate get() = get("ankiDelegate")
    val ankiDelegator: KAnkiDelegator get() = get("ankiDelegator")
    val ankiServiceDelegator: KAnkiServiceDelegator get() = get("ankiServiceDelegator")
    val wordListRepo: WordListRepository get() = get("wordListRepo")
    val HSKSegmenter: HSKTextSegmenter get() = get("HSKSegmenter")
    val gDriveBackup: GoogleDriveBackup get() = get("gDriveBackup")

    private fun registerMostServices() {
        // Required for Widget -- Minimal Set
        register("snackbar", HSKAppServicesPriority.Widget) { SnackbarManager }
        register("resources", HSKAppServicesPriority.Widget) { CachedResources.load() }
        register("appPreferences", HSKAppServicesPriority.Widget) {
            AppPreferencesStore.getInstance(PrefixedPreferencesStore.getDataStore(Utils.getAppDatabasePath() / "app.preferences_pb"))
        }

        // Depends on appPreferences because of aggressive db update when starting up app on app updates.
        register("database", HSKAppServicesPriority.Widget, dependsOn = setOf("appPreferences")) { DatabaseHelper.getInstance().liveDatabase }

        register("widgetsPreferencesProvider", HSKAppServicesPriority.Widget) {
            val provider : WidgetPreferencesStoreProvider = { widgetId: Int ->
                val widgetDataStore = PrefixedPreferencesStore.getDataStore(Utils.getAppDatabasePath() / "widgets.preferences_pb")
                WidgetPreferencesStore.getInstance(widgetDataStore, widgetId)
            }
            provider
        }

        // Required for fullApp -- but still partial set (missing Anki & GoogleDrive) Set
        register("ankiStore", HSKAppServicesPriority.PartialApp, dependsOn = setOf("database", "appPreferences")) {
            AnkiStore(
                Utils.getAnkiDAO(),
                get<ChineseWordsDatabase>("database").wordListDAO(),
                get("appPreferences"))
        }
        register("wordListRepo", HSKAppServicesPriority.PartialApp, dependsOn = setOf("ankiStore", "database")) {
            WordListRepository(
                get("ankiStore"),
                get<ChineseWordsDatabase>("database").wordListDAO(),
                get<ChineseWordsDatabase>("database").annotatedChineseWordDAO()
            )
        }
        register("HSKSegmenter", HSKAppServicesPriority.FullApp) {
            val segmenter = Utils.getHSKSegmenter()

            get<CoroutineScope>("appScope").launch(AppDispatchers.IO) {
                segmenter.preload()
            }

            segmenter
        }
    }
}
