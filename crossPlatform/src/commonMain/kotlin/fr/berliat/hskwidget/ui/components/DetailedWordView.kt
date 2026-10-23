package fr.berliat.hskwidget.ui.components

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.font.FontStyle.Companion.Italic
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.fillMaxHeight

import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.type.Modality
import fr.berliat.hskwidget.data.type.WordType
import fr.berliat.hskwidget.data.type.HSK_Level
import fr.berliat.hskwidget.ui.theme.AppTypographies
import fr.berliat.hskwidget.ui.toRes
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.baseline_volume_up_24
import fr.berliat.hskwidget.bookmark_24px
import fr.berliat.hskwidget.bookmark_heart_24px
import fr.berliat.hskwidget.content_copy_24px
import fr.berliat.hskwidget.dictionary_item_altdefinition
import fr.berliat.hskwidget.dictionary_item_annotate
import fr.berliat.hskwidget.dictionary_item_antonym
import fr.berliat.hskwidget.dictionary_item_examples
import fr.berliat.hskwidget.dictionary_item_lists
import fr.berliat.hskwidget.dictionary_item_modality
import fr.berliat.hskwidget.dictionary_item_synonyms
import fr.berliat.hskwidget.dictionary_item_toggle
import fr.berliat.hskwidget.dictionary_item_type
import fr.berliat.hskwidget.format_list_bulleted_add_24px
import fr.berliat.hskwidget.keyboard_arrow_down_24px
import fr.berliat.hskwidget.keyboard_arrow_up_24px
import fr.berliat.hskwidget.search_24px
import fr.berliat.hskwidget.search_hint
import fr.berliat.hskwidget.dictionary_item_collocations
import fr.berliat.hskwidget.widget_btn_copy
import fr.berliat.hskwidget.widget_btn_speak

import HSKWordView

/**
 * When [verticallyConstrained] is true, the center column must not contribute to the
 * Row's intrinsic height, so the Row height is defined by the side (button) columns
 * only. Combined with fillMaxHeight + clipToBounds, the center content is then
 * cropped to the height of the first column instead of expanding the card.
 */
private fun Modifier.excludeFromHeightIntrinsics(): Modifier = this.then(
    object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int
        ): Int = 0

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int
        ): Int = 0

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int
        ): Int = measurable.minIntrinsicWidth(height)

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int
        ): Int = measurable.maxIntrinsicWidth(height)
    }
)

@Composable
fun DetailedWordView (
    word: AnnotatedChineseWord,
    pinyinEditable: Boolean,
    shapeModifier: PrettyCardShapeModifier,
    modifier: Modifier = Modifier,
    textSize: Float = 16f,
    dictionaryLocale: Locale = Locale.ENGLISH,
    onFavoriteClick: ((AnnotatedChineseWord) -> Unit)? = null,
    onSpeakClick: ((AnnotatedChineseWord) -> Unit)? = null,
    onCopyClick: ((AnnotatedChineseWord) -> Unit)? = null,
    onListsClick: ((AnnotatedChineseWord) -> Unit)? = null,
    onSearchTerm: ((String) -> Unit)? = null,
    onPinyinChange: (String) -> Unit = {},
    showAnnotation: Boolean = true,
    verticallyConstrained: Boolean = false,
) {
    // Compute definition / annotation / alt definition
    var definition = word.word?.definition?.get(dictionaryLocale) ?: ""
    var annotation = word.annotation?.notes ?: ""
    if (definition.isEmpty()) {
        definition = annotation
        annotation = ""
    }
    val altDef = if (dictionaryLocale != Locale.CN_HSK3)
        word.word?.definition?.get(Locale.CN_HSK3) ?: ""
    else
        word.word?.definition?.get(Locale.getDefault()) ?: ""

    // Pinyins
    var pinyins = word.pinyins.toString()

    // Show/Hide "more" section
    var isMoreVisible by remember { mutableStateOf(false) }
    val nothingMore = (altDef + (word.word?.examples ?: "") + (word.word?.collocations ?: "") + (word.word?.antonym ?: "") + (word.word?.synonyms ?: ""))
        .isEmpty() &&
            (word.word?.modality ?: Modality.UNKNOWN) == Modality.UNKNOWN &&
            (word.word?.wordType ?: WordType.UNKNOWN) == WordType.UNKNOWN

    val hanziFontSize = AppTypographies.hanzi.fontSize.value + (textSize - 16f) * 0.5f
    val pinyinFontSize = AppTypographies.pinyin.fontSize.value + (textSize - 16f) * 0.5f
    val bodyLargeStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = textSize.sp,
        lineHeight = (textSize * 1.05f).sp
    )
    val bodyMediumStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = (textSize - 2f).coerceAtLeast(10f).sp,
        lineHeight = ((textSize - 2f).coerceAtLeast(10f) * 1.05f).sp
    )

    PrettyCard(
        onClick = {
            if (nothingMore || verticallyConstrained) return@PrettyCard

            isMoreVisible = !isMoreVisible
            if (isMoreVisible)
                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.WIDGET_EXPAND)
            else
                Logging.logAnalyticsEvent(Logging.ANALYTICS_EVENTS.WIDGET_COLLAPSE)
        },
        shapeModifier = shapeModifier,
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().wrapContentWidth(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    onSpeakClick?.let {
                        RoundIconButton(
                            Res.drawable.baseline_volume_up_24,
                            Res.string.widget_btn_speak,
                            { onSpeakClick(word) }
                        )
                    }

                    onCopyClick?.let {
                        RoundIconButton(
                            Res.drawable.content_copy_24px,
                            Res.string.widget_btn_copy,
                            { onCopyClick(word) }
                        )
                    }
                }

                val vSpacing = if (nothingMore || verticallyConstrained) Arrangement.Top else Arrangement.SpaceBetween
                Column(
                    modifier = Modifier
                        .then(if (verticallyConstrained) Modifier.excludeFromHeightIntrinsics() else Modifier)
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds(),
                    verticalArrangement = vSpacing
                ) {
                    HSKWordView(
                        hanziText = word.simplified,
                        pinyinText = pinyins,
                        pinyinEditable = pinyinEditable && !word.hasWord(),
                        onPinyinChange = {
                            if (pinyins != it) {
                                pinyins = it
                                onPinyinChange(it)
                            }
                        },
                        hanziStyle = AppTypographies.hanzi.copy(fontSize = hanziFontSize.sp),
                        pinyinStyle = AppTypographies.pinyin.copy(fontSize = pinyinFontSize.sp),
                        clickedHanziStyle = AppTypographies.clickedHanzi.copy(fontSize = hanziFontSize.sp),
                        clickedPinyinStyle = AppTypographies.clickedPinyin.copy(fontSize = pinyinFontSize.sp),
                    )
                    // Definition & Annotation
                    Text(
                        definition.ifEmpty { annotation },
                        style = bodyLargeStyle,
                        overflow = TextOverflow.Clip,
                        softWrap = true
                    )
                    if (showAnnotation && !definition.isEmpty() && annotation.isNotEmpty()) {
                        Text(
                            annotation,
                            style = bodyMediumStyle.copy(fontStyle = Italic),
                            color = MaterialTheme.colorScheme.secondary,
                            overflow = TextOverflow.Clip,
                            softWrap = true
                        )
                    }

                    // Toggle more (hidden when cropped, since it can't be expanded anyway)
                    if (!nothingMore) {
                        Row(horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                painter = painterResource(
                                    if (isMoreVisible) Res.drawable.keyboard_arrow_up_24px
                                    else Res.drawable.keyboard_arrow_down_24px
                                ),
                                contentDescription = stringResource(Res.string.dictionary_item_toggle)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxHeight().wrapContentWidth(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    onFavoriteClick?.let {
                        IconButton(onClick = { onFavoriteClick(word) }) {
                            val resId =
                                if (word.hasAnnotation()) Res.drawable.bookmark_heart_24px else Res.drawable.bookmark_24px
                            Icon(
                                painter = painterResource(resId),
                                contentDescription = stringResource(Res.string.dictionary_item_annotate),
                                tint = if (word.hasAnnotation()) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }

                    val hSK = word.word?.let {
                        (it.hskLevel?.takeIf{ lvl -> lvl != HSK_Level.NOT_HSK }  ?: "").toString()
                    } ?: ""
                    Text(
                        text = hSK,
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )

                    onListsClick?.let {
                        RoundIconButton(
                            Res.drawable.format_list_bulleted_add_24px,
                            Res.string.dictionary_item_lists,
                            { onListsClick(word) }
                        )
                    }
                }
            }

            if (isMoreVisible) {
                // More section
                Row(
                    modifier = Modifier.padding(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val modality = word.word?.modality ?: Modality.UNKNOWN
                        if (modality != Modality.UNKNOWN) {
                            Text(
                                stringResource(Res.string.dictionary_item_modality),
                                style = AppTypographies.detailCardSubTitle)
                            Text(
                                stringResource(modality.toRes()),
                                style = bodyMediumStyle
                            )
                        }

                        word.word?.synonyms?.takeIf { it.isNotEmpty() }?.let {
                            SearchableDetailField(
                                title = stringResource(Res.string.dictionary_item_synonyms),
                                content = it,
                                bodyStyle = bodyMediumStyle,
                                onSearchTerm = onSearchTerm,
                                splitRegex = Regex("[\\n,，、；;]+")
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        val wordType = word.word?.wordType ?: WordType.UNKNOWN
                        if (wordType != WordType.UNKNOWN) {
                            Text(
                                stringResource(Res.string.dictionary_item_type),
                                style = AppTypographies.detailCardSubTitle)
                            Text(
                                stringResource(wordType.toRes()),
                                style = bodyMediumStyle
                            )
                        }

                        word.word?.antonym?.takeIf { it.isNotEmpty() }?.let {
                            SearchableDetailField(
                                title = stringResource(Res.string.dictionary_item_antonym),
                                content = it,
                                bodyStyle = bodyMediumStyle,
                                onSearchTerm = onSearchTerm,
                                splitRegex = Regex("[\\n,，、；;]+")
                            )
                        }
                    }
                }

                word.word?.collocations?.takeIf { it.isNotEmpty() }?.let {
                    Row(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        SearchableDetailField(
                            title = stringResource(Res.string.dictionary_item_collocations),
                            content = it,
                            bodyStyle = bodyMediumStyle,
                            onSearchTerm = onSearchTerm,
                            splitRegex = Regex("\\n+"),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                word.word?.examples?.takeIf { it.isNotEmpty() }?.let {
                    Row(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        SearchableDetailField(
                            title = stringResource(Res.string.dictionary_item_examples),
                            content = it,
                            bodyStyle = bodyMediumStyle,
                            onSearchTerm = onSearchTerm,
                            splitRegex = Regex("\\n+"),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(10.dp)
                ) {
                    Column {
                        Text(
                            stringResource(Res.string.dictionary_item_altdefinition),
                            style = AppTypographies.detailCardSubTitle)

                        if (altDef.isNotEmpty()) {
                            Text(altDef, style = bodyMediumStyle)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Detail field split into one searchable row per entry, magnifier at the
 * beginning of each line (not far right). Whole line is clickable.
 *
 * Split rules: collocations/examples break on newlines (each line = 1 entry);
 * synonyms/antonyms additionally break on `, ， 、 ； ;`.
 */
@Composable
private fun SearchableDetailField(
    title: String,
    content: String,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    onSearchTerm: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    splitRegex: Regex? = null
) {
    Column(modifier = modifier) {
        Text(title, style = AppTypographies.detailCardSubTitle)
        // DB strings sometimes carry literal "\n" escapes (backslash + n)
        // instead of real line breaks — normalize first so they split too.
        val normalized = content.replace("\\n", "\n")
        if (onSearchTerm == null || splitRegex == null) {
            Text(normalized, style = bodyStyle)
            return@Column
        }
        val entries = normalized.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.isEmpty()) {
            Text(normalized, style = bodyStyle)
            return@Column
        }
        // Fallback: single entry identical to content keeps old whole-block search.
        if (entries.size == 1 && entries[0] == normalized.trim()) {
            SearchableLine(
                entry = entries[0],
                bodyStyle = bodyStyle,
                onSearchTerm = onSearchTerm
            )
            return@Column
        }
        entries.forEach { entry ->
            SearchableLine(
                entry = entry,
                bodyStyle = bodyStyle,
                onSearchTerm = onSearchTerm
            )
        }
    }
}

@Composable
private fun SearchableLine(
    entry: String,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    onSearchTerm: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSearchTerm?.let{ onSearchTerm(entry) } },
        verticalAlignment = Alignment.Top
    ) {
        onSearchTerm?.let {
            IconButton(
                onClick = { onSearchTerm(entry) },
                modifier = Modifier.size(28.dp).padding(top = 2.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.search_24px),
                    contentDescription = stringResource(Res.string.search_hint),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            entry,
            style = bodyStyle,
            modifier = Modifier.weight(1f).padding(top = 4.dp, bottom = 4.dp)
        )
    }
}
