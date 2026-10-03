package fr.berliat.hskwidget.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.AppServices
import fr.berliat.hskwidget.core.ExpectedUtils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.HSKAppServicesPriority
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.data.store.ChineseWordsDatabase
import fr.berliat.hskwidget.data.store.WidgetPreferencesStore
import fr.berliat.hskwidget.data.store.WidgetPreferencesStoreProvider
import fr.berliat.hskwidget.domain.WidgetController
import fr.berliat.hskwidget.domain.getWidgetControllerInstance
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.manualFileKitCoreInitialization

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.concurrent.TimeUnit

actual class FlashcardWidgetProvider actual constructor()
    : AppWidgetProvider() {
    companion object {
        private const val TAG = "WidgetProvider"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val _widgetIds = MutableStateFlow(intArrayOf())

        private lateinit var contextProvider: () -> Context
        private lateinit var widgetPrefProvider: WidgetPreferencesStoreProvider
        private lateinit var database: ChineseWordsDatabase
        private var isInitialized = false

        private suspend fun getWidgetPreferences(widgetId: Int): WidgetPreferencesStore {
            return widgetPrefProvider.invoke(widgetId)
        }

        private suspend fun getWidgetController(widgetId: Int): WidgetController {
            return getWidgetControllerInstance(
                getWidgetPreferences(widgetId),
                database)
        }

        suspend fun init(
            contextProvider: () -> Context
        ) = withContext(Dispatchers.IO) {
            Log.d(TAG, "init ${HSKAppServices.status.value}")

            FileKit.manualFileKitCoreInitialization(contextProvider.invoke())
            ExpectedUtils.init(contextProvider.invoke())

            val status = HSKAppServices.status.value
            if (status !is AppServices.Status.Ready || status.upToPrio < HSKAppServicesPriority.Widget) {
                HSKAppServices.init(HSKAppServicesPriority.Widget)

                HSKAppServices.status.first {
                    it is AppServices.Status.Failed
                            ||
                            (it is AppServices.Status.Ready
                                    && it.upToPrio >= HSKAppServicesPriority.Widget
                                    )

                }
            }

            init(
                contextProvider,
                widgetPrefProvider = HSKAppServices.widgetsPreferencesProvider,
                database = HSKAppServices.database
            )
        }

        fun init(
            contextProvider: () -> Context,
            widgetPrefProvider: WidgetPreferencesStoreProvider,
            database: ChineseWordsDatabase
        ) {
            if (!isInitialized) {
                this.contextProvider = contextProvider
                this.widgetPrefProvider = widgetPrefProvider
                this.database = database
                isInitialized = true
            }
        }

        fun getWidgetIds() : IntArray {
            val context = contextProvider.invoke()
            val appWidgetMgr = AppWidgetManager.getInstance(context)

            val widgetIds = appWidgetMgr.getAppWidgetIds(
                ComponentName(context, FlashcardWidgetProvider::class.java)
            )

            if (!_widgetIds.value.contentEquals(widgetIds)) {
                _widgetIds.value = widgetIds
            }

            return widgetIds
        }

        /**
         * This is a workaround for a Bug in handling systemwide events.
         * An empty WorkManager queue will trigger an APPWIGET_UPDATE event, which is undesired.
         * Read more at: https://www.reddit.com/r/android_devs/comments/llq2mw/question_why_should_it_be_expected_that/
         */
        fun preventUnnecessaryAppWidgetUpdates(context: Context) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosByTag("always_pending_work")
            if (workInfos.get().isNotEmpty()) return

            val alwaysPendingWork = OneTimeWorkRequestBuilder<DummyWorker>()
                .setInitialDelay(5000L, TimeUnit.DAYS)
                .addTag("always_pending_work")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "always_pending_work",
                ExistingWorkPolicy.KEEP,
                alwaysPendingWork
            )

            return
        }
    }

    actual suspend fun getWidgetIds(): List<Int> = Companion.getWidgetIds().asList()

    actual fun updateAllFlashCardWidgets() {
        val context = contextProvider.invoke()
        val widgetIds = Companion.getWidgetIds()
        onUpdate(context,
            AppWidgetManager.getInstance(context),
            widgetIds)
    }

    actual fun redrawAllFlashCardWidgets() {
        val context = contextProvider.invoke()
        val widgetIds = Companion.getWidgetIds()
        scope.launch(AppDispatchers.IO) {
            if (!isInitialized) init { context }

            for (appWidgetId in widgetIds) {
                // Switch to the IO dispatcher to perform background work
                withContext(Dispatchers.IO) {
                    getWidgetController(appWidgetId).redraw()
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.i(TAG, "onUpdate")
        // There may be multiple widgets active, so update all of them
        scope.launch(AppDispatchers.IO) {
            if (!isInitialized) init { context }

            for (appWidgetId in appWidgetIds) {
                // Switch to the IO dispatcher to perform background work
                withContext(Dispatchers.IO) {
                    getWidgetController(appWidgetId).updateWord()
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.i(TAG, "onDeleted")

        super.onDeleted(context, appWidgetIds)

        scope.launch(AppDispatchers.IO) {
            if (!isInitialized) init { context }
            for (appWidgetId in appWidgetIds) {
                getWidgetController(appWidgetId).clearWidgetUpdate()
            }
        }

        // When the user deletes the widget, delete the preference associated with it.
        scope.launch(Dispatchers.IO) {
            if (!isInitialized) init { context }

            for (widgetId in appWidgetIds) {
                Logging.logAnalyticsWidgetAction(Logging.ANALYTICS_EVENTS.WIGDET_REMOVE, widgetId)

                getWidgetPreferences(widgetId).clear()
            }

            Companion.getWidgetIds() // Update local value and listeners
        }
    }

    internal suspend fun updateWidgets(context: Context, intent: Intent, widgetId: Int) = withContext(Dispatchers.IO) {
        var widgetIds = IntArray(1)
        if (widgetId == -1) {
            widgetIds = Companion.getWidgetIds()

            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.AUTO_WORD_CHANGE)
        } else {
            widgetIds[0] = widgetId

            var event = Logging.ANALYTICS_EVENTS.WIDGET_MANUAL_WORD_CHANGE
            if (intent.action == WidgetController.ACTION_AUTO_UPDATE)
                event = Logging.ANALYTICS_EVENTS.AUTO_WORD_CHANGE

            Logging.logAnalyticsWidgetAction(event, widgetId)
        }

        widgetIds.forEach {
            getWidgetController(it).updateWord()
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "onReceive (action: ${intent?.action})")

        if (context == null) return

        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1

        scope.launch(Dispatchers.IO) {
            if (!isInitialized) init { context }

            when (intent!!.action) {
                WidgetController.ACTION_CONFIGURE_LATEST -> {
                    getWidgetController(Companion.getWidgetIds().last()).startActivityToConfigure()
                    Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.WIGDET_ADD)
                }

                AppWidgetManager.ACTION_APPWIDGET_CONFIGURE -> {
                    getWidgetController(Companion.getWidgetIds().last()).startActivityToConfigure()
                }

                WidgetController.ACTION_SPEAK -> {
                    getWidgetController(widgetId).speakWord()
                }

                WidgetController.ACTION_DICTIONARY -> {
                    getWidgetController(widgetId).openDictionary()
                }

                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    if (widgetId == -1) {
                        for (appWidgetId in Companion.getWidgetIds()) {
                            getWidgetController(appWidgetId).scheduleWidgetUpdate()
                        }
                    } else {
                        getWidgetController(widgetId).scheduleWidgetUpdate()
                    }

                    updateWidgets(context, intent, widgetId)
                }

                AppWidgetManager.ACTION_APPWIDGET_UPDATE, WidgetController.ACTION_AUTO_UPDATE ->
                    updateWidgets(context, intent, widgetId)

                else -> {
                    super.onReceive(context, intent)
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        Log.i(TAG, "onAppWidgetOptionsChanged")
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Logging.logAnalyticsWidgetAction(Logging.ANALYTICS_EVENTS.WIGDET_RESIZE, appWidgetId)

        context?.let {
            scope.launch(AppDispatchers.IO) {
                getWidgetController(appWidgetId).redraw()
            }
        }
    }

    override fun onEnabled(context: Context) {
        Log.i(TAG, "onEnabled")
        // Enter relevant functionality for when the first widget is created
        super.onEnabled(context)

        preventUnnecessaryAppWidgetUpdates(context)
        scope.launch(AppDispatchers.IO) {
            for (appWidgetId in getWidgetIds()) {
                getWidgetController(appWidgetId).scheduleWidgetUpdate()
            }
        }

        val appMgr = AppWidgetManager.getInstance(context)
        onUpdate(context, appMgr, Companion.getWidgetIds())
    }

    override fun onDisabled(context: Context) {
        Log.i(TAG, "onDisabled")
        // Enter relevant functionality for when the last widget is disabled
        super.onDisabled(context)
        scope.launch(AppDispatchers.IO) {
            for (appWidgetId in getWidgetIds()) {
                getWidgetController(appWidgetId).clearWidgetUpdate()
            }
        }

        WorkManager.getInstance(context).cancelUniqueWork("always_pending_work")
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        Log.i(TAG, "onRestored")
        super.onRestored(context, oldWidgetIds, newWidgetIds)

        newWidgetIds?.let {
            scope.launch(AppDispatchers.IO) {
                for (appWidgetId in newWidgetIds) {
                    getWidgetController(appWidgetId).scheduleWidgetUpdate()
                }
            }
        }
    }

    class DummyWorker(context: Context, workerParams: WorkerParameters)
        : Worker(context, workerParams) {
        override fun doWork(): Result {
            return Result.success()
        }
    }

    /* Thank to https://stackoverflow.com/questions/25153604/get-the-size-of-my-homescreen-widget */
    class WidgetSizeProvider(
        private val context: Context // Do not pass Application context
    ) {

        private val appWidgetManager = AppWidgetManager.getInstance(context)

        fun getWidgetsSize(widgetId: Int): Pair<Int, Int> {
            val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val width = getWidgetWidth(isPortrait, widgetId)
            val height = getWidgetHeight(isPortrait, widgetId)
            val widthInPx = context.dip(width)
            val heightInPx = context.dip(height)
            return widthInPx to heightInPx
        }

        private fun getWidgetWidth(isPortrait: Boolean, widgetId: Int): Int =
            if (isPortrait) {
                getWidgetSizeInDp(widgetId, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            } else {
                getWidgetSizeInDp(widgetId, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            }

        private fun getWidgetHeight(isPortrait: Boolean, widgetId: Int): Int =
            if (isPortrait) {
                getWidgetSizeInDp(widgetId, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            } else {
                getWidgetSizeInDp(widgetId, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            }

        private fun getWidgetSizeInDp(widgetId: Int, key: String): Int =
            appWidgetManager.getAppWidgetOptions(widgetId).getInt(key, 0)

        private fun Context.dip(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()
    }
}
