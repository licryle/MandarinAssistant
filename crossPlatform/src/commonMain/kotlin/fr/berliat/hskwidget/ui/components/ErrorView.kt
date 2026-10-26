package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.error_24px
import fr.berliat.hskwidget.fix_it
import fr.berliat.hskwidget.loading
import fr.berliat.hskwidget.refresh_24px
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

class Error(
    val errorText: StringResource = Res.string.loading,
    val retryText: StringResource = Res.string.fix_it,
    val onRetryClick: (() -> Unit)? = null
)

@Composable
fun ErrorView(
    backgroundColor: Color = MaterialTheme.colorScheme.background, // replace with theme color if needed
    modifier: Modifier = Modifier,
    error : Error = Error()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f) // adjust if needed
            .background(backgroundColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(Res.drawable.error_24px),
            contentDescription = null,
            modifier = Modifier.size(50.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(error.errorText),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        error.onRetryClick?.let {
            IconButton(
                onClick = error.onRetryClick,
                text = stringResource(error.retryText),
                drawable = Res.drawable.refresh_24px
            )
        }
    }
}

@Preview
@Composable
fun ErrorViewPreview() {
    AppTheme {
        ErrorView(
            error = Error(
                errorText = Res.string.loading,
                retryText = Res.string.fix_it,
                onRetryClick = {}
            )
        )
    }
}