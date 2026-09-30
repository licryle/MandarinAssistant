package fr.berliat.hskwidget.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.dictionary_filter_language_label
import fr.berliat.hskwidget.dictionary_language_filter
import fr.berliat.hskwidget.keyboard_arrow_down_24px
import fr.berliat.hskwidget.translate_24px
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageFilterChip(
    selectedLocale: Locale?,
    supportedLocales: List<Locale>,
    includeNullLocale: Boolean,
    onLocaleSelected: (Locale?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val languageDesc = stringResource(Res.string.dictionary_language_filter)

    Box(modifier = modifier) {
        FilterChip(
            selected = false, // We use it as a trigger, not a toggle
            onClick = { expanded = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val flag = selectedLocale?.flag ?: Res.drawable.translate_24px
                    Icon(
                        painter = painterResource(flag),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selectedLocale?.flag == null) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Unspecified
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(Res.drawable.keyboard_arrow_down_24px),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(end = 8.dp)
                .semantics { contentDescription = languageDesc }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (includeNullLocale) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(Res.drawable.translate_24px),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.dictionary_filter_language_label))
                        }
                    },
                    onClick = {
                        onLocaleSelected(null)
                        expanded = false
                    }
                )
            }

            supportedLocales.forEach { locale ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            locale.flag?.let {
                                Icon(
                                    painter = painterResource(it),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(locale.displayName))
                        }
                    },
                    onClick = {
                        onLocaleSelected(locale)
                        expanded = false
                    }
                )
            }
        }
    }
}
