package fr.berliat.hskwidget.core

import android.os.Bundle
import co.touchlab.kermit.Logger

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics

import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

actual object ExpectedLogging {
    internal actual fun logCrashalytics(e: Throwable) {
        Firebase.crashlytics.recordException(e)
    }

    internal actual fun logAnalyticsEvent(event: Logging.ANALYTICS_EVENTS,
                                          params: Map<String, String>) {
        HSKAppServices.appScope.launch(Dispatchers.IO) {
            emit(event, Logging.withCommonParams(params))
        }
    }

    internal actual fun logAnalyticsWidgetAction(event: Logging.ANALYTICS_EVENTS, widgetId: Int) {
        HSKAppServices.appScope.launch(Dispatchers.IO) {
            emit(event, Logging.withWidgetActionParams(widgetId))
        }
    }

    private fun emit(event: Logging.ANALYTICS_EVENTS, enriched: Map<String, String>) {
        val bundle = Bundle()
        enriched.forEach {
            bundle.putString(it.key, it.value)
        }

        Firebase.analytics.logEvent(event.name, bundle)
    }

    internal actual suspend fun getAnalyticsWidgetIds(): List<Int> {
        return try {
            FlashcardWidgetProvider.getWidgetIds().asList()
        } catch (e: Exception) {
            Logger.e(tag = "ExpectedLogging", messageString = "Cannot get widgets list", throwable = e)
            emptyList()
        }
    }

    internal actual suspend fun getAnalyticsWidgetSize(widgetId: Int): String {
        return try {
            val size = FlashcardWidgetProvider.WidgetSizeProvider(ExpectedUtils.context).getWidgetsSize(widgetId)
            "${size.first}x${size.second}"
        } catch (e: Exception) {
            Logger.e(tag = "ExpectedLogging", messageString = "Cannot get widget size", throwable = e)
            "UNKNOWN"
        }
    }
}