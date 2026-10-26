package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults.contentPaddingWithoutLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.ui.dismissKeyboardOnClick
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    labelProvider: @Composable (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = contentPaddingWithoutLabel(16.dp, 16.dp, 16.dp, 16.dp)
) {
    var expanded by remember { mutableStateOf(false) }
    val textStyle = MaterialTheme.typography.bodyMedium
    val interactionSource = remember { MutableInteractionSource() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth().dismissKeyboardOnClick()
    ) {
        PaddedOutlinedTextField(
            value = labelProvider(selected),
            onValueChange = {},
            readOnly = true,
            textStyle = textStyle,
            interactionSource = interactionSource,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            contentPadding = contentPadding,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelProvider(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun DropdownSelectorPreview() {
    AppTheme {
        var selected by remember { mutableStateOf("English") }
        DropdownSelector(
            label = "Language",
            options = listOf("English", "French", "Chinese"),
            selected = selected,
            onSelected = { selected = it }
        )
    }
}
