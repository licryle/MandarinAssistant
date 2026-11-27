package fr.berliat.hskwidget.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews

import fr.berliat.hskwidget.R
import fr.berliat.hskwidget.core.ExpectedUtils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.store.ChineseWordsDatabase
import fr.berliat.hskwidget.data.store.WidgetPreferencesStore
import fr.berliat.hskwidget.data.type.HSK_Level
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.collections.set
import kotlin.time.Clock


private val mutex = Mutex()
private val instances = mutableMapOf<Int, WidgetController>()

actual suspend fun getWidgetControllerInstance(widgetStore: WidgetPreferencesStore,
                               database: ChineseWordsDatabase): WidgetController {
    instances[widgetStore.widgetId]?.let { return it }

    val contextProvider = { ExpectedUtils.context }

    return mutex.withLock {
        instances[widgetStore.widgetId] ?:
        WidgetController(widgetStore, database, contextProvider).also { instance ->
            instances[widgetStore.widgetId] = instance
        }
    }
}

actual class WidgetController(
    widgetStore: WidgetPreferencesStore,
    database: ChineseWordsDatabase,
    val contextProvider: () -> Context)
        : CommonWidgetController(widgetStore, database) {
    companion object {
        private const val TAG = "WidgetController"
        const val ACTION_SPEAK = "fr.berliat.hskwidget.ACTION_WIDGET_SPEAK"
        const val ACTION_DICTIONARY = "fr.berliat.hskwidget.ACTION_DICTIONARY"
        const val ACTION_AUTO_UPDATE = "fr.berliat.hskwidget.ACTION_AUTOUPDATE"
        const val ACTION_CONFIGURE_LATEST = "fr.berliat.hskwidget.APPWIDGET_CONFIGURE_LATEST"

        fun requestAddDesktopWidget(context: Context, appWidgetManager: AppWidgetManager): Boolean {
            val myProvider = ComponentName(context, FlashcardWidgetProvider::class.java)
            val confIntent = Intent(context, FlashcardWidgetProvider::class.java).apply {
                action = ACTION_CONFIGURE_LATEST
            }
            // System injects EXTRA_APPWIDGET_ID via fill-in -> must be mutable.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callbackIntent = PendingIntent.getBroadcast(context, 0, confIntent, flags)

            return try {
                appWidgetManager.requestPinAppWidget(myProvider, null, callbackIntent)
            } catch (e: IllegalStateException) {
                // No foreground activity / user locked.
                false
            }
        }

        /** Thanks to https://gist.github.com/manishcm/bd05dff09b5b1640d25f **/
        internal fun getPendingSelfIntent(context: Context?, action: String?, widgetId: Int)
                : PendingIntent? {
            val intent = Intent(context, FlashcardWidgetProvider::class.java)
            intent.action = action
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

            return PendingIntent.getBroadcast(context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        internal fun getWidgetAlarmIntent(context: Context, widgetId: Int): PendingIntent {
            val intent: Intent = Intent(context, FlashcardWidgetProvider::class.java)
                .setAction(ACTION_AUTO_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            val pi = PendingIntent.getBroadcast(
                context,
                widgetId, // So it's unique to that widget
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT  or PendingIntent.FLAG_IMMUTABLE
            )
            return pi
        }
    }

    fun startActivityToConfigure() {
        val context = contextProvider.invoke()
        val confIntent = Intent(ACTION_APPWIDGET_CONFIGURE).apply {
            setPackage(context.packageName)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(confIntent)
    }

    override suspend fun redrawWidget(word: AnnotatedChineseWord?) {
        withContext(Dispatchers.IO) {
            val context = contextProvider.invoke()
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Switch back to the main thread to update UI
            withContext(Dispatchers.Main) {
                var views: RemoteViews?
                if (word == null) {
                    Log.i(TAG, "updateFlashCardWidget ID $widgetId , but no word available")

                    views = RemoteViews(
                        context.packageName,
                        R.layout.flashcard_widget_not_configured
                    ).apply {
                        setOnClickPendingIntent(
                            R.id.flashcard_not_configured,
                            getPendingSelfIntent(
                                context,
                                ACTION_APPWIDGET_CONFIGURE,
                                widgetId
                            )
                        )
                    }
                } else {
                    Log.i(TAG, "updateFlashCardWidget ID $widgetId with word $word")

                    val searchWordIntent =
                        getPendingSelfIntent(context, ACTION_DICTIONARY, widgetId)
                    // Get the layout for the widget and attach an on-click listener
                    // to the button.
                    views = RemoteViews(
                        context.packageName,
                        R.layout.flashcard_widget
                    ).apply {
                        setOnClickPendingIntent(R.id.flashcard_chinese, searchWordIntent)
                        setOnClickPendingIntent(R.id.flashcard_definition, searchWordIntent)
                        setOnClickPendingIntent(R.id.flashcard_pinyin, searchWordIntent)

                        setOnClickPendingIntent(
                            R.id.flashcard_speak,
                            getPendingSelfIntent(context, ACTION_SPEAK, widgetId)
                        )
                        setOnClickPendingIntent(
                            R.id.flashcard_reload,
                            getPendingSelfIntent(
                                context,
                                AppWidgetManager.ACTION_APPWIDGET_UPDATE,
                                widgetId
                            )
                        )

                        setTextViewText(R.id.flashcard_chinese, word.simplified)
                        val dictionaryLocale = Locale.resolve(HSKAppServices.appPreferences.dictionaryLocale.value)
                        setTextViewText(
                            R.id.flashcard_definition,
                            word.word?.definition[dictionaryLocale] ?: word.annotation?.notes ?: ""
                        )

                        setTextViewText(R.id.flashcard_pinyin, word.pinyins.toString())
                        setTextViewText(R.id.flashcard_hsklevel, word.hskLevel.toString())

                        // Responsive Visibility Logic
                        val options = appWidgetManager.getAppWidgetOptions(widgetId)
                        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                        // Hide the top row entirely if it's too short vertically
                        val showHeader = minHeight > 80
                        // Hide side icons if it's too narrow
                        val showIcons = minWidth > 110 && showHeader

                        setViewVisibility(R.id.flashcard_action_bar, if (showIcons) View.VISIBLE else View.GONE)
                        
                        setViewVisibility(R.id.flashcard_hsklevel,
                            if (word.hskLevel == HSK_Level.NOT_HSK) View.GONE else View.VISIBLE)
                    }
                }

                // Tell the AppWidgetManager to perform an update on the current widget.
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    fun scheduleWidgetUpdate() {
        if (widgetStore.refreshInterval.value < 0) { // No refresh
            clearWidgetUpdate()
            return
        }

        val context = contextProvider.invoke()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = getWidgetAlarmIntent(context, widgetId)

        val calendar: Calendar = Calendar.getInstance()
        calendar.setTimeInMillis(Clock.System.now().toEpochMilliseconds())

        am.setInexactRepeating(
            AlarmManager.RTC,
            calendar.getTimeInMillis(),
            widgetStore.refreshInterval.value,
            pi
        )
    }

    fun clearWidgetUpdate() {
        val context = contextProvider.invoke()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(getWidgetAlarmIntent(context, widgetId))
    }
}
