package fr.berliat.hskwidget.ui.components.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.ocr_display_smallest_text
import fr.berliat.hskwidget.ui.components.LanguageFilterChip
import fr.berliat.hskwidget.ui.components.TextSizeChip
import fr.berliat.hskwidget.ui.horizontalScrollbar
import fr.berliat.hskwidget.ui.theme.AppTypographies

/**
 * Reader filter bar shared by OCR and Dictionary reader mode:
 * language + text size + space words + show pinyins.
 *
 * Reads and writes the reader prefs directly — it is reader-only UI bound
 * to reader-only prefs, so no ViewModel pass-through is needed.
 */
@Composable
fun ReaderFilterBar(
    prefs: AppPreferencesStore = HSKAppServices.appPreferences,
    modifier: Modifier = Modifier
) {
    val separatorEnabled by prefs.readerSeparateWords.asStateFlow().collectAsState()
    val showPinyins by prefs.readerShowAllPinyins.asStateFlow().collectAsState()
    val selectedLocale by prefs.dictionaryLocale.asStateFlow().collectAsState()

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScrollbar(scrollState)
            .horizontalScroll(scrollState)
            .padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageFilterChip(
            selectedLocale = selectedLocale,
            supportedLocales = Locale.entries.filter { it.flag != null },
            includeNullLocale = true,
            onLocaleSelected = { prefs.dictionaryLocale.value = it }
        )

        TextSizeChip(
            onDecrease = { updateReaderTextSize(-2f) },
            onIncrease = { updateReaderTextSize(+2f) },
            modifier = Modifier.padding(end = 8.dp)
        )

        SeparatorChip(
            selected = separatorEnabled,
            onToggle = {
                HSKAppServices.appPreferences.readerSeparateWords.value = it
                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.OCR_TOGGLE_SEPARATOR,
                    mapOf("ENABLED" to it.toString())
                )
            }
        )

        PinyinChip(
            selected = showPinyins,
            onToggle = {
                HSKAppServices.appPreferences.readerShowAllPinyins.value = it
                Logging.logAnalyticsEvent(
                    Logging.ANALYTICS_EVENTS.OCR_TOGGLE_PINYIN,
                    mapOf("ENABLED" to it.toString())
                )
            }
        )
    }
}

private fun updateReaderTextSize(increment: Float) {
    val prefs = HSKAppServices.appPreferences
    val newSize = (prefs.readerTextSize.value.value + increment).coerceAtLeast(
        AppTypographies.smallestHanziFontSize.value)

    if (newSize == AppTypographies.smallestHanziFontSize.value) {
        HSKAppServices.snackbar.show(SnackbarType.WARNING, Res.string.ocr_display_smallest_text)
    }

    prefs.readerTextSize.value = newSize.sp

    Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.OCR_TEXT_SIZE_CHANGE)
}
