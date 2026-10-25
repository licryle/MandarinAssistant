package fr.berliat.hskwidget.ui.components.filterBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.ocr_display_smallest_text
import fr.berliat.hskwidget.ui.theme.AppTypographies

enum class TextSizeMode {
    DICTIONARY,
    READER
}

/**
 * Stateful wrapper over [FilterBar] that automatically collects state flows from [AppPreferencesStore],
 * performs analytics logging, and executes preference mutations.
 */
@Composable
fun PrefsFilterBar(
    prefs: AppPreferencesStore = HSKAppServices.appPreferences,
    textSizeMode: TextSizeMode = TextSizeMode.DICTIONARY,
    showAnnotationFilter: Boolean = false,
    showSeparatorFilter: Boolean = false,
    showPinyinFilter: Boolean = false,
    modifier: Modifier = Modifier
) {
    val selectedLocale by prefs.dictionaryLocale.asStateFlow().collectAsState()
    val hasAnnotation by prefs.searchFilterHasAnnotation.asStateFlow().collectAsState()
    val separatorEnabled by prefs.readerSeparateWords.asStateFlow().collectAsState()
    val showPinyins by prefs.readerShowAllPinyins.asStateFlow().collectAsState()

    FilterBar(
        selectedLocale = selectedLocale,
        onLocaleSelected = { prefs.dictionaryLocale.value = it },
        onDecreaseTextSize = { updateTextSize(prefs, textSizeMode, -2f) },
        onIncreaseTextSize = { updateTextSize(prefs, textSizeMode, +2f) },
        modifier = modifier,
        hasAnnotation = hasAnnotation,
        onHasAnnotationToggle = if (showAnnotationFilter) {
            { enabled ->
                prefs.searchFilterHasAnnotation.value = enabled
                Logging.logAnalyticsEvent(
                    if (enabled) Logging.ANALYTICS_EVENTS.DICT_ANNOTATION_ON
                    else Logging.ANALYTICS_EVENTS.DICT_ANNOTATION_OFF
                )
            }
        } else null,
        separatorEnabled = separatorEnabled,
        onSeparatorToggle = if (showSeparatorFilter) {
            { enabled ->
                prefs.readerSeparateWords.value = enabled
                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.OCR_TOGGLE_SEPARATOR,
                    mapOf("ENABLED" to enabled.toString())
                )
            }
        } else null,
        showPinyins = showPinyins,
        onPinyinToggle = if (showPinyinFilter) {
            { enabled ->
                prefs.readerShowAllPinyins.value = enabled
                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.OCR_TOGGLE_PINYIN,
                    mapOf("ENABLED" to enabled.toString())
                )
            }
        } else null
    )
}

private fun updateTextSize(
    prefs: AppPreferencesStore,
    mode: TextSizeMode,
    increment: Float
) {
    when (mode) {
        TextSizeMode.DICTIONARY -> {
            val newSize = (prefs.dictionaryTextSize.value.value + increment).coerceAtLeast(
                AppTypographies.smallestHanziFontSize.value
            )
            prefs.dictionaryTextSize.value = newSize.sp
            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.DICT_TEXT_SIZE_CHANGE)
        }
        TextSizeMode.READER -> {
            val newSize = (prefs.readerTextSize.value.value + increment).coerceAtLeast(
                AppTypographies.smallestHanziFontSize.value
            )
            if (newSize == AppTypographies.smallestHanziFontSize.value) {
                HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.ocr_display_smallest_text)
            }
            prefs.readerTextSize.value = newSize.sp
            Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.OCR_TEXT_SIZE_CHANGE)
        }
    }
}
