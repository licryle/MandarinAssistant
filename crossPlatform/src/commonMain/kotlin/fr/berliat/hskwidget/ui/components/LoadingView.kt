package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.loading
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    loadingText: StringResource = Res.string.loading
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(1f) // adjust if needed
            .background(backgroundColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingProgressView()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(loadingText),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview
@Composable
fun LoadingViewPreview() {
    AppTheme {
        LoadingView()
    }
}