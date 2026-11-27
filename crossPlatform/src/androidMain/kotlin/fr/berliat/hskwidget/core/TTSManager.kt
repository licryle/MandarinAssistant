package fr.berliat.hskwidget.core

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.cancel
import fr.berliat.hskwidget.core.Logging.logAnalyticsError
import fr.berliat.hskwidget.dialog_tts_error
import fr.berliat.hskwidget.fix_it
import fr.berliat.hskwidget.speech_failure_toast_chinese_unsupported
import fr.berliat.hskwidget.speech_failure_toast_unknown

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * Android text-to-speech, kept separate from [ExpectedUtils].
 *
 * Chinese OEM ROMs often ship several TTS engines where the system default is
 * a silent accessibility stub. We probe the installed engines once, pick the
 * first one that accepts an installed Chinese voice, and reuse it afterwards.
 */
internal object TTSManager {
    private const val TAG = "Utils"
    private const val TTS_INIT_TIMEOUT_MS = 10_000L
    private const val TTS_SPEAK_TIMEOUT_MS = 4_000L

    // ColorOS ships an accessibility-only TTS stub that silently drops audio
    // for third-party apps. Try it only after every real engine.
    private const val PLUS_TTS_STUB_ENGINE = "com.oplus.ttsaccessibilityengine"

    private val context: Context
        get() = ExpectedUtils.context

    private val audioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* transient duck; nothing to do */ }
            .build()
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

    // A single failure can be transient (audio focus stolen, engine busy).
    // Keep the warm engine for one retry, then discard it so the next tap
    // re-probes for a different, working engine.
    @Volatile
    private var consecutiveFailures = 0

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

        // Setup stays after the await: onReady is suspend and calling it from
        // the binder-thread onInit callback risks re-entrancy on OEM engines.
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
            val res = withContext(Dispatchers.Main) { setVoice(voice) }
            if (res == TextToSpeech.SUCCESS) {
                Log.i(
                    TAG,
                    "TTS [$engineLabel] accepted voice ${voice.name} " +
                        "(locale=${voice.locale}, network=${voice.isNetworkConnectionRequired})"
                )
                return true
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

        val orderedEngines = (listOfNotNull(defaultEngine) + engineNames)
            .distinct()
            .sortedBy { it == PLUS_TTS_STUB_ENGINE }

        for (engine in orderedEngines) {
            val tts = initTts(engine) { candidate ->
                candidate.configureChinese(engine)
            }
            if (tts != null) {
                return@withLock tts.also {
                    cachedChineseTts = it
                    consecutiveFailures = 0
                }
            }
        }
        null
    }

    /**
     * Speaks [word] and verifies via [UtteranceProgressListener] that synthesis
     * actually completes rather than being silently dropped by an OEM stub engine.
     */
    private suspend fun TextToSpeech.speakAndVerify(word: String): Boolean {
        val utteranceId = "tts-${word.hashCode()}-${System.nanoTime()}"
        val finished = CompletableDeferred<Boolean>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var focusRequested = false

        fun releaseFocus() {
            if (!focusRequested) return
            focusRequested = false
            try {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            } catch (_: Exception) {}
        }

        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == utteranceId) Log.i(TAG, "TTS onStart($id)")
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) finished.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) finished.complete(false)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    Log.e(TAG, "TTS onError($id, errorCode=$errorCode)")
                    finished.complete(false)
                }
            }
        })

        return try {
            focusRequested = audioManager.requestAudioFocus(audioFocusRequest) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED

            val queueResult = withContext(Dispatchers.Main) {
                speak(word, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
            if (queueResult == TextToSpeech.ERROR) {
                false
            } else {
                withTimeoutOrNull(TTS_SPEAK_TIMEOUT_MS.milliseconds) { finished.await() } ?: false
            }
        } finally {
            // One cleanup path for success, error and timeout.
            releaseFocus()
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

    internal fun speak(word: String) {
        HSKAppServices.appScope.launch(AppDispatchers.IO) {
            val err = try {
                val engine = chineseTts()
                if (engine == null) {
                    SpeechError(
                        Res.string.speech_failure_toast_chinese_unsupported,
                        ttsLanguageRemedyIntent()
                    )
                } else {
                    Log.i(TAG, "Playing $word out loud.")
                    if (engine.speakAndVerify(word)) {
                        consecutiveFailures = 0
                        null
                    } else {
                        consecutiveFailures++
                        SpeechError(
                            Res.string.speech_failure_toast_chinese_unsupported,
                            ttsLanguageRemedyIntent()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed: $e")
                consecutiveFailures++
                SpeechError(Res.string.speech_failure_toast_unknown)
            }

            if (err != null) {
                // Keep the warm engine for a quick replay. Only after two
                // consecutive failures do we shut it down and force a re-probe.
                if (consecutiveFailures >= 2) {
                    cachedChineseTts?.let { tts ->
                        withContext(Dispatchers.Main) { tts.shutdown() }
                    }
                    cachedChineseTts = null
                    consecutiveFailures = 0
                }
                showSpeechError(err, word)
            }
        }
    }
}
