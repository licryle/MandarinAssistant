package fr.berliat.hskwidget.ui.screens.about

import co.touchlab.kermit.Logger

import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.about_bug_report_email_template
import fr.berliat.hskwidget.about_email_noapp
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import org.jetbrains.compose.resources.getString

import fr.berliat.hskwidget.core.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AboutViewModel {
    val version = Utils.getAppVersionName()

    private val viewModelScope = CoroutineScope(SupervisorJob())

    data class Stats(
        val wordsCnt: Int = 0,
        val annotationCnt: Int = 0,
        val englishDefPct: Int = 0,
        val frenchDefPct: Int = 0,
        val hsk3DefPct: Int = 0,
        val collocationsPct: Int = 0,
        val examplesPct: Int = 0,
        val antonymsSynonymsPct: Int = 0,
        val typeUsagePct: Int = 0
    )

    // Internal mutable state
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow() // expose as read-only

    // Actions
    fun onClickWebsite() {
        Utils.openLink("https://github.com/licryle/Android-HSKFlashcardsWidget")
        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.ABOUT_GITHUB)
    }

    fun openEmail() {
        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.ABOUT_EMAIL)

        if (!Utils.sendEmail("cyrille.berliat+hsk@gmail.com", "About Mandarin Assistant App", "")) {
            HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.about_email_noapp)
        }
    }

    fun reportBug() {
        Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.ABOUT_BUG_REPORT)

        viewModelScope.launch(AppDispatchers.Main) {
            val logs = Logging.getLogFileContent()
            val body = getString(
                Res.string.about_bug_report_email_template,
                Utils.getAppVersionName(),
                Utils.getPlatformName(),
                Utils.getSystemVersion(),
                Utils.getDeviceModel(),
                logs
            )

            if (!Utils.sendEmail("cyrille.berliat+hsk@gmail.com", "Mandarin Assistant Bug Report", body)) {
                HSKAppServices.snackbar.show(SnackbarType.ERROR, Res.string.about_email_noapp)
            }
        }
    }

    fun fetchStats() {
        Logger.d(tag = TAG, messageString = "fetching stats")

        viewModelScope.launch(AppDispatchers.IO) {
            val db = HSKAppServices.database
            val wordsDao = db.chineseWordDAO()
            val definitionsDao = db.wordDefinitionDAO()
            val annotationsDao = db.chineseWordAnnotationDAO()

            val fieldsStats = wordsDao.getFieldsStats()
            val defStats = definitionsDao.getLanguageStats(
                enCode = Locale.ENGLISH.code,
                frCode = Locale.FRENCH.code,
                hsk3Code = Locale.CN_HSK3.code
            )
            val annotationsCnt = annotationsDao.getCount()

            val totalWords = fieldsStats.total
            val fetchedStats = if (totalWords > 0) {
                Stats(
                    wordsCnt = totalWords,
                    annotationCnt = annotationsCnt,
                    englishDefPct = (defStats.englishCnt * 100f / totalWords).roundToInt(),
                    frenchDefPct = (defStats.frenchCnt * 100f / totalWords).roundToInt(),
                    hsk3DefPct = (defStats.hsk3Cnt * 100f / totalWords).roundToInt(),
                    collocationsPct = (fieldsStats.collocationsCnt * 100f / totalWords).roundToInt(),
                    examplesPct = (fieldsStats.examplesCnt * 100f / totalWords).roundToInt(),
                    antonymsSynonymsPct = (fieldsStats.antonymsAndSynonymsCnt * 100f / totalWords).roundToInt(),
                    typeUsagePct = (fieldsStats.typeAndUsageCnt * 100f / totalWords).roundToInt()
                )
            } else {
                Stats(annotationCnt = annotationsCnt)
            }

            Logger.d(tag = TAG, messageString = "stats fetched")

            _stats.value = fetchedStats
        }
    }

    companion object {
        private const val TAG = "AboutFragment"
    }
}