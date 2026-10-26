package fr.berliat.hskwidget.ui.components.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.bookmark_add_24px
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme
import fr.berliat.hskwidget.dictionary_noresult_icon
import fr.berliat.hskwidget.dictionary_noresult_text
import fr.berliat.hskwidget.dictionary_noresultwithfilter_text
import fr.berliat.hskwidget.filter_alt_off_24px
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CompactNoResultHeader(
    word: AnnotatedChineseWord?,
    couldAnnotate: Boolean,
    onAnnotate: (AnnotatedChineseWord) -> Unit,
    modifier: Modifier = Modifier
) {
    val potentialWord = word?.simplified ?: ""
    val (txt, icon, mod) = if (couldAnnotate && potentialWord.isNotEmpty()) {
        Triple(
            stringResource(Res.string.dictionary_noresult_text, potentialWord),
            Res.drawable.bookmark_add_24px,
            Modifier.clickable { onAnnotate(word!!) }
        )
    } else {
        Triple(
            stringResource(Res.string.dictionary_noresultwithfilter_text),
            Res.drawable.filter_alt_off_24px,
            Modifier
        )
    }

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
            .then(mod),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(Res.string.dictionary_noresult_icon),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = txt,
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

@Preview
@Composable
fun CompactNoResultHeaderPreview() {
    AppTheme {
        CompactNoResultHeader(
            word = AnnotatedChineseWord.getBlank("测试"),
            couldAnnotate = true,
            onAnnotate = {}
        )
    }
}