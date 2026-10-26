package fr.berliat.hskwidget.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.cancel
import fr.berliat.hskwidget.proceed

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun ConfirmDialog(
    title: StringResource,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmButtonLabel: StringResource = Res.string.proceed,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { onDismiss() },
        title = {
            Text(text = stringResource(title))
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text(stringResource(confirmButtonLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Preview
@Composable
fun ConfirmDialogPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            ConfirmDialog(
                title = Res.string.proceed,
                message = "Are you sure you want to proceed with this action?",
                onDismiss = {},
                onConfirm = {},
                confirmButtonLabel = Res.string.proceed
            )
        }
    }
}