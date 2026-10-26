package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import org.jetbrains.compose.ui.tooling.preview.Preview
import fr.berliat.hskwidget.ui.theme.AppTheme

@Composable
fun SmartLinearProgressIndicator(
    progress: Float, // <0 or >1: indeterminate
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap
) {
    if (progress in 0f..1f) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth(),
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap
        )
    }
}

@Preview
@Composable
fun SmartLinearProgressIndicatorPreview() {
    AppTheme {
        SmartLinearProgressIndicator(progress = 0.5f)
    }
}
