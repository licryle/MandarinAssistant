package fr.berliat.hskwidget.ui.components.filterBar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.ui.horizontalScrollbar

/**
 * Stateless filter bar component containing shared controls:
 * - Language filter chip
 * - Text size adjustment chip
 * - Optional annotation filter chip (shown when [onHasAnnotationToggle] is non-null)
 * - Optional word separator chip (shown when [onSeparatorToggle] is non-null)
 * - Optional pinyin chip (shown when [onPinyinToggle] is non-null)
 */
@Composable
fun FilterBar(
    selectedLocale: Locale?,
    onLocaleSelected: (Locale?) -> Unit,
    onDecreaseTextSize: () -> Unit,
    onIncreaseTextSize: () -> Unit,
    modifier: Modifier = Modifier,
    hasAnnotation: Boolean = false,
    onHasAnnotationToggle: ((Boolean) -> Unit)? = null,
    separatorEnabled: Boolean = false,
    onSeparatorToggle: ((Boolean) -> Unit)? = null,
    showPinyins: Boolean = false,
    onPinyinToggle: ((Boolean) -> Unit)? = null
) {
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
            onLocaleSelected = onLocaleSelected
        )

        TextSizeChip(
            onDecrease = onDecreaseTextSize,
            onIncrease = onIncreaseTextSize,
            modifier = Modifier.padding(end = 8.dp)
        )

        if (onHasAnnotationToggle != null) {
            AnnotationFilterChip(
                selected = hasAnnotation,
                onToggle = onHasAnnotationToggle
            )
        }

        if (onSeparatorToggle != null) {
            SeparatorChip(
                selected = separatorEnabled,
                onToggle = onSeparatorToggle
            )
        }

        if (onPinyinToggle != null) {
            PinyinChip(
                selected = showPinyins,
                onToggle = onPinyinToggle
            )
        }
    }
}
