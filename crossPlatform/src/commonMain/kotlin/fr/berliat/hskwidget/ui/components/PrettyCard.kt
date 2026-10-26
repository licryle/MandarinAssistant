package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.ui.dismissKeyboardOnClick
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme
import androidx.compose.material3.Text

sealed interface PrettyCardShapeModifier {
    object First: PrettyCardShapeModifier
    object Last: PrettyCardShapeModifier
    object Single: PrettyCardShapeModifier
    object Middle: PrettyCardShapeModifier
}

@Composable
fun PrettyCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
    borderColor: Color = Color.Transparent,
    roundCornerRadius: Dp = 20.dp,
    shapeModifier: PrettyCardShapeModifier = PrettyCardShapeModifier.Single,
    onClick : () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val modifiedShape = when(shapeModifier) {
        PrettyCardShapeModifier.First -> RoundedCornerShape(topStart = roundCornerRadius, topEnd = roundCornerRadius)
        PrettyCardShapeModifier.Last -> RoundedCornerShape(bottomStart = roundCornerRadius, bottomEnd = roundCornerRadius)
        PrettyCardShapeModifier.Single -> RoundedCornerShape(roundCornerRadius)
        PrettyCardShapeModifier.Middle -> RoundedCornerShape(size = 0.dp)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .dismissKeyboardOnClick()
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = modifiedShape
            )
            .padding(vertical = 1.5.dp),
        shape = modifiedShape,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(0.dp),
            content = content
        )
    }
}

@Preview
@Composable
fun PrettyCardPreview() {
    AppTheme {
        PrettyCard(modifier = Modifier.padding(16.dp)) {
            Text("Pretty Card Content", modifier = Modifier.padding(16.dp))
        }
    }
}
