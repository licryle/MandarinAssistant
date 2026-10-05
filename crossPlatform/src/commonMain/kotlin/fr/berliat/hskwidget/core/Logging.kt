package fr.berliat.hskwidget.core

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

private val logScope = CoroutineScope(SupervisorJob() + AppDispatchers.IO)
private val logMutex = Mutex()

object Logging {
    private var logFile: PlatformFile? = null

    class FileLogWriter(private val file: PlatformFile) : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            val path = file.toKotlinxIoPath()
            logScope.launch(AppDispatchers.IO) {
                logMutex.withLock {
                    try {
                        // SystemFileSystem.sink with append=true is supported in newer kotlinx-io
                        // If not, we might need a workaround, but let's try this first.
                        SystemFileSystem.sink(path, append = true).buffered().use { sink ->
                            sink.writeString("[$severity] $tag: $message\n")
                            throwable?.let {
                                sink.writeString(it.stackTraceToString() + "\n")
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore to avoid infinite loop
                    }
                }
            }
        }
    }

    fun setupFileLogging() {
        logScope.launch(AppDispatchers.IO) {
            logMutex.withLock {
                try {
                    val file = Utils.getAppDataPath() / "app_logs.txt"
                    logFile = file
                    val path = file.toKotlinxIoPath()

                    // Truncate the file on launch
                    SystemFileSystem.sink(path, append = false).buffered().use { sink ->
                        sink.writeString("--- App Launch ---\n")
                    }

                    Logger.addLogWriter(FileLogWriter(file))
                } catch (e: Exception) {
                    Logger.e(
                        tag = "Logging",
                        messageString = "Failed to setup file logging",
                        throwable = e
                    )
                }
            }
        }
    }

    suspend fun getLogFileContent(): String = withContext(AppDispatchers.IO) {
        val file = logFile ?: return@withContext ""
        val path = file.toKotlinxIoPath()
        return@withContext try {
            if (SystemFileSystem.exists(path)) {
                SystemFileSystem.source(path).buffered().use { it.readString() }
            } else {
                ""
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    val GlobalCoroutineExceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { context, exception ->
            Logger.e(
                tag = "CoroutineCrash",
                messageString = "Unhandled coroutine exception on context: $context",
                throwable = exception,
            )

            try {
                logCrashalytics(exception)
            } catch (e: Throwable) {
                Logger.w(tag = "Logging", messageString = "Failed to log exception to Crashlytics: ${e.message}")
            }
        }

    fun logAnalyticsScreenView(screenName: String) {
        logAnalyticsEvent(
            ANALYTICS_EVENTS.SCREEN_VIEW,
            mapOf("SCREEN_NAME" to screenName)
        )
    }

    fun logAnalyticsEvent(event: ANALYTICS_EVENTS, params: Map<String, String> = emptyMap()) =
        ExpectedLogging.logAnalyticsEvent(event, params)

    fun logAnalyticsError(module: String, error: String, details: String) {
        logAnalyticsEvent(
            ANALYTICS_EVENTS.ERROR,
            mapOf(
                "MODULE" to module,
                "ERROR_ID" to error,
                "DETAILS" to details
            )
        )
    }

    fun logAnalyticsWidgetAction(event: ANALYTICS_EVENTS, widgetId: Int) =
        ExpectedLogging.logAnalyticsWidgetAction(event, widgetId)

    fun logCrashalytics(e: Throwable) =
        ExpectedLogging.logCrashalytics(e)

    /**
     * Resolved dictionary language code for analytics.
     *
     * When the user preference is null (= "App language"), resolves to the
     * app language itself via [Locale.resolve], so analytics never sees
     * null/"app" but the concrete language in use (e.g. "en", "fr", "zh-CN-HSK03").
     */
    internal fun getDictionaryLanguageCode(): String {
        return try {
            val pref: Locale? = try {
                HSKAppServices.appPreferences.dictionaryLocale.value
            } catch (_: Exception) {
                null
            }
            Locale.resolve(pref).code
        } catch (_: Exception) {
            try {
                LocaleManager.getCurrentLocale()
            } catch (_: Exception) {
                "unknown"
            }
        }
    }

    /**
     * Enriches [params] with every common analytics param. All widget list
     * access happens in here (via [ExpectedLogging.getAnalyticsWidgetIds]), so
     * call sites never touch widget logic.
     * - `DICT_LANGUAGE`: resolved dictionary language ([getDictionaryLanguageCode]).
     *   When the preference is null (= "App language"), resolves to the app
     *   language itself, so analytics never sees null/"app" but the concrete
     *   language in use (e.g. "en", "fr", "zh-CN-HSK03").
     * - `UI_LANGUAGE`: current app/UI locale (e.g. "en", "fr", "zh-Hans").
     * - `WIDGET_TOTAL_NUMBER` / `MAX_WIDGET_ID`: derived from the current widget list.
     *
     * Caller-provided values win. Never throws.
     */
    internal suspend fun withCommonParams(params: Map<String, String>): Map<String, String> {
        val out = params.toMutableMap()
        addDictionaryLanguage(out)
        addUiLanguage(out)
        addWidgetCollection(out, safeFetchWidgetIds())
        return out
    }

    /**
     * Full param map for a per-widget action: `WIDGET_NUMBER` / `WIDGET_SIZE`
     * plus everything [withCommonParams] adds. Single widget-list fetch shared
     * by all keys, so call sites pass only the [widgetId].
     */
    internal suspend fun withWidgetActionParams(widgetId: Int): Map<String, String> {
        val widgets = safeFetchWidgetIds()
        val out = mutableMapOf(
            "WIDGET_NUMBER" to widgets.indexOf(widgetId).toString(),
            "WIDGET_SIZE" to safeFetchWidgetSize(widgetId)
        )
        addDictionaryLanguage(out)
        addUiLanguage(out)
        addWidgetCollection(out, widgets)
        return out
    }

    internal fun getUiLanguageCode(): String {
        return try {
            LocaleManager.getCurrentLocale()
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun addUiLanguage(out: MutableMap<String, String>) {
        if (out.containsKey("UI_LANGUAGE")) return
        try {
            out["UI_LANGUAGE"] = getUiLanguageCode()
        } catch (_: Exception) {
            // Keep other params even if language resolution fails.
        }
    }

    private fun addDictionaryLanguage(out: MutableMap<String, String>) {
        if (out.containsKey("DICT_LANGUAGE")) return
        try {
            out["DICT_LANGUAGE"] = getDictionaryLanguageCode()
        } catch (_: Exception) {
            // Keep other params even if language resolution fails.
        }
    }

    private fun addWidgetCollection(out: MutableMap<String, String>, widgets: List<Int>) {
        if (!out.containsKey("WIDGET_TOTAL_NUMBER")) {
            out["WIDGET_TOTAL_NUMBER"] = widgets.size.toString()
        }
        if (!out.containsKey("MAX_WIDGET_ID")) {
            out["MAX_WIDGET_ID"] = widgets.lastOrNull()?.toString() ?: "0"
        }
    }

    private suspend fun safeFetchWidgetIds(): List<Int> {
        return try {
            ExpectedLogging.getAnalyticsWidgetIds()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun safeFetchWidgetSize(widgetId: Int): String {
        return try {
            ExpectedLogging.getAnalyticsWidgetSize(widgetId)
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }

    enum class ANALYTICS_EVENTS {
        SCREEN_VIEW,
        AUTO_WORD_CHANGE,
        ERROR, // Use logAnalyticsError for details
        WIDGET_PLAY_WORD,
        WIDGET_MANUAL_WORD_CHANGE,
        WIDGET_RECONFIGURE,
        WIDGET_CONFIG_VIEW,
        WIGDET_RESIZE,
        WIGDET_ADD,
        WIDGET_EXPAND,
        WIDGET_COLLAPSE,
        WIGDET_REMOVE,
        WIDGET_OPEN_DICTIONARY,
        WIDGET_COPY_WORD,
        CONFIG_BACKUP_ON,
        CONFIG_BACKUP_OFF,
        CONFIG_BACKUP_RESTORE,
        CONFIG_BACKUPCLOUD_ON, // Reserved for future use
        CONFIG_BACKUPCLOUD_OFF, // Reserved for future use
        CONFIG_BACKUPCLOUD_RESTORE,
        CONFIG_BACKUPCLOUD_BACKUP,
        CONFIG_ANKI_SYNC_ON,
        CONFIG_ANKI_SYNC_OFF,
        ANNOTATION_SAVE,
        ANNOTATION_DELETE,
        LIST_CREATE,
        LIST_DELETE,
        LIST_MODIFY_WORD, // Deprecated: use LIST_WORD_ADD / LIST_WORD_REMOVE
        LIST_WORD_ADD,
        LIST_WORD_REMOVE,
        LIST_RENAME,
        DICT_CHANGE_LANG,
        DICT_ANNOTATION_ON,
        DICT_ANNOTATION_OFF,
        DICT_TEXT_SIZE_CHANGE,
        DICT_LOAD_MORE,
        DICT_EMPTY_RESULT,
        OCR_TEXT_SIZE_CHANGE,
        OCR_TOGGLE_PINYIN,
        OCR_TOGGLE_SEPARATOR,
        OCR_RECOGNIZE_SUCCESS,
        OCR_RECOGNIZE_FAIL,
        DICT_SEARCH,
        OCR_CAPTURE,
        OCR_WORD_NOTFOUND,
        OCR_WORD_FOUND,
        ANNOTATION_VIEW,
        CONFIG_LOCALE_CHANGE,
        BACKUPDISK_FOLDER_SELECT_SUCCESS,
        BACKUPDISK_FOLDER_SELECT_FAIL,
        BACKUPCLOUD_LOGIN,
        BACKUPCLOUD_BACKUP_SUCCESS,
        BACKUPCLOUD_BACKUP_FAIL,
        BACKUPCLOUD_RESTORE_SUCCESS,
        BACKUPCLOUD_RESTORE_FAIL,
        BACKUPCLOUD_CANCEL,
        ANKI_SYNC_START,
        ANKI_SYNC_SUCCESS,
        ANKI_SYNC_CANCEL,
        SUPPORT_FETCH_FAIL,
        WIDGET_ADD_ATTEMPT,
        WIDGET_BATTERY_FIX_CLICK,
        ABOUT_GITHUB,
        ABOUT_EMAIL,
        ABOUT_BUG_REPORT,
        PURCHASE_CLICK,
        PURCHASE_FAILED,
        PURCHASE_SUCCESS
    }
}

expect object ExpectedLogging {
    internal fun logCrashalytics(e: Throwable)
    internal fun logAnalyticsEvent(event: Logging.ANALYTICS_EVENTS,
                          params: Map<String, String> = mapOf())
    internal fun logAnalyticsWidgetAction(event: Logging.ANALYTICS_EVENTS, widgetId: Int)

    internal suspend fun getAnalyticsWidgetIds(): List<Int>
    /** Platform widget size string for analytics ("WxH" or "UNKNOWN"). */
    internal suspend fun getAnalyticsWidgetSize(widgetId: Int): String
}
