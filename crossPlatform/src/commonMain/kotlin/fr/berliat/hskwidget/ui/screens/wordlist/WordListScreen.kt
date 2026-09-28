package fr.berliat.hskwidget.ui.screens.wordlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.data.model.WordList
import fr.berliat.hskwidget.data.model.WordListWithCount
import fr.berliat.hskwidget.ui.components.ConfirmDialog
import fr.berliat.hskwidget.core.YYMMDD
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.ui.components.PrettyCard
import fr.berliat.hskwidget.ui.components.RoundIconButton

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.annotation_edit_delete_confirm_message
import fr.berliat.hskwidget.annotation_edit_delete_confirm_title
import fr.berliat.hskwidget.delete
import fr.berliat.hskwidget.delete_24px
import fr.berliat.hskwidget.edit_24px
import fr.berliat.hskwidget.ic_add_24dp
import fr.berliat.hskwidget.ui.components.PrettyCardShapeModifier
import fr.berliat.hskwidget.ui.localizedName
import fr.berliat.hskwidget.ui.theme.AppSizes.screenWithFABBottomPadding
import fr.berliat.hskwidget.wordlist_create_new_list_button
import fr.berliat.hskwidget.wordlist_createddate
import fr.berliat.hskwidget.wordlist_delete_button
import fr.berliat.hskwidget.wordlist_editeddate
import fr.berliat.hskwidget.wordlist_rename_button
import fr.berliat.hskwidget.wordlist_word_count

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WordListScreen(
    onClickList: (WordList) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WordListViewModel = remember {
        WordListViewModel(HSKAppServices.wordListRepo, HSKAppServices.ankiDelegator)
    }
) {
    val wordLists by viewModel.allLists.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var confirmDeleteList by remember { mutableStateOf<WordList?>(null) }
    var wordListToRename by remember { mutableStateOf<WordList?>(null) }

    val listState = rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    if (showCreateDialog) {
        WordListCreateRenameDialog(
            viewModel = viewModel,
            list = null,
            onSuccess = { showCreateDialog = false },
            onCancel = { showCreateDialog = false },
            modifier = modifier
        )
    }

    val cDL = confirmDeleteList
    if (cDL != null) {
        ConfirmDialog(
            title = Res.string.annotation_edit_delete_confirm_title,
            message = stringResource(Res.string.annotation_edit_delete_confirm_message),
            onConfirm = {
                viewModel.deleteList(cDL)
                confirmDeleteList = null
            },
            onDismiss = { confirmDeleteList = null },
            confirmButtonLabel = Res.string.delete,
            modifier = modifier
        )
    }

    val wLTR = wordListToRename
    wLTR?.let {
        WordListCreateRenameDialog(
            viewModel = viewModel,
            list = wordListToRename,
            onSuccess = { wordListToRename = null },
            onCancel = { wordListToRename = null },
            defaultName = wLTR.name,
            modifier = modifier
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)) {
            itemsIndexed(wordLists) { index, wordList ->
                WordListRow(
                    wordList = wordList,
                    onClick = { onClickList(wordList.wordList) },
                    onRename = { wordListToRename = wordList.wordList },
                    onDelete = { confirmDeleteList = wordList.wordList },
                    modifier = modifier,
                    shapeModifier = when {
                        wordLists.size == 1 -> PrettyCardShapeModifier.Single
                        index == 0 -> PrettyCardShapeModifier.First
                        index == wordLists.size - 1 -> PrettyCardShapeModifier.Last
                        else -> PrettyCardShapeModifier.Middle
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            content = {
                Icon(
                    painter = painterResource(Res.drawable.ic_add_24dp),
                    contentDescription = stringResource(Res.string.wordlist_create_new_list_button)
                )
            }
        )
    }
}

@Composable
private fun WordListRow(
    wordList: WordListWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    shapeModifier: PrettyCardShapeModifier,
    modifier: Modifier = Modifier
) {
    PrettyCard(
        onClick = onClick,
        shapeModifier = shapeModifier
    ) {
        Row(modifier = Modifier, verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween) {

            val hideBtn = (wordList.listType == WordList.ListType.SYSTEM)
            RoundIconButton(
                iconRes = Res.drawable.edit_24px,
                contentDescriptionRes = Res.string.wordlist_rename_button,
                onClick = { if (!hideBtn) onRename() },
                modifier = modifier.alpha(if (hideBtn) 0f else 1f)
            )

            Column(modifier = Modifier
                .weight(1f)
                .padding(10.dp)) {
                Text(
                    text = wordList.localizedName(),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(
                        modifier = modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(Res.string.wordlist_word_count, wordList.wordCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(Res.string.wordlist_editeddate, wordList.lastModified.YYMMDD()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(Res.string.wordlist_createddate, wordList.creationDate.YYMMDD()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            RoundIconButton(
                iconRes = Res.drawable.delete_24px,
                contentDescriptionRes = Res.string.wordlist_delete_button,
                onClick = { if (!hideBtn) onDelete() },
                modifier = modifier.alpha(if (hideBtn) 0f else 1f)
            )
        }
    }

    if (shapeModifier == PrettyCardShapeModifier.Last) {
        Spacer(modifier = modifier.height(screenWithFABBottomPadding))
    }
}
