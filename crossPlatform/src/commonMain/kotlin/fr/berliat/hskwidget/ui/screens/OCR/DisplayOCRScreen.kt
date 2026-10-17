package fr.berliat.hskwidget.ui.screens.OCR

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle

import fr.berliat.hskwidget.core.Locale
import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hsktextviews.views.HSKTextView
import fr.berliat.hsktextviews.views.ShowPinyins
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.model.ChineseWord
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.ui.components.DetailedWordView
import fr.berliat.hskwidget.ui.components.TextSizeChip
import fr.berliat.hskwidget.ui.components.Error
import fr.berliat.hskwidget.ui.components.ErrorView
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.ui.screens.wordlist.WordListSelectionDialog
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.ocr_display_add
import fr.berliat.hskwidget.ocr_display_loading
import fr.berliat.hskwidget.ocr_display_ocr_no_text
import fr.berliat.hskwidget.ocr_display_pinyins
import fr.berliat.hskwidget.ocr_display_separator
import fr.berliat.hskwidget.ocr_display_text_segmentation_failed
import fr.berliat.hskwidget.photo_camera_24px
import fr.berliat.hskwidget.ui.components.PrettyCardShapeModifier
import fr.berliat.hskwidget.ui.horizontalScrollbar
import fr.berliat.hskwidget.ui.theme.AppTypographies

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import co.touchlab.kermit.Logger

private const val TAG = "DisplayOCRScreen"

@Composable
fun DisplayOCRScreen(
    modifier: Modifier = Modifier,
    viewModel: DisplayOCRViewModel = remember {
        val database = HSKAppServices.database
        DisplayOCRViewModel(
            savedStateHandle = SavedStateHandle(),
            appPreferences = HSKAppServices.appPreferences,
            annotatedChineseWordDAO = database.annotatedChineseWordDAO(),
            chineseWordFrequencyDAO = database.chineseWordFrequencyDAO(),
            segmenter = HSKAppServices.HSKSegmenter
        )
    },
    segmenter : HSKTextSegmenter = HSKAppServices.HSKSegmenter,
    appConfig : AppPreferencesStore = HSKAppServices.appPreferences,
    imageFile: PlatformFile? = null,
    preText: String = "",
    onClickOCRAdd : (String) -> Unit = {},
    onFavoriteClick : (AnnotatedChineseWord) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dictionaryTextSize by appConfig.dictionaryTextSize.asStateFlow().collectAsStateWithLifecycle()

    LaunchedEffect(preText) {
        viewModel.setText(preText)

        val word = uiState.selectedWord?.simplified ?: ""
        if (word != "")
            viewModel.fetchWordForDisplay(word)
    }

    LaunchedEffect(uiState.isSegmenterReady, imageFile) {
        if (uiState.isSegmenterReady && (imageFile != null)) {
            Logger.d(tag = TAG, messageString = "LaunchedEffect triggering recognizeText for ${imageFile.path}")
            viewModel.recognizeText(imageFile)
        }
    }

    if (uiState.isSegmenterReady && imageFile == null && preText == "") {
        HSKAppServices.snackbar.show(SnackbarType.WARNING,Res.string.ocr_display_ocr_no_text)
    }

    Column (modifier = modifier) {
        OcrDisplayConfig(modifier = modifier, viewModel = viewModel)

        Row(modifier = modifier.weight(1f)) {
            if (uiState.isProcessing) {
                LoadingView()
            } else {
                val error by viewModel.error.collectAsState()
                val err = error
                if (err != null) {
                    ErrorView(
                        modifier = modifier,
                        error = Error(err)
                    )
                } else {
                    Column {
                        HSKTextView(
                            text = uiState.text,
                            segmenter = segmenter,
                            hanziStyle = AppTypographies.hanzi.copy(fontSize = uiState.textSize.sp),
                            pinyinStyle = AppTypographies.pinyin,
                            clickedHanziStyle = AppTypographies.hanzi.copy(fontSize = uiState.textSize.sp),
                            clickedPinyinStyle = AppTypographies.pinyin,
                            clickedBackgroundColor = MaterialTheme.colorScheme.inverseOnSurface,
                            loadingComposable = { LoadingView(loadingText = Res.string.ocr_display_loading) },
                            emptyComposable = { OCRDisplayEmpty() },
                            onWordClick = { word -> viewModel.fetchWordForDisplay(word) },
                            showPinyins = if (uiState.showPinyins) ShowPinyins.ALL else ShowPinyins.CLICKED,
                            endSeparator = if (uiState.separatorEnabled) viewModel.wordSeparator else "",
                            clickedWords = uiState.clickedWords,
                            onTextAnalysisSuccess = { words -> viewModel.augmentWordFrequencyAppeared(words) },
                            onTextAnalysisFailure = { e ->
                                Logger.e(tag = TAG, messageString = "Text analysis failure: ${e.message}", throwable = e)
                                viewModel.setError(Res.string.ocr_display_text_segmentation_failed)
                            }
                        )

                        OcrDisplayAdd(
                            modifier = modifier,
                            onClick = { onClickOCRAdd(uiState.text) }
                        )
                    }
                }
            }
        }

        var showWordListDialog by remember { mutableStateOf<ChineseWord?>(null) }
        showWordListDialog?.let { word ->
            WordListSelectionDialog(
                word = word,
                onDismiss = { showWordListDialog = null },
                onSaved = { showWordListDialog = null }
            )
        }

        uiState.selectedWord?.let { word ->
            DetailedWordView(
                word = word,
                pinyinEditable = false,
                textSize = dictionaryTextSize.value,
                dictionaryLocale = Locale.resolve(appConfig.dictionaryLocale.value),
                onFavoriteClick = onFavoriteClick,
                onSpeakClick = viewModel::speakWord,
                onCopyClick = viewModel::copyToClipboard,
                onListsClick = { showWordListDialog = word.toChineseWord() },
                shapeModifier = PrettyCardShapeModifier.First
            )
        }
    }
}

@Composable
private fun OCRDisplayEmpty() {
    Text("No text returned")
}

@Composable
private fun OcrDisplayConfig(
    viewModel: DisplayOCRViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScrollbar(scrollState)
            .horizontalScroll(scrollState)
            .padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextSizeChip(
            onDecrease = { viewModel.updateTextSize(-2f) },
            onIncrease = { viewModel.updateTextSize(+2f) },
            modifier = Modifier.padding(end = 8.dp)
        )

        FilterChip(
            selected = uiState.separatorEnabled,
            onClick = { viewModel.toggleSeparator(!uiState.separatorEnabled) },
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(end = 8.dp),
            label = {
                Text(
                    text = stringResource(Res.string.ocr_display_separator),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )

        FilterChip(
            selected = uiState.showPinyins,
            onClick = { viewModel.toggleShowPinyins(!uiState.showPinyins) },
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(end = 8.dp),
            label = {
                Text(
                    text = stringResource(Res.string.ocr_display_pinyins),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}

@Composable
private fun OcrDisplayAdd(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clickable { onClick() }, // makes whole area clickable
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.photo_camera_24px),
            contentDescription = stringResource(Res.string.ocr_display_add),
            modifier = Modifier.size(50.dp),
            tint = MaterialTheme.colorScheme.onSurface // or Color.Unspecified if you want raw drawable
        )
        Text(
            text = stringResource(Res.string.ocr_display_add),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
