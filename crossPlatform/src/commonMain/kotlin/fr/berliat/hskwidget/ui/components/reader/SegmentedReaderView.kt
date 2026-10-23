package fr.berliat.hskwidget.ui.components.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hsktextviews.views.HSKTextView
import fr.berliat.hsktextviews.views.ShowPinyins
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.ocr_display_loading
import fr.berliat.hskwidget.ocr_display_text_segmentation_failed
import fr.berliat.hskwidget.reader_word_spacing_processing
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.ui.dismissKeyboardOnClick
import fr.berliat.hskwidget.ui.theme.AppTypographies
import org.jetbrains.compose.resources.StringResource

private const val TAG = "SegmentedReaderView"

const val READER_WORD_SEPARATOR = "·"

/**
 * Shared segmented-text reader. Thin wrapper around HSKTextView with the
 * OCR styling. The caller owns layout (typically Box weight(1f)); this
 * composable never scrolls itself — HSKTextView scrolls internally.
 *
 * @param textSize single shared size (dictionaryTextSize per locked spec).
 */
@Composable
fun SegmentedReaderView(
    text: String,
    textSize: Float,
    showPinyins: Boolean,
    separatorEnabled: Boolean,
    clickedWords: Map<String, String>,
    onWordClick: (String) -> Unit,
    onAppeared: (List<Pair<String, String>>) -> Unit = {},
    onFailure: (StringResource) -> Unit = {},
    segmenter: HSKTextSegmenter = HSKAppServices.HSKSegmenter,
    wordSeparator: String = READER_WORD_SEPARATOR,
    modifier: Modifier = Modifier
) {
    // Word spacing needs the segmenter: warn while it is still preloading, so the
    // user knows the "Space words" toggle isn't applied yet. Uses the helper that
    // already exists in the app instead of any new focus/keyboard logic.
    LaunchedEffect(segmenter, separatorEnabled) {
        if (separatorEnabled && !segmenter.isReady()) {
            HSKAppServices.snackbar.show(
                SnackbarType.INFO,
                Res.string.reader_word_spacing_processing,
                duration = SnackbarDuration.Long
            )
        }
    }

    Box(modifier = modifier.dismissKeyboardOnClick()) {
        HSKTextView(
            text = text,
            segmenter = segmenter,
            hanziStyle = AppTypographies.hanzi.copy(fontSize = textSize.sp),
            pinyinStyle = AppTypographies.pinyin,
            clickedHanziStyle = AppTypographies.hanzi.copy(fontSize = textSize.sp),
            clickedPinyinStyle = AppTypographies.pinyin,
            clickedBackgroundColor = MaterialTheme.colorScheme.inverseOnSurface,
            loadingComposable = { LoadingView(loadingText = Res.string.ocr_display_loading) },
            emptyComposable = { ReaderEmpty() },
            onWordClick = onWordClick,
            showPinyins = if (showPinyins) ShowPinyins.ALL else ShowPinyins.CLICKED,
            endSeparator = if (separatorEnabled) wordSeparator else "",
            clickedWords = clickedWords,
            onTextAnalysisSuccess = onAppeared,
            onTextAnalysisFailure = { e ->
                Logger.e(tag = TAG, messageString = "Text analysis failure: ${e.message}", throwable = e)
                onFailure(Res.string.ocr_display_text_segmentation_failed)
            }
        )
    }
}
