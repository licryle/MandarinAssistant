package fr.berliat.hskwidget.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log

import co.touchlab.kermit.Logger

import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hskwidget.data.dao.AnkiDAO
import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.Utils.BackgroundRestrictionType
import fr.berliat.hskwidget.cancel
import fr.berliat.hskwidget.core.Logging.logAnalyticsError
import fr.berliat.hskwidget.dialog_tts_error
import fr.berliat.hskwidget.domain.WidgetController
import fr.berliat.hskwidget.fix_it
import fr.berliat.hskwidget.speech_failure_toast_chinese_unsupported
import fr.berliat.hskwidget.speech_failure_toast_init
import fr.berliat.hskwidget.speech_failure_toast_unknown
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.filesDir

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

import java.util.Locale
import androidx.core.net.toUri

fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> throw Exception("No activity attached to context")
}

fun Context.getActivity(): Activity? = try {
    findActivity()
} catch (_: Exception) {
    null
}

@SuppressLint("StaticFieldLeak")
actual object ExpectedUtils {
    private var _context: Context? = null
    val context
        get() = _context!!

    // Initialize once, first writer wins (app: applicationContext from
    // MainActivity; widget process: receiver context). Always keep the
    // application context so a destroyed Activity is never retained.
    fun init(context: Context) {
        if (_context == null) {
            _context = context.applicationContext
        }
    }

    internal actual fun getAppDataPath(): PlatformFile = FileKit.filesDir
    internal actual suspend fun getAppDatabasePath(): PlatformFile = withContext(AppDispatchers.IO) {
        return@withContext FileKit.databasesDir
    }

    internal actual fun openLink(url: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            url.toUri()
        ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    internal actual fun sendEmail(email: String, subject: String, body: String) : Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri() // use only "mailto:", don't put address here
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            try {
                context.startActivity(Intent.createChooser(intent, "Send email with..."))
            } catch (_: Exception) {
                return false
            }
        }

        return true
    }

    internal actual fun getAnkiDAO(): AnkiDAO {
        return AnkiDAO(context)
    }

    internal actual fun getHSKSegmenter() : HSKTextSegmenter {
        return JiebaHSKTextSegmenter()
    }

    internal actual fun copyToClipBoard(s: String) {
        // https://stackoverflow.com/a/28780585/3059536
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", s)
        clipboard.setPrimaryClip(clip)
    }

    internal actual fun isMuted() : Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val musicVolume: Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return musicVolume == 0
    }

    private data class SpeechError(
        val errStringId: StringResource,
        val errRemedyIntent: String? = null
    )

    private data class TtsInitResult(
        val tts: TextToSpeech? = null,
        val error: SpeechError? = null
    )

    // Engines may report LANG_NOT_SUPPORTED for one Chinese locale while
    // supporting another (zh vs zh-CN vs cmn, simplified vs traditional).
    private val chineseLocales: List<Locale> = listOf(
        Locale.SIMPLIFIED_CHINESE,
        Locale.CHINA,
        Locale.CHINESE,
        Locale.TRADITIONAL_CHINESE,
        Locale.TAIWAN,
        Locale.forLanguageTag("zh-Hans-CN"),
        Locale.forLanguageTag("zh-Hans"),
        Locale.forLanguageTag("zh-Hant"),
        Locale.forLanguageTag("cmn-CN"),
        Locale.forLanguageTag("cmn")
    ).distinctBy { it.toLanguageTag() }

    private fun canResolveActivity(intent: Intent): Boolean = try {
        intent.resolveActivity(context.packageManager) != null ||
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun startActivitySafely(intent: Intent): Boolean {
        if (!canResolveActivity(intent)) return false

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ttsLanguageRemedyIntent(): String {
        val installDataIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        return if (canResolveActivity(installDataIntent)) {
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
        } else {
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        }
    }

    private fun TextToSpeech.trySetChineseLanguage(): Boolean {
        for (locale in chineseLocales) {
            val result = setLanguage(locale)
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                Log.i(TAG, "TTS language set to $locale (result=$result)")
                return true
            }
        }

        Log.e(TAG, "No Chinese locale supported by TTS engine.")
        return false
    }

    private suspend fun initTts(engine: String?): Pair<TextToSpeech?, Int> {
        val initStatus = CompletableDeferred<Int>()
        val tts = try {
            if (engine == null) {
                TextToSpeech(context) { status -> initStatus.complete(status) }
            } else {
                TextToSpeech(context, { status -> initStatus.complete(status) }, engine)
            }
        } catch (_: Exception) {
            return null to TextToSpeech.ERROR
        }

        val status = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) { initStatus.await() }
            ?: TextToSpeech.ERROR
        return tts to status
    }

    private suspend fun createTtsForChinese(): TtsInitResult {
        var anyEngineInitialized = false

        // Try the user's default engine first.
        val (defaultTts, defaultStatus) = initTts(null)
        var engines: List<String> = emptyList()
        var defaultEngine: String? = null

        if (defaultTts != null) {
            if (defaultStatus == TextToSpeech.SUCCESS) {
                anyEngineInitialized = true
                if (defaultTts.trySetChineseLanguage()) {
                    return TtsInitResult(tts = defaultTts)
                }
            }

            // Keep the engine list before shutting the instance down so we can
            // fall back to another engine that may have the Chinese voice.
            engines = try {
                defaultTts.engines.map { it.name }
            } catch (_: Exception) {
                emptyList()
            }
            defaultEngine = try {
                defaultTts.defaultEngine
            } catch (_: Exception) {
                null
            }
            defaultTts.shutdown()
        }

        val fallbackEngines = engines
            .filter { it != defaultEngine }
            .distinct()
            .sortedByDescending { it == GOOGLE_TTS_ENGINE }
            .take(MAX_FALLBACK_ENGINES)

        for (engine in fallbackEngines) {
            val (tts, status) = initTts(engine)
            if (tts == null) continue

            if (status == TextToSpeech.SUCCESS) {
                anyEngineInitialized = true
                if (tts.trySetChineseLanguage()) {
                    return TtsInitResult(tts = tts)
                }
            }

            tts.shutdown()
        }

        return if (anyEngineInitialized) {
            TtsInitResult(
                error = SpeechError(
                    Res.string.speech_failure_toast_chinese_unsupported,
                    ttsLanguageRemedyIntent()
                )
            )
        } else {
            TtsInitResult(
                error = SpeechError(
                    Res.string.speech_failure_toast_init,
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )
        }
    }

    private suspend fun showSpeechError(err: SpeechError, word: String) {
        val errString = getString(err.errStringId)
        val titleString = getString(Res.string.dialog_tts_error)
        val yesButton = getString(Res.string.fix_it)
        val noButton = getString(Res.string.cancel)

        withContext(Dispatchers.Main) {
            val activity = context.getActivity()
            val remedyIntent = err.errRemedyIntent
            val remedyAction: (() -> Unit)? = remedyIntent?.let { action ->
                {
                    startActivitySafely(Intent(action))
                    Unit
                }
            }

            if (remedyIntent == null ||
                activity == null || activity.isFinishing || activity.isDestroyed) {
                HSKAppServices.snackbar.show(
                    type = SnackbarType.ERROR,
                    messageRes = err.errStringId,
                    actionLabelRes = if (remedyAction != null) Res.string.fix_it else null,
                    onAction = remedyAction
                )
            } else {
                AlertDialog.Builder(activity)
                    .setTitle(titleString)
                    .setMessage(errString)
                    .setPositiveButton(yesButton) { _, _ ->
                        if (!startActivitySafely(Intent(remedyIntent))) {
                            Log.w(TAG, "No activity found to handle $remedyIntent")
                        }
                    }
                    .setNegativeButton(noButton, null)
                    .show()
            }
        }

        logAnalyticsError(
            "SPEECH",
            getString(err.errStringId),
            "hasRemedy=${err.errRemedyIntent != null} WORD_LEN=${word.length}"
        )
    }

    internal actual fun playWordInBackground(word: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var tts: TextToSpeech? = null

            val err = try {
                val result = createTtsForChinese()
                val workingTts = result.tts
                tts = workingTts

                if (workingTts == null) {
                    result.error
                } else {
                    Log.i(TAG, "Playing $word out loud.")
                    val speakResult = workingTts.speak(
                        word,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "tts-${word.hashCode()}"
                    )
                    if (speakResult == TextToSpeech.ERROR) {
                        SpeechError(Res.string.speech_failure_toast_unknown)
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                SpeechError(Res.string.speech_failure_toast_unknown)
            }

            if (err != null) {
                tts?.shutdown()
                showSpeechError(err, word)
            }
        }
    }

    internal actual fun openAppForSearchQuery(query: SearchQuery) {
        val context = context
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(INTENT_SEARCH_WORD, query.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            // fallback: app has no launch intent?
            Logger.e(tag = TAG, messageString = "No launch intent found for ${context.packageName}")
        }
    }

    internal actual fun attemptAddDesktopWidget(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

        WidgetController.requestAddDesktopWidget(context, appWidgetManager)
        return true
    }

    internal actual fun isBackgroundRestricted(): BackgroundRestrictionType {
        val aggressiveManufacturers = setOf(
            "honor",
            "huawei",
            "xiaomi",
            "oppo",
            "vivo",
            "realme",
            "oneplus"
        )

        return if(Build.MANUFACTURER.lowercase() in aggressiveManufacturers) {
            BackgroundRestrictionType.BACKGROUND_MAYBE_RESTRICTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (activityManager.isBackgroundRestricted)
                BackgroundRestrictionType.BACKGROUND_RESTRICTED
            else
                BackgroundRestrictionType.BATTERY_OPTIMIZED
        } else {
            BackgroundRestrictionType.BATTERY_OPTIMIZED
        }
    }

    internal actual fun openBatteryOptimizationSettings() {
        val intents = listOf(
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                data = Uri.fromParts("package", context.packageName, null)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )

        val pm = context.packageManager

        for (intent in intents) {
            if (intent.resolveActivity(pm) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }
    }

    internal actual fun getSystemVersion(): String =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, Build ${Build.DISPLAY}, Patch ${Build.VERSION.SECURITY_PATCH})"
    internal actual fun getDeviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})"
    internal actual fun getPlatformName(): String = "Android"

    private const val TAG = "Utils"
    private const val GOOGLE_TTS_ENGINE = "com.google.android.tts"
    private const val TTS_INIT_TIMEOUT_MS = 5_000L
    private const val MAX_FALLBACK_ENGINES = 3
    const val INTENT_SEARCH_WORD = "INTENT_SEARCH_WORD"
}
