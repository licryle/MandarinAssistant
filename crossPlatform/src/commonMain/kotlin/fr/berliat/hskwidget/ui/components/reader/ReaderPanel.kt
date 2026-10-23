package fr.berliat.hskwidget.ui.components.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.bookmark_add_24px
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.dictionary_noresult_icon
import fr.berliat.hskwidget.dictionary_noresult_text
import fr.berliat.hskwidget.dictionary_noresultwithfilter_text
import fr.berliat.hskwidget.dictionary_search_loading
import fr.berliat.hskwidget.filter_alt_off_24px
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.ui.components.PrettyCardShapeModifier
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * Locked layout: takes all height, inner reader scrolls, panel itself does not.
 * Card stays glued to the bottom (like OCR).
 *
 * Column { CompactHeader(fixed) + Box(weight 1f)[reader] + Card(bottom, if any) }
 */
@Composable
fun ReaderPanel(
    text: String,
    textSize: Float,
    showPinyins: Boolean,
    separatorEnabled: Boolean,
    clickedWords: Map<String, String>,
    selectedWord: AnnotatedChineseWord?,
    dictionaryLocale: Locale,
    couldAnnotate: Boolean,
    onAnnotate: (String) -> Unit,
    onWordClick: (String) -> Unit,
    onAppeared: (List<Pair<String, String>>) -> Unit,
    onFailure: (StringResource) -> Unit,
    onFavoriteClick: (AnnotatedChineseWord) -> Unit,
    onSpeakClick: (AnnotatedChineseWord) -> Unit,
    onCopyClick: (AnnotatedChineseWord) -> Unit,
    onSearchTerm: ((String) -> Unit)? = null,
    onListsSaved: () -> Unit = {},
    cardTextSize: Float = textSize,
    segmenter: HSKTextSegmenter = HSKAppServices.HSKSegmenter,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        CompactNoResultHeader(
            query = text,
            couldAnnotate = couldAnnotate,
            onAnnotate = onAnnotate
        )

        // Analysing before the segmenter finishes preloading would leave
        // spacers that HSKTextView never retries, so gate the text on readiness.
        var isSegmenterReady by remember(segmenter) { mutableStateOf(segmenter.isReady()) }
        LaunchedEffect(segmenter) {
            while (!segmenter.isReady()) {
                delay(100.milliseconds)
            }
            isSegmenterReady = true
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (!isSegmenterReady) {
                LoadingView(loadingText = Res.string.dictionary_search_loading)
            } else {
                SegmentedReaderView(
                    text = text,
                    textSize = textSize,
                    showPinyins = showPinyins,
                    separatorEnabled = separatorEnabled,
                    clickedWords = clickedWords,
                    onWordClick = onWordClick,
                    onAppeared = onAppeared,
                    onFailure = onFailure,
                    segmenter = segmenter,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        SelectableWordCard(
            word = selectedWord,
            textSize = cardTextSize,
            dictionaryLocale = dictionaryLocale,
            onFavoriteClick = onFavoriteClick,
            onSpeakClick = onSpeakClick,
            onCopyClick = onCopyClick,
            onSearchTerm = onSearchTerm,
            shapeModifier = PrettyCardShapeModifier.First,
            onListsSaved = onListsSaved
        )
    }
}

@Composable
fun CompactNoResultHeader(
    query: String,
    couldAnnotate: Boolean,
    onAnnotate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (txt, icon) = if (couldAnnotate) {
        Pair(Res.string.dictionary_noresult_text, Res.drawable.bookmark_add_24px)
    } else {
        Pair(Res.string.dictionary_noresultwithfilter_text, Res.drawable.filter_alt_off_24px)
    }

    // Same card style as OCRReminder: bordered rounded box, bold text.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .then(if (couldAnnotate) Modifier.clickable { onAnnotate(query) } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(Res.string.dictionary_noresult_icon),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (couldAnnotate) stringResource(txt, query) else stringResource(txt),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
