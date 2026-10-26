package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaddedOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    label: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    shape: Shape = OutlinedTextFieldDefaults.shape,
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        textStyle = textStyle.copy(color = LocalContentColor.current),
        interactionSource = interactionSource,
        enabled = enabled,
        modifier = modifier,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = {
                    ProvideTextStyle(value = textStyle) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            innerTextField()
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                },
                enabled = enabled,
                singleLine = true,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                label = label,
                trailingIcon = trailingIcon,
                colors = colors,
                contentPadding = contentPadding,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape,
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            )
        }
    )
}

@Preview
@Composable
fun PaddedOutlinedTextFieldPreview() {
    AppTheme {
        val (text, setText) = remember { mutableStateOf("Sample input text") }
        PaddedOutlinedTextField(
            value = text,
            onValueChange = setText,
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}
