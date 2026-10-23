package fr.berliat.hskwidget.ui.components.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.model.ChineseWord
import fr.berliat.hskwidget.ui.components.DetailedWordView
import fr.berliat.hskwidget.ui.components.PrettyCardShapeModifier
import fr.berliat.hskwidget.ui.screens.wordlist.WordListSelectionDialog

/**
 * Word detail card + lists dialog. Stateless: the caller owns [word] and
 * all callbacks (backed by ReaderSessionViewModel or DisplayOCRViewModel).
 * Glued to the bottom by the caller — never scrolls the reader itself.
 */
@Composable
fun SelectableWordCard(
    word: AnnotatedChineseWord?,
    textSize: Float,
    dictionaryLocale: Locale,
    onFavoriteClick: (AnnotatedChineseWord) -> Unit,
    onSpeakClick: (AnnotatedChineseWord) -> Unit,
    onCopyClick: (AnnotatedChineseWord) -> Unit,
    onSearchTerm: ((String) -> Unit)? = null,
    shapeModifier: PrettyCardShapeModifier = PrettyCardShapeModifier.First,
    onListsSaved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var listWord by remember { mutableStateOf<ChineseWord?>(null) }

    listWord?.let { w ->
        WordListSelectionDialog(
            word = w,
            onDismiss = { listWord = null },
            onSaved = {
                listWord = null
                onListsSaved()
            }
        )
    }

    word?.let { w ->
        DetailedWordView(
            word = w,
            pinyinEditable = false,
            textSize = textSize,
            dictionaryLocale = dictionaryLocale,
            onFavoriteClick = onFavoriteClick,
            onSpeakClick = onSpeakClick,
            onCopyClick = onCopyClick,
            onListsClick = { listWord = w.toChineseWord() },
            onSearchTerm = onSearchTerm,
            shapeModifier = shapeModifier,
            modifier = modifier
        )
    }
}
