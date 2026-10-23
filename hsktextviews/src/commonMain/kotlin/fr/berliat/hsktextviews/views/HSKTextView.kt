package fr.berliat.hsktextviews.views

import HSKWordView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hsktextviews.PinyinUtils.getPinyins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class ShowPinyins(val value: Int) {
    NONE(0),
    CLICKED(1),
    ALL(2);

    companion object {
        fun fromInt(value: Int): ShowPinyins {
            return entries.firstOrNull { it.value == value } ?: NONE
        }
    }
}

@Composable
fun HSKTextView(
    text: String,
    segmenter: HSKTextSegmenter? = null,
    showPinyins: ShowPinyins = ShowPinyins.ALL,
    endSeparator: String = "",
    hanziStyle: TextStyle,
    pinyinStyle: TextStyle,
    clickedWords: Map<String, String>,
    clickedHanziStyle: TextStyle,
    clickedPinyinStyle: TextStyle,
    clickedBackgroundColor: Color = Color.Black,
    loadingComposable: @Composable () -> Unit,
    emptyComposable: @Composable () -> Unit,
    onWordClick: ((String) -> Unit)? = null,
    onTextAnalysisSuccess: ((List<Pair<String, String>>) -> Unit)? = null,
    onTextAnalysisFailure: ((e: Exception) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var words by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    var isLoading by remember { mutableStateOf(true) }
    var isSegmented by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val trimText = text.trim()
    LaunchedEffect(trimText, segmenter) {
        if (trimText.isBlank()) return@LaunchedEffect

        val seg = segmenter
        // The segmenter preloads in the background (it can take a while on cold
        // start). Rather than blocking the screen, show the raw glyphs right
        // away and re-segment in place once the segmenter is ready.
        if (seg == null) {
            words = withContext(Dispatchers.Default) { glyphFallback(trimText) }
            isSegmented = false
            isLoading = false
            return@LaunchedEffect
        }
        if (!seg.isReady()) {
            words = withContext(Dispatchers.Default) { glyphFallback(trimText) }
            isSegmented = false
            isLoading = false
            while (!seg.isReady()) delay(100)
        }

        val parsedWords = withContext(Dispatchers.Default) { segmentWords(trimText, seg) }
        words = parsedWords
        isSegmented = true
        isLoading = false

        if (parsedWords.isEmpty()) {
            onTextAnalysisFailure?.invoke(Exception("Segmenter returned no words"))
        } else {
            onTextAnalysisSuccess?.invoke(parsedWords)
        }
    }

    when {
        isLoading -> loadingComposable()
        trimText.isBlank() || words.isEmpty() -> emptyComposable()
        else ->
            FlowRow(
                modifier = modifier.fillMaxWidth().verticalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.5.dp)
            ) {
                words.forEach { (word, pinyin) ->
                    if (word == "\n") {
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        val isClicked = clickedWords.keys.contains(word)
                        val truePinyin = if (isClicked) { // Clicked words should contain the truePinyin
                            clickedWords[word]!!
                        } else {
                            pinyin
                        }
                        val showedPinyin = when(showPinyins) {
                            ShowPinyins.NONE -> ""
                            ShowPinyins.CLICKED -> if (isClicked) truePinyin else ""
                            ShowPinyins.ALL -> truePinyin
                        }

                        HSKWordView(
                            modifier = modifier,
                            hanziText = word,
                            pinyinText = showedPinyin,
                            pinyinEditable = false,
                            isClicked = isClicked,
                            onWordClick = { onWordClick?.invoke(word) },
                            hanziStyle = hanziStyle,
                            pinyinStyle = pinyinStyle,
                            clickedHanziStyle = clickedHanziStyle,
                            clickedPinyinStyle = clickedPinyinStyle,
                            clickedBackgroundColor = clickedBackgroundColor
                        )

                        // optional end separator
                        if (endSeparator.isNotEmpty() && isSegmented) {
                            Box(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 2.dp)
                                    .align(Alignment.CenterVertically),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = endSeparator)
                            }
                        }
                    }
                }
            }
    }
}

/** Segment every paragraph into words, keeping the blank line between paragraphs. */
private fun segmentWords(text: String, segmenter: HSKTextSegmenter): List<Pair<String, String>> {
    val parsedWords = mutableListOf<Pair<String, String>>()
    text.split("\n").forEach { paragraph ->
        segmenter.segment(paragraph)?.forEach { word ->
            parsedWords.add(word to word.getPinyins().joinToString(" "))
        }
        parsedWords.add("\n" to "")
    }
    return parsedWords
}

/** One word per glyph: shown while the segmenter is still preloading. */
private fun glyphFallback(text: String): List<Pair<String, String>> {
    val parsedWords = mutableListOf<Pair<String, String>>()
    text.split("\n").forEach { paragraph ->
        paragraph.forEach { char ->
            val glyph = char.toString()
            parsedWords.add(glyph to glyph.getPinyins().joinToString(" "))
        }
        parsedWords.add("\n" to "")
    }
    return parsedWords
}