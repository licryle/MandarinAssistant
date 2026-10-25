package fr.berliat.hskwidget.ui.screens.dictionary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.model.ChineseWord
import fr.berliat.hskwidget.ui.components.DetailedWordView
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.ui.components.DbUpdateView
import fr.berliat.hskwidget.ui.components.filterBar.PrefsFilterBar
import fr.berliat.hskwidget.ui.components.filterBar.TextSizeMode
import fr.berliat.hskwidget.ui.components.reader.CompactNoResultHeader
import fr.berliat.hskwidget.ui.navigation.Screen
import fr.berliat.hskwidget.ui.screens.wordlist.WordListSelectionDialog

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.bookmark_add_24px
import fr.berliat.hskwidget.core.ContainHanziResult
import fr.berliat.hsktextviews.PinyinUtils.isHanzi
import fr.berliat.hskwidget.core.containsHanzi
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.dictionary_search_loading
import fr.berliat.hskwidget.dictionary_noresult_icon
import fr.berliat.hskwidget.domain.DatabaseHelper
import fr.berliat.hskwidget.ui.components.PrettyCardShapeModifier
import fr.berliat.hskwidget.ui.dismissKeyboardOnTap
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DictionarySearchScreen(
    onAnnotate: (AnnotatedChineseWord) -> Unit,
    onSearchTerm: (String) -> Unit = {},
    onNavigate: (Screen) -> Unit = {},
    modifier: Modifier = Modifier,
    initialIntentSource: IntentSources,
    viewModel: DictionarySearchViewModel = remember { DictionarySearchViewModel(
        prefsStore = HSKAppServices.appPreferences,
        annotatedChineseWordDAO = HSKAppServices.database.annotatedChineseWordDAO(),
        widgetProvider = FlashcardWidgetProvider()
    ) }
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val hasMoreResults by viewModel.hasMoreResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val updateProgress by DatabaseHelper.updateProgress.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val wordExists by viewModel.wordExists.collectAsState()
    val hasAnnotationFilter by viewModel.hasAnnotationFilter.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val dictionaryLocale by viewModel.dictionaryLocale.collectAsState()
    val textLocale by viewModel.textLocale.collectAsState()

    var showWordListDialog by remember { mutableStateOf<ChineseWord?>(null) }

    val listState = rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    val query = searchQuery.query
    val queryHasHanzi = when (query.containsHanzi()) {
        ContainHanziResult.SOME, ContainHanziResult.ALL -> true
        ContainHanziResult.NONE, ContainHanziResult.EMPTY -> false
    }
    val isLongText = query.length > 100 || query.count { it.isHanzi() } > 20
    val mayAnnotate = wordExists == false && queryHasHanzi
    val couldAnnotate = mayAnnotate && !isLongText
    val isEmptyResult = results.isEmpty()

    // Nothing in the dictionary for a hanzi query: analyse the text in place.
    // Gated on `couldAnnotate` (the word really is absent) so that a word that
    // exists but is hidden by a filter keeps the normal filters + "filtered
    // out" message instead of swapping the chips away. Derived from state only,
    // so typing/clearing can never be fought by a screen change; the one
    // navigation step it implies is handled below.
    val isReaderMode = isEmptyResult && mayAnnotate

    if (isReaderMode) {
        onNavigate(
            Screen.Reader(
                preText = searchQuery.query,
                imageFilePath = null,
                couldAnnotate = couldAnnotate,
                allowPicture = false
            )
        )

        return
    }

    // Whenever searchQuery changes, scroll to top.
    // The first search after an external navigation (widget/share) is attributed
    // to that intent; later searches (typing) are in-app. Re-armed per navigation.
    var pendingSource by remember(initialIntentSource) { mutableStateOf(initialIntentSource) }
    LaunchedEffect(
        searchQuery.toString(),
        hasAnnotationFilter.toString(),
        dictionaryLocale.toString()
    ) {
        viewModel.performSearch(pendingSource)
        pendingSource = IntentSources.IN_APP
        listState.scrollToItem(0)
    }

    showWordListDialog?.let {
        WordListSelectionDialog(
            word = it,
            onDismiss = { showWordListDialog = null },
            onSaved = {
                viewModel.listsAssociationChanged()
                showWordListDialog = null
            },
            modifier = modifier
        )
    }

    Column(modifier = modifier.fillMaxSize().dismissKeyboardOnTap()) {
        PrefsFilterBar(
            textSizeMode = TextSizeMode.DICTIONARY,
            showAnnotationFilter = true
        )

        // Main content: word results, or a segmented reading of the query when
        // no word matches.
        Box(modifier = Modifier.fillMaxSize()) {
            if (updateProgress != null) {
                DbUpdateView(progress = updateProgress)
            } else if (isLoading) {
                LoadingView(loadingText = Res.string.dictionary_search_loading)
            } else if (isEmptyResult) {
                CompactNoResultHeader(
                    word = null,
                    couldAnnotate = couldAnnotate,
                    onAnnotate = onAnnotate
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    itemsIndexed(results) { index, word ->
                        DetailedWordView(
                            word = word,
                            pinyinEditable = false,
                            textSize = textSize.value,
                            dictionaryLocale = textLocale,
                            onFavoriteClick = onAnnotate,
                            onSpeakClick = { viewModel.speakWord(word.simplified) },
                            onCopyClick = { viewModel.copyWord(word.simplified) },
                            onListsClick = { showWordListDialog = word.toChineseWord() },
                            onSearchTerm = onSearchTerm,
                            shapeModifier = when {
                                results.size == 1 -> PrettyCardShapeModifier.Single
                                index == 0 -> PrettyCardShapeModifier.First
                                index == results.size - 1 && !hasMoreResults -> PrettyCardShapeModifier.Last
                                else -> PrettyCardShapeModifier.Middle
                            }
                        )

                        if (!isLoading && !isLoadingMore
                            && hasMoreResults && index >= results.size - 5) {
                            // Trigger pagination
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                        }
                    }
                }
            }

            if (couldAnnotate && !isReaderMode && !isLoading && updateProgress == null) {
                FloatingActionButton(
                    onClick = { onAnnotate(AnnotatedChineseWord.getBlank(query)) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    content = {
                        Icon(
                            painter = painterResource(Res.drawable.bookmark_add_24px),
                            contentDescription = stringResource(Res.string.dictionary_noresult_icon)
                        )
                    }
                )
            }
        }
    }
}
