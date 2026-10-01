package fr.berliat.hskwidget.core

import swiftPMImport.fr.berliat.hskwidget.crossPlatform.FIRAnalytics
import swiftPMImport.fr.berliat.hskwidget.crossPlatform.FIRCrashlytics
import fr.berliat.hskwidget.domain.WidgetProvider
import fr.berliat.hskwidget.domain.awaitWidgetSize
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey

@OptIn(ExperimentalForeignApi::class)
actual object ExpectedLogging {
    internal actual fun logCrashalytics(e: Throwable) {
        val userInfo = mutableMapOf<Any?, Any?>()
        userInfo[NSLocalizedDescriptionKey] = e.message ?: "Unknown error"
        userInfo["KotlinStackTrace"] = e.stackTraceToString()

        val error = NSError.errorWithDomain(
            domain = "KotlinException",
            code = 0,
            userInfo = userInfo
        )
        FIRCrashlytics.crashlytics().recordError(error)
    }

    internal actual fun logAnalyticsEvent(event: Logging.ANALYTICS_EVENTS,
                                          params: Map<String, String>) {
        HSKAppServices.appScope.launch(AppDispatchers.IO) {
            val enriched = Logging.withCommonParams(params)
                .toMutableMap<Any?, Any?>()

            FIRAnalytics.logEventWithName(event.name, enriched)
        }
    }

    internal actual fun logAnalyticsWidgetAction(event: Logging.ANALYTICS_EVENTS, widgetId: Int) {
        HSKAppServices.appScope.launch(AppDispatchers.IO) {
            val enriched = Logging.withWidgetActionParams(widgetId)
                .toMutableMap<Any?, Any?>()

            FIRAnalytics.logEventWithName(event.name, enriched)
        }
    }

    internal actual suspend fun getAnalyticsWidgetIds(): List<Int> {
        return try {
            FlashcardWidgetProvider().getWidgetIds()
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal actual suspend fun getAnalyticsWidgetSize(widgetId: Int): String {
        return try {
            WidgetProvider.delegate?.awaitWidgetSize(widgetId) ?: "UNKNOWN"
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }
}
