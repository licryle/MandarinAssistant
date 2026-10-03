package fr.berliat.hskwidget.core

import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hsktextviews.PinyinUtils.isHanzi
import fr.berliat.hskwidget.BuildKonfig
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.copied_to_clipboard
import fr.berliat.hskwidget.core.Logging.logAnalyticsError
import fr.berliat.hskwidget.core.Logging.logAnalyticsEvent
import fr.berliat.hskwidget.core.Utils.BackgroundRestrictionType
import fr.berliat.hskwidget.data.dao.AnkiDAO
import fr.berliat.hskwidget.data.repo.ChineseWordFrequencyRepo
import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.speech_failure_toast_muted

import io.github.vinceglb.filekit.PlatformFile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import org.jetbrains.compose.resources.getString
import kotlin.math.pow
import kotlin.math.round

object Utils {
    fun openLink(url: String) = ExpectedUtils.openLink(url)

    fun sendEmail(email: String, subject: String, body: String): Boolean =
        ExpectedUtils.sendEmail(email, subject, body)

    fun getAppVersion(): Int = BuildKonfig.VERSION_CODE
    fun getAppVersionName(): String = BuildKonfig.VERSION_NAME + " (" + BuildKonfig.VERSION_CODE + ")"

    fun getAnkiDAO(): AnkiDAO = ExpectedUtils.getAnkiDAO()

    fun getHSKSegmenter(): HSKTextSegmenter = ExpectedUtils.getHSKSegmenter()

    fun copyToClipBoard(s: String) {
        ExpectedUtils.copyToClipBoard(s)

        HSKAppServices.snackbar.show(SnackbarType.INFO, Res.string.copied_to_clipboard, listOf(s))

        logAnalyticsEvent(Logging.ANALYTICS_EVENTS.WIDGET_COPY_WORD)

        incrementConsultedWord(s)
    }

    fun isMuted() : Boolean = ExpectedUtils.isMuted()

    fun playWordInBackground(word: String) {
        if (isMuted()) {
            HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.speech_failure_toast_muted)

            CoroutineScope(AppDispatchers.IO).launch {
                logAnalyticsError(
                    "SPEECH",
                    getString(Res.string.speech_failure_toast_muted),
                    "muted WORD_LEN=${word.length}"
                )
            }
        } else {
            ExpectedUtils.playWordInBackground(word)
        }

        incrementConsultedWord(word)

        logAnalyticsEvent(Logging.ANALYTICS_EVENTS.WIDGET_PLAY_WORD)
    }

    fun openAppForSearchQuery(query: SearchQuery) = ExpectedUtils.openAppForSearchQuery(query)

    fun incrementConsultedWord(word: String) {
        HSKAppServices.appScope.launch(AppDispatchers.IO) {
            val db = HSKAppServices.database
            val frequencyWordsRepo = ChineseWordFrequencyRepo(
                db.chineseWordFrequencyDAO(),
                db.annotatedChineseWordDAO()
            )

            frequencyWordsRepo.incrementConsulted(word)
        }
    }

    fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun getAppDataPath(): PlatformFile = ExpectedUtils.getAppDataPath()
    suspend fun getAppDatabasePath(): PlatformFile = ExpectedUtils.getAppDatabasePath()
    fun attemptAddDesktopWidget(): Boolean = ExpectedUtils.attemptAddDesktopWidget()

    enum class BackgroundRestrictionType {
        BACKGROUND_RESTRICTED,
        BATTERY_OPTIMIZED,
        BACKGROUND_MAYBE_RESTRICTED,
        NO_RESTRICTION
    }

    fun isBackgroundRestricted(): BackgroundRestrictionType = ExpectedUtils.isBackgroundRestricted()
    fun openBatteryOptimizationSettings() = ExpectedUtils.openBatteryOptimizationSettings()

    fun getSystemVersion(): String = ExpectedUtils.getSystemVersion()
    fun getDeviceModel(): String = ExpectedUtils.getDeviceModel()
    fun getPlatformName(): String = ExpectedUtils.getPlatformName()
}

expect object ExpectedUtils {
    internal fun getAppDataPath(): PlatformFile
    internal suspend fun getAppDatabasePath(): PlatformFile

    internal fun openLink(url: String)
    internal fun sendEmail(email: String, subject: String = "", body: String = "") : Boolean

    internal fun getHSKSegmenter() : HSKTextSegmenter

    internal fun getAnkiDAO(): AnkiDAO

    internal fun copyToClipBoard(s: String)

    internal fun isMuted() : Boolean
    internal fun playWordInBackground(word: String)

    internal fun openAppForSearchQuery(query: SearchQuery)

    internal fun attemptAddDesktopWidget(): Boolean

    internal fun isBackgroundRestricted(): BackgroundRestrictionType
    internal fun openBatteryOptimizationSettings()

    internal fun getSystemVersion(): String
    internal fun getDeviceModel(): String
    internal fun getPlatformName(): String
}

fun String.capitalize() =
    this.lowercase().replaceFirstChar { it.uppercaseChar() }

fun String.toSafeFileName(): String {
    // Keep letters, digits, underscore, dash, and dot
    return this.replace(Regex("[^A-Za-z0-9\\._\\-]"), "_")
}

fun Long.fromKBToMB(decimals: Int = 2): String {
    val mb = this.toDouble() / (1024 * 1024)
    val factor = 10.0.pow(decimals)
    val rounded = round(mb * factor) / factor
    return rounded.toString()
}

fun Instant.YYMMDD(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = this.toLocalDateTime(timeZone)
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    return "${local.year}/$month/$day"
}

fun Instant.YYMMDDHHMMSS(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = this.toLocalDateTime(timeZone)
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')

    val hour = local.hour.toString().padStart(2, '0')
    val minutes = local.minute.toString().padStart(2, '0')
    val seconds = local.second.toString().padStart(2, '0')

    return "${local.year}/$month/$day $hour:$minutes:$seconds"
}

enum class ContainHanziResult {
    EMPTY,
    NONE,
    SOME,
    ALL
}

fun String.containsHanzi(): ContainHanziResult {
    if (this.isEmpty()) return ContainHanziResult.EMPTY

    var res = ContainHanziResult.NONE

    this.forEachIndexed { index, ch ->
        if (res == ContainHanziResult.NONE && ch.isHanzi()) {
            res = if (index == 0) ContainHanziResult.ALL else return ContainHanziResult.SOME
        }

        if (res == ContainHanziResult.ALL && !ch.isHanzi()) return ContainHanziResult.SOME
    }

    return res
}