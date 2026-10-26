package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.app_name
import fr.berliat.hskwidget.ic_launcher
import fr.berliat.hskwidget.widget_not_configured

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun WidgetEmptyWordView (
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher),
            contentDescription = stringResource(Res.string.app_name),
            modifier = Modifier
                .width(70.dp)
                .height(70.dp),
        )

        Text(
            text = stringResource(Res.string.widget_not_configured),
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview
@Composable
fun WidgetEmptyWordViewPreview() {
    AppTheme {
        WidgetEmptyWordView()
    }
}
