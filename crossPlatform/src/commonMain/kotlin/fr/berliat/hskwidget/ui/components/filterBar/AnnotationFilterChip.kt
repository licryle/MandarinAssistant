package fr.berliat.hskwidget.ui.components.filterBar

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.bookmark_heart_24px
import fr.berliat.hskwidget.dictionary_search_filter_hasannotation_hint
import fr.berliat.hskwidget.ui.dismissKeyboardOnClick
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme


@Composable
fun AnnotationFilterChip(
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        shape = RoundedCornerShape(50),
        modifier = modifier.padding(end = 8.dp).dismissKeyboardOnClick(),
        label = {
            Icon(
                painter = painterResource(Res.drawable.bookmark_heart_24px),
                contentDescription = stringResource(Res.string.dictionary_search_filter_hasannotation_hint),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.dictionary_search_filter_hasannotation_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    )
}

@Preview
@Composable
fun AnnotationFilterChipPreview() {
    AppTheme {
        AnnotationFilterChip(selected = true, onToggle = {})
    }
}
