package fr.berliat.hskwidget.ui.application.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.menu
import fr.berliat.hskwidget.menu_24px
import fr.berliat.hskwidget.menu_ocr
import fr.berliat.hskwidget.photo_camera_24px
import fr.berliat.hskwidget.search_hint

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    onOcrClick: () -> Unit,
    onMenuClick: () -> Unit,
    viewModel: AppBarViewModel = remember { AppBarViewModel() }
) {
    // All query state, sync and debounce live in the ViewModel; this stays a
    // dumb renderer so typing can never race navigation from here.
    val localText by viewModel.localText.collectAsState()

    TopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, // center children vertically
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                PillSearchBar(
                    query = localText,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                viewModel.moveCursorToEnd()
                            }
                        },
                    hint = stringResource(Res.string.search_hint),
                    onClear = {
                        focusRequester.requestFocus()
                        viewModel.clearSearch()
                    }
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(painterResource(Res.drawable.menu_24px),
                    contentDescription = stringResource(Res.string.menu))
            }
        },
        actions = {
            IconButton(onClick = onOcrClick) {
                Icon(
                    painterResource(Res.drawable.photo_camera_24px),
                    contentDescription = stringResource(Res.string.menu_ocr)
                )
            }
        }
    )
}
