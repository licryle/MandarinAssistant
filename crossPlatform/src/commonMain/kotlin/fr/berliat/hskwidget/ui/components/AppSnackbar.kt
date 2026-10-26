package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.ui.theme.snackbarStyleFor

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Column
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun AppSnackbar(
    message: String,
    type: SnackbarType,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    customIcon: DrawableResource? = null,
    modifier: Modifier = Modifier
) {
    val style = MaterialTheme.colorScheme.snackbarStyleFor(type)
    val displayIcon = customIcon ?: style.iconLeft

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = style.containerColor,
        contentColor = style.contentColor,
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 25.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            displayIcon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = type.name,
                    tint = style.contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = style.contentColor
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AppSnackbarPreview() {
    AppTheme {
        Column {
            AppSnackbar(
                message = "This is a success notification message.",
                type = SnackbarType.SUCCESS,
                actionLabel = "Dismiss",
                onActionClick = {}
            )
        }
    }
}
