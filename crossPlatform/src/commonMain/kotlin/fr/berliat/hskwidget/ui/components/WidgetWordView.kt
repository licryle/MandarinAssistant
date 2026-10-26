package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.baseline_refresh_24
import fr.berliat.hskwidget.baseline_volume_up_24
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.model.ChineseWord
import fr.berliat.hskwidget.data.model.ChineseWordAnnotation
import fr.berliat.hskwidget.data.type.ClassLevel
import fr.berliat.hskwidget.data.type.ClassType
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme
import fr.berliat.hskwidget.data.type.HSK_Level
import fr.berliat.hskwidget.data.type.Modality
import fr.berliat.hskwidget.data.type.Pinyins
import fr.berliat.hskwidget.data.type.WordType
import fr.berliat.hskwidget.dictionary_item_reload
import fr.berliat.hskwidget.widget_btn_speak
import kotlinx.datetime.Instant

@Composable
fun WidgetWordView(
    word: AnnotatedChineseWord,
    modifier: Modifier = Modifier,
    dictionaryLocale: Locale? = Locale.ENGLISH,
    onClickUpdate: () -> Unit = {},
    onClickSpeak: () -> Unit = {},
    onClickWord: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(3.dp)
    ) {
        // Top row: reload - level - speak
        Row(
            modifier = Modifier.fillMaxWidth().padding(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(
                iconRes = Res.drawable.baseline_refresh_24,
                contentDescriptionRes = Res.string.dictionary_item_reload,
                onClick = onClickUpdate,
            )

            Spacer(modifier = Modifier.weight(1f))

            if (word.hskLevel != HSK_Level.NOT_HSK) {
                Text(
                    text = word.word?.hskLevel.toString(),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            RoundIconButton(
                iconRes = Res.drawable.baseline_volume_up_24,
                contentDescriptionRes = Res.string.widget_btn_speak,
                onClick = onClickSpeak
            )
        }

        // Middle content: text
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
                .clickable(
                    enabled = true,
                    onClick = onClickWord
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word.pinyins.toString(),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(0.dp)
            )

            Text(
                text = word.simplified,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(0.dp)
            )

            Text(
                text = word.word?.definition[dictionaryLocale ?: Locale.ENGLISH] ?: word.annotation?.notes ?: "",
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(0.dp)
            )
        }
    }
}

@Preview
@Composable
fun WidgetWordViewPreview() {
    AppTheme {
        WidgetWordView(
            word = AnnotatedChineseWord(
                word = ChineseWord(
                    simplified ="你好",
                    traditional = "妳好",
                    hskLevel = HSK_Level.HSK1,
                    pinyins = Pinyins("nǐ hǎo"),
                    popularity = 100,
                    examples = "你好吗？",
                    collocations = "你好, 您好",
                    modality = Modality.ORAL,
                    wordType = WordType.INTERJECTION,
                    synonyms = "您好",
                    antonym = "再见",
                    searchableText = ""
                ),
                annotation = ChineseWordAnnotation(
                    simplified = "你好",
                    pinyins = Pinyins("nǐ hǎo"),
                    notes = "important note",
                    classType = ClassType.Speaking,
                    level = ClassLevel.Elementary1,
                    themes = "greeting",
                    firstSeen = Instant.fromEpochMilliseconds(1672531200000L),
                    isExam = true,
                    searchableText = ""
                )
            )
        )
    }
}