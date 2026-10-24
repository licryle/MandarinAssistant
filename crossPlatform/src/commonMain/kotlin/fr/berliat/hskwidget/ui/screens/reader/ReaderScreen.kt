package fr.berliat.hskwidget.ui.screens.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle

import fr.berliat.hsktextviews.HSKTextSegmenter
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.ui.components.Error
import fr.berliat.hskwidget.ui.components.ErrorView
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.ui.components.reader.ReaderFilterBar
import fr.berliat.hskwidget.ui.components.reader.SegmentedReaderView
import fr.berliat.hskwidget.ui.components.reader.SelectableWordCard
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.ocr_display_add
import fr.berliat.hskwidget.ocr_display_ocr_no_text
import fr.berliat.hskwidget.photo_camera_24px
import fr.berliat.hskwidget.ui.components.PrettyCardShapeModifier

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path

import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import co.touchlab.kermit.Logger
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.ui.components.reader.CompactNoResultHeader

private const val TAG = "ReaderScreen"

@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = remember {
        val database = HSKAppServices.database
        ReaderViewModel(
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
    allowPicture: Boolean = false,
    couldAnnotate: Boolean = false,
    onClickOCRAdd : (String) -> Unit = {},
    onFavoriteClick : (AnnotatedChineseWord) -> Unit = {},
    onSearchTerm: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showPinyins by viewModel.showPinyins.collectAsState()
    val separateWords by viewModel.separateWords.collectAsState()
    val readerTextSize by viewModel.readerTextSize.collectAsState()
    val dictTextSize by viewModel.dictTextSize.collectAsState()
    val dictionaryLocale by viewModel.dictionaryLocale.collectAsState()

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
        ReaderFilterBar(prefs = appConfig, modifier = modifier)

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
                        if (couldAnnotate) {
                            Row {
                                val potentialWord = AnnotatedChineseWord.getBlank(preText)
                                CompactNoResultHeader(
                                    word = potentialWord,
                                    couldAnnotate = couldAnnotate,
                                    onAnnotate = onFavoriteClick
                                )
                            }
                        }

                        SegmentedReaderView(
                            text = uiState.text,
                            textSize = readerTextSize,
                            showPinyins = showPinyins,
                            separatorEnabled = separateWords,
                            clickedWords = uiState.clickedWords,
                            onWordClick = { word -> viewModel.fetchWordForDisplay(word) },
                            onAppeared = { words -> viewModel.augmentWordFrequencyAppeared(words) },
                            onFailure = { res -> viewModel.setError(res) },
                            segmenter = segmenter,
                            wordSeparator = viewModel.wordSeparator,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (allowPicture) {
                            OcrDisplayAdd(
                                modifier = modifier,
                                onClick = { onClickOCRAdd(uiState.text) }
                            )
                        }
                    }
                }
            }
        }

        SelectableWordCard(
            word = uiState.selectedWord,
            textSize = dictTextSize,
            dictionaryLocale = dictionaryLocale,
            onFavoriteClick = onFavoriteClick,
            onSpeakClick = viewModel::speakWord,
            onCopyClick = viewModel::copyToClipboard,
            onSearchTerm = onSearchTerm,
            shapeModifier = PrettyCardShapeModifier.First
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
