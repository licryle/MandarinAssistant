package fr.berliat.hskwidget.ui.components.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.ocr_display_ocr_no_text
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReaderEmpty(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.ocr_display_ocr_no_text),
        modifier = modifier
    )
}
