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
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

import java.util.Locale
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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

    // OEM engines often label Chinese "chn"/"chs"/"cht" instead of "zh".
    private val chineseLanguageCodes = setOf(
        "zh", "zho", "chi", "cmn", "yue", "chn", "chs", "cht"
    )

    private fun Locale.isChinese(): Boolean =
        language.lowercase() in chineseLanguageCodes ||
            toLanguageTag().lowercase().let { tag ->
                chineseLanguageCodes.any { tag.startsWith(it) }
            }

    private fun Voice.isChinese(): Boolean = locale.isChinese() || name.let {
        it.contains("中文") || it.contains("汉语") || it.contains("漢語") ||
            it.contains("普通话") || it.contains("普通話") ||
            it.contains("国语") || it.contains("國語") ||
            it.contains("Chinese", ignoreCase = true)
    }

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
        val candidates = listOf(
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA,
            "com.android.settings.TTS_SETTINGS",
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        )
        return candidates.firstOrNull { canResolveActivity(Intent(it)) }
            ?: Settings.ACTION_SETTINGS
    }
    @Volatile
    private var cachedChineseTts: TextToSpeech? = null

    private val ttsMutex = Mutex()

    private suspend fun initTts(
        engine: String?,
        onReady: suspend (TextToSpeech) -> Boolean = { true }
    ): TextToSpeech? {
        val readySignal = CompletableDeferred<Int>()

        val tts = withContext(Dispatchers.Main) {
            try {
                val listener = TextToSpeech.OnInitListener { status ->
                    readySignal.complete(status)
                }
                if (engine == null) {
                    TextToSpeech(context, listener)
                } else {
                    TextToSpeech(context, listener, engine)
                }
            } catch (_: Exception) {
                null
            }
        } ?: return null

        val status = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS.milliseconds) {
            readySignal.await()
        }

        if (status != TextToSpeech.SUCCESS) {
            withContext(Dispatchers.Main) { tts.shutdown() }
            return null
        }

        val accepted = try {
            onReady(tts)
        } catch (_: Exception) {
            false
        }

        if (!accepted) {
            withContext(Dispatchers.Main) { tts.shutdown() }
            return null
        }

        return tts
    }

    private fun TextToSpeech.installedChineseVoices(): List<Voice> = try {
        voices.orEmpty()
            .filter { it.isChinese() }
            .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            // Offline voices first: faster and work without background data
            .sortedBy { it.isNetworkConnectionRequired }
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun TextToSpeech.configureChinese(engineLabel: String): Boolean {
        // 1. Try installed Chinese voices via setVoice() directly (do NOT call
        //    setLanguage(voice.locale) first—script tags like zh-Hans-CN cause -2)
        for (voice in installedChineseVoices()) {
            repeat(3) { attempt ->
                val res = withContext(Dispatchers.Main) { setVoice(voice) }
                if (res == TextToSpeech.SUCCESS) {
                    Log.i(
                        TAG,
                        "TTS [$engineLabel] accepted voice ${voice.name} " +
                                "(locale=${voice.locale}, network=${voice.isNetworkConnectionRequired})"
                    )
                    return true
                }
                delay(50L * (attempt + 1))
            }
        }

        // 2. Try standard Chinese Locales via setLanguage()
        val standardLocales = listOf(
            Locale.SIMPLIFIED_CHINESE, // zh_CN
            Locale.CHINESE,            // zh
            Locale.TRADITIONAL_CHINESE // zh_TW
        )
        for (loc in standardLocales) {
            val langStatus = withContext(Dispatchers.Main) { setLanguage(loc) }
            if (langStatus != TextToSpeech.LANG_MISSING_DATA &&
                langStatus != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.i(TAG, "TTS [$engineLabel] accepted setLanguage($loc) -> $langStatus")
                return true
            }
        }

        // 3. Last resort for OEM wrapper engines (e.g. ColorOS) where isLanguageAvailable >= 0
        //    even though setLanguage() and setVoice() return error codes
        val availableLocale = standardLocales.firstOrNull { loc ->
            val avail = try {
                isLanguageAvailable(loc)
            } catch (_: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            avail >= TextToSpeech.LANG_AVAILABLE
        }
        if (availableLocale != null) {
            Log.i(TAG, "TTS [$engineLabel] fallback via isLanguageAvailable($availableLocale)")
            return true
        }

        return false
    }

    private suspend fun chineseTts(): TextToSpeech? = ttsMutex.withLock {
        cachedChineseTts?.let { return@withLock it }

        var engineNames: List<String> = emptyList()
        var defaultEngine = try {
            Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        } catch (_: Exception) {
            null
        }

        // Enumerate engines via a probe instance
        val probe = initTts(null)
        if (probe != null) {
            defaultEngine = try { probe.defaultEngine } catch (_: Exception) { defaultEngine }
            engineNames = try { probe.engines.map { it.name } } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) { probe.shutdown() }
        }
        if (engineNames.isEmpty()) {
            engineNames = try {
                context.packageManager.queryIntentServices(
                    Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                    PackageManager.MATCH_DEFAULT_ONLY
                ).mapNotNull { it.serviceInfo?.packageName }
            } catch (_: Exception) {
                emptyList()
            }
        }

        // Put ColorOS's accessibility-only stub last because it silently drops audio
        // for third-party apps when another real TTS engine is installed
        val orderedEngines = (listOfNotNull(defaultEngine) + engineNames)
            .distinct()
            .sortedBy { it == "com.oplus.ttsaccessibilityengine" }


        for (engine in orderedEngines) {
            val tts = initTts(engine) { candidate ->
                candidate.configureChinese(engine)
            }
            if (tts != null) {
                return@withLock tts.also { cachedChineseTts = it }
            }
        }
        null
    }

    /**
     * Speaks [word] and verifies via [android.speech.tts.UtteranceProgressListener] that
     * synthesis actually starts rather than being silently dropped by an OEM stub engine.
     */
    private suspend fun TextToSpeech.speakAndVerify(word: String): Boolean {
        val utteranceId = "tts-${word.hashCode()}-${System.nanoTime()}"
        val finished = CompletableDeferred<Boolean>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        @Suppress("DEPRECATION")
        fun releaseFocus() {
            try { audioManager.abandonAudioFocus(null) } catch (_: Exception) {}
        }

        setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    releaseFocus()
                    finished.complete(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    releaseFocus()
                    finished.complete(false)
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    releaseFocus()
                    finished.complete(false)
                }
            }
        })

        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )

        val queueResult = withContext(Dispatchers.Main) {
            speak(word, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
        if (queueResult == TextToSpeech.ERROR) {
            releaseFocus()
            return false
        }

        return (withTimeoutOrNull(4000.milliseconds) { finished.await() } ?: false).also { ok ->
            if (!ok) releaseFocus()
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
                { startActivitySafely(Intent(action)) }
            }

            if (remedyIntent == null ||
                activity == null || activity.isFinishing || activity.isDestroyed
            ) {
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
            val err = try {
                val engine = chineseTts()
                if (engine == null) {
                    SpeechError(
                        Res.string.speech_failure_toast_chinese_unsupported,
                        ttsLanguageRemedyIntent()
                    )
                } else {
                    Log.i(TAG, "Playing $word out loud.")
                    if (!engine.speakAndVerify(word)) {
                        withContext(Dispatchers.Main) { engine.shutdown() }
                        cachedChineseTts = null
                        SpeechError(
                            Res.string.speech_failure_toast_chinese_unsupported,
                            ttsLanguageRemedyIntent()
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed: $e")
                SpeechError(Res.string.speech_failure_toast_unknown)
            }
            if (err != null) {
                cachedChineseTts?.let { tts ->
                    withContext(Dispatchers.Main) { tts.shutdown() }
                }
                cachedChineseTts = null
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
    private const val TTS_INIT_TIMEOUT_MS = 10_000L
    const val INTENT_SEARCH_WORD = "INTENT_SEARCH_WORD"
}
