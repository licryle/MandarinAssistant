package fr.berliat.hskwidget.ui.screens.widgetConfigure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.data.model.WordListWithCount
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.ui.screens.widget.WidgetView
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.format_list_bulleted_add_24px
import fr.berliat.hskwidget.ui.localizedName
import fr.berliat.hskwidget.ui.theme.widgetDefaultBox
import fr.berliat.hskwidget.widget_configure
import fr.berliat.hskwidget.widget_configure_close
import fr.berliat.hskwidget.widget_configure_error_no_list
import fr.berliat.hskwidget.widget_configure_frequency
import fr.berliat.hskwidget.widget_configure_frequency_hint
import fr.berliat.hskwidget.widget_configure_frequency_options
import fr.berliat.hskwidget.widget_configure_new_back
import fr.berliat.hskwidget.widget_configure_no_change
import fr.berliat.hskwidget.widget_configure_wordlist_title
import fr.berliat.hskwidget.widget_configure_wordlist_word_count

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WidgetConfigWithPreviewScreen(
    widgetId: Int,
    onSuccessfulSave : () -> Unit,
    modifier: Modifier = Modifier,
    expectsActivityResult: Boolean = false,
    viewModel: WidgetConfigViewModel = remember(widgetId) {
        WidgetConfigViewModel(widgetId, onSuccessfulSave = onSuccessfulSave)
    }
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 15.dp)
    ) {
        Box(widgetDefaultBox.align(Alignment.CenterHorizontally)) {
            WidgetView(widgetId = widgetId, dictionaryLocale = viewModel.dictionaryLocale.value)
        }

        Spacer(modifier = modifier.height(10.dp))

        WidgetConfigScreen(
            widgetId = widgetId,
            expectsActivityResult = expectsActivityResult,
            onSuccessfulSave = onSuccessfulSave,
            viewModel = viewModel,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    widgetId: Int,
    onSuccessfulSave : () -> Unit,
    modifier: Modifier = Modifier,
    expectsActivityResult: Boolean = false,
    viewModel: WidgetConfigViewModel = remember(widgetId) {
        WidgetConfigViewModel(widgetId, onSuccessfulSave = onSuccessfulSave)
    }
) {
    val allLists = viewModel.allLists.collectAsState()
    val selectedIds = viewModel.selectedListIds.collectAsState()
    val localSelectedIds = remember(selectedIds) { mutableStateSetOf<Long>() }

    val refreshInterval = viewModel.refreshInterval.collectAsState()
    val localRefreshInterval = remember(refreshInterval) { mutableLongStateOf(refreshInterval.value) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIds.value) {
        // Clear and update the local state only when the ViewModel state changes
        localSelectedIds.clear()
        localSelectedIds.addAll(selectedIds.value)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        // Refresh rate
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.widget_configure_frequency),
                style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.weight(1f))

            Box {
                val refreshOptions = stringArrayResource(Res.array.widget_configure_frequency_options)
                val refreshIntervals =
                    refreshOptions.associate { option ->
                        option.substringBefore(" ").toLong() to
                                option.substringAfter(" ")
                    }
                val localRefreshValue = if (refreshIntervals.contains(localRefreshInterval.longValue))
                    refreshIntervals.getValue(localRefreshInterval.longValue)
                else
                    refreshIntervals.getValue(refreshIntervals.keys.first())


                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.width(160.dp)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = localRefreshValue,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                        label = {
                            Text(
                                stringResource(Res.string.widget_configure_frequency_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }) {
                        refreshIntervals.map {
                            DropdownMenuItem(
                                text = { Text(it.value, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { localRefreshInterval.longValue = it.key; expanded = false }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.format_list_bulleted_add_24px),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.widget_configure_wordlist_title),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (allLists.value.isEmpty()) {
            LoadingView()
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            // List container (scrollable)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(allLists.value, key = { it.id }) { list ->
                    WidgetConfigListItem(
                        list = list,
                        isSelected = localSelectedIds.contains(list.id),
                        onToggle = { included ->
                            if (included) localSelectedIds.add(list.id) else localSelectedIds.remove(
                                list.id
                            )
                        }
                    )
                }
            }

            fun hasUnsavedChanges() =
                !localSelectedIds.isEmpty() && (
                        localSelectedIds.toSet() != selectedIds.value.toSet()
                        || localRefreshInterval.longValue != refreshInterval.value)

            val configureButtonLabel = when {
                localSelectedIds.isEmpty() -> Res.string.widget_configure_error_no_list
                !hasUnsavedChanges() -> Res.string.widget_configure_no_change
                expectsActivityResult && selectedIds.value.isEmpty() -> Res.string.widget_configure_new_back
                expectsActivityResult -> Res.string.widget_configure_close
                else -> Res.string.widget_configure
            }

            // Confirm button
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                onClick = { viewModel.savePreferences(localSelectedIds, localRefreshInterval.longValue) },
                enabled = hasUnsavedChanges()
            ) {
                Text(text = stringResource(configureButtonLabel))
            }
        }
    }
}

// Todo: solve that flicker
@Composable
private fun WidgetConfigListItem(
    list: WordListWithCount,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = list.localizedName(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(Res.string.widget_configure_wordlist_word_count, list.wordCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 8.dp)
        )
        Switch(
            checked = isSelected,
            onCheckedChange = onToggle
        )
    }
}
