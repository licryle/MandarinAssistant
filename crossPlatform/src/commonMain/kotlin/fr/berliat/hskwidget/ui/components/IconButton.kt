package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun IconButton(text: String, onClick: () -> Unit,
               modifier: Modifier = Modifier,
               drawable: DrawableResource? = null,
               enabled: Boolean = true,
               btnColor: ButtonColors = ButtonDefaults.buttonColors()
) {
    Box(
        contentAlignment = Alignment.Center, // Centers content inside Box
        modifier = modifier
    ) {
        Button(onClick = onClick,
            enabled = enabled,
            colors = btnColor
        ) {
            drawable?.let {
                Icon(
                    painter = painterResource(drawable),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
            }

            Text(text)
        }
    }
}

@Preview
@Composable
fun IconButtonPreview() {
    AppTheme {
        IconButton(text = "Click Me", onClick = {})
    }
}