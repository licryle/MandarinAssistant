package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

/*** Note: I couldn't use the OutlineTextDefaults because it gives buggy controls over padding */
@Composable
fun OutlinedContainer(
    label: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart, // <-- CONTENT GRAVITY
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    content: @Composable () -> Unit // <-- AGNOSTIC SLOT
) {
    var labelWidth by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val strokeWidth = with(density) { 1.dp.toPx() }
    val cornerRadius = with(density) { 4.dp.toPx() }
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .drawBehind {
                val textPaddingStart = 8.dp.toPx()
                val textGapWidth = labelWidth + 8.dp.toPx()
                val path = Path().apply {
                    moveTo(textPaddingStart + textGapWidth, 0f)
                    lineTo(size.width - cornerRadius, 0f)
                    arcTo(Rect(size.width - cornerRadius * 2, 0f, size.width, cornerRadius * 2), 270f, 90f, false)
                    lineTo(size.width, size.height - cornerRadius)
                    arcTo(Rect(size.width - cornerRadius * 2, size.height - cornerRadius * 2, size.width, size.height), 0f, 90f, false)
                    lineTo(cornerRadius, size.height)
                    arcTo(Rect(0f, size.height - cornerRadius * 2, cornerRadius * 2, size.height), 90f, 90f, false)
                    lineTo(0f, cornerRadius)
                    arcTo(Rect(0f, 0f, cornerRadius * 2, cornerRadius * 2), 180f, 90f, false)
                    lineTo(textPaddingStart, 0f)
                }
                drawPath(
                    path = path,
                    color = outlineColor,
                    style = Stroke(width = strokeWidth)
                )
            }
            .padding(contentPadding)
    ) {
        // Content Wrapper applying your gravity rule
        Box(
            modifier = Modifier.fillMaxHeight(), // Crucial for IntrinsicSize.Min rows
            contentAlignment = contentAlignment
        ) {
            content()
        }

        // Floating label
        val textStyle = MaterialTheme.typography.bodySmall
        Text(
            text = label,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onTextLayout = { textLayoutResult ->
                labelWidth = textLayoutResult.size.width.toFloat()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer {
                    translationY = (textStyle.fontSize * -1).toPx()
                    translationX = 4.dp.toPx()
                }
        )
    }
}

@Preview
@Composable
fun OutlinedContainerPreview() {
    AppTheme {
        OutlinedContainer(
            label = "Sample Label",
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(
                text = "Container Content Goes Here",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
