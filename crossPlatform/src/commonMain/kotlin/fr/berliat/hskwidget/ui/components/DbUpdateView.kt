package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.database_updating_progress
import fr.berliat.hskwidget.loading
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun DbUpdateView(
    progress: Float?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.size(64.dp),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (progress != null) stringResource(Res.string.database_updating_progress, progress.toInt()) else stringResource(Res.string.loading),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Preview
@Composable
fun DbUpdateViewPreview() {
    AppTheme {
        DbUpdateView(progress = 45f)
    }
}
