package fr.berliat.hskwidget.ui.components.filterBar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.ocr_display_conf_bigger
import fr.berliat.hskwidget.ocr_display_conf_smaller
import fr.berliat.hskwidget.text_decrease_24px
import fr.berliat.hskwidget.text_increase_24px

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TextSizeChip(
    modifier: Modifier = Modifier,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val borderWidth = 1.dp
    val horizontalPadding = 13.dp
    val halfPillWidth = 60.dp
    val pillHeight = 32.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        tonalElevation = 0.dp,
        color = Color.Transparent,
        border = BorderStroke(borderWidth, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(pillHeight)
                .wrapContentWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp))
                    .clickable { onDecrease() }
                    .fillMaxHeight()
                    .width(halfPillWidth)
                    .padding(horizontal = horizontalPadding, vertical = 5.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Icon(
                    painter = painterResource(Res.drawable.text_decrease_24px),
                    contentDescription = stringResource(Res.string.ocr_display_conf_smaller),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VerticalDivider(thickness = borderWidth, color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 50.dp, bottomEnd = 50.dp))
                    .clickable { onIncrease() }
                    .fillMaxHeight()
                    .width(halfPillWidth)
                    .padding(horizontal = horizontalPadding, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.text_increase_24px),
                    contentDescription = stringResource(Res.string.ocr_display_conf_bigger),
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
