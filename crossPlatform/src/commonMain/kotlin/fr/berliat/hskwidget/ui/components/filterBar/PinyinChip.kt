package fr.berliat.hskwidget.ui.components.filterBar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.ocr_display_pinyins
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme


@Composable
fun PinyinChip(
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        shape = RoundedCornerShape(50),
        modifier = modifier.padding(end = 8.dp),
        label = {
            Text(
                text = stringResource(Res.string.ocr_display_pinyins),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    )
}

@Preview
@Composable
fun PinyinChipPreview() {
    AppTheme {
        PinyinChip(selected = true, onToggle = {})
    }
}