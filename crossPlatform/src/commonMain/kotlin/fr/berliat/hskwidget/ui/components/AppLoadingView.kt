package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.app_name
import fr.berliat.hskwidget.ic_launcher

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun AppLoadingView(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_launcher),
                modifier = modifier.size(190.dp),
                contentDescription = stringResource(Res.string.app_name),
                tint = Color.Unspecified // This is the fix for the black icon
            )
        }
    }
}

@Preview
@Composable
fun AppLoadingViewPreview() {
    AppTheme {
        AppLoadingView()
    }
}