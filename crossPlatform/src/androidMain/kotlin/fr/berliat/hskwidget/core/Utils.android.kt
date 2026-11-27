package fr.berliat.hskwidget.core

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
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

import co.touchlab.kermit.Logger

import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hskwidget.data.dao.AnkiDAO
import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.core.Utils.BackgroundRestrictionType
import fr.berliat.hskwidget.domain.WidgetController
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.filesDir

import kotlinx.coroutines.withContext


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

    internal actual fun playWordInBackground(word: String) = TTSManager.speak(word)

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
    const val INTENT_SEARCH_WORD = "INTENT_SEARCH_WORD"
}
