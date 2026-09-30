package fr.berliat.hskwidget.ui.screens.annotate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults.contentPaddingWithoutLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.data.type.ClassLevel
import fr.berliat.hskwidget.data.type.ClassType
import fr.berliat.hskwidget.ui.components.ConfirmDialog
import fr.berliat.hskwidget.ui.components.DetailedWordView
import fr.berliat.hskwidget.ui.components.DropdownSelector
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.annotation_edit_class_level_hint
import fr.berliat.hskwidget.annotation_exam_switch
import fr.berliat.hskwidget.annotation_edit_class_type_hint
import fr.berliat.hskwidget.annotation_edit_delete_confirm_message
import fr.berliat.hskwidget.annotation_edit_delete_confirm_title
import fr.berliat.hskwidget.annotation_edit_delete_failure
import fr.berliat.hskwidget.annotation_edit_delete_success
import fr.berliat.hskwidget.annotation_edit_is_exam_hint
import fr.berliat.hskwidget.annotation_edit_notes_hint
import fr.berliat.hskwidget.annotation_edit_save_failure
import fr.berliat.hskwidget.annotation_edit_save_success
import fr.berliat.hskwidget.core.SnackbarType
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.delete
import fr.berliat.hskwidget.save
import fr.berliat.hskwidget.ui.components.OutlinedContainer
import fr.berliat.hskwidget.ui.components.PrettyCardShapeModifier
import fr.berliat.hskwidget.ui.dismissKeyboardOnClick
import fr.berliat.hskwidget.ui.toRes

import org.jetbrains.compose.resources.stringResource

@Composable
fun AnnotateScreen(
    word: String,
    onSaveSuccess: (String) -> Unit,
    onDeleteSuccess: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnnotateViewModel = remember { AnnotateViewModel(
        prefsStore = HSKAppServices.appPreferences,
        database = HSKAppServices.database,
        wordListRepo = HSKAppServices.wordListRepo,
        ankiCaller = HSKAppServices.ankiDelegator
    ) }
) {
    var annotatedWord by remember { mutableStateOf<AnnotatedChineseWord?>(null) }

    var pinyins by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var themes by remember { mutableStateOf("") }
    var isExam by remember { mutableStateOf(false) }
    var selectedClassType by remember { mutableStateOf(viewModel.lastAnnotatedClassType.value) }
    var selectedClassLevel by remember { mutableStateOf(viewModel.lastAnnotatedClassLevel.value) }

    var confirmDeleteDialog by remember { mutableStateOf(false) }
    val examToggleDesc = stringResource(Res.string.annotation_exam_switch)

    var notesFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val constrainWordView = notesFocused || imeVisible

    LaunchedEffect(word) {
        annotatedWord = viewModel.fetchAnnotatedWord(word)

        annotatedWord.let { word ->
            pinyins = word?.pinyins.toString()
            notes = word?.annotation?.notes.orEmpty()
            themes = word?.annotation?.themes.orEmpty()
            isExam = word?.annotation?.isExam ?: false
            selectedClassType = if (word?.hasAnnotation() ?: false) {
                word.annotation?.classType ?: viewModel.lastAnnotatedClassType.value
            } else {
                viewModel.lastAnnotatedClassType.value
            }
            selectedClassLevel = if (word?.hasAnnotation() ?: false) {
                word.annotation?.level ?: viewModel.lastAnnotatedClassLevel.value
            } else {
                viewModel.lastAnnotatedClassLevel.value
            }
        }
    }

    fun toastAndAssessSuccess(word: String, action: ACTION, e: Exception?): Boolean {
        val msgRes = when {
            action == ACTION.DELETE && e == null -> Res.string.annotation_edit_delete_success
            action == ACTION.DELETE && e != null -> Res.string.annotation_edit_delete_failure
            action == ACTION.UPDATE && e == null -> Res.string.annotation_edit_save_success
            action == ACTION.UPDATE && e != null -> Res.string.annotation_edit_save_failure
            else -> throw (Exception()) // We'll crash
        }

        if (e == null) {
            HSKAppServices.snackbar.show(SnackbarType.SUCCESS, msgRes, listOf(word))
            return true
        } else {
            HSKAppServices.snackbar.show(SnackbarType.ERROR, msgRes, listOf(word, e.message ?: ""))
            return false
        }
    }

    val fixedAnnotatedWord = annotatedWord
    if (fixedAnnotatedWord == null) {
        LoadingView()
        return
    }

    if (confirmDeleteDialog) {
        ConfirmDialog(
            title = Res.string.annotation_edit_delete_confirm_title,
            message = stringResource(Res.string.annotation_edit_delete_confirm_message),
            onDismiss = { confirmDeleteDialog = false },
            onConfirm = {
                viewModel.deleteAnnotation(word) { word, e ->
                    if (toastAndAssessSuccess(word, ACTION.DELETE, e)) onDeleteSuccess(word)
                }
                confirmDeleteDialog = false
            },
            confirmButtonLabel = Res.string.delete,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        DetailedWordView(
            word = annotatedWord!!,
            onSpeakClick = { viewModel.speakWord(annotatedWord!!.simplified) },
            onCopyClick = { viewModel.copyWord(annotatedWord!!.simplified) },
            onFavoriteClick = null,
            onListsClick = null,
            onPinyinChange = { pinyins = it },
            pinyinEditable = true,
            dictionaryLocale = Locale.resolve(HSKAppServices.appPreferences.dictionaryLocale.value),
            modifier = Modifier,
            shapeModifier = PrettyCardShapeModifier.Single,
            showAnnotation = false,
            verticallyConstrained = constrainWordView
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(Res.string.annotation_edit_notes_hint)) },
            modifier = Modifier.fillMaxWidth().weight(1f)
                .onFocusChanged { notesFocused = it.isFocused }
        )

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            val littlePadding = contentPaddingWithoutLabel(8.dp, 4.dp, 8.dp, 4.dp)
            val switchPadding = contentPaddingWithoutLabel(8.dp, 4.dp, 8.dp, 0.dp)

            DropdownSelector(
                label = stringResource(Res.string.annotation_edit_class_type_hint),
                options = ClassType.entries,
                selected = selectedClassType,
                onSelected = { selectedClassType = it },
                labelProvider = { stringResource(it.toRes()) },
                contentPadding = littlePadding,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )

            DropdownSelector(
                label = stringResource(Res.string.annotation_edit_class_level_hint),
                options = ClassLevel.entries,
                selected = selectedClassLevel,
                onSelected = { selectedClassLevel = it },
                labelProvider = { stringResource(it.toRes()) },
                contentPadding = littlePadding,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 3.dp, end = 3.dp),
            )

            OutlinedContainer(
                label = stringResource(Res.string.annotation_edit_is_exam_hint),
                contentPadding = switchPadding,
                contentAlignment = Alignment.Center, // Centers the Switch vertically and horizontally
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
            ) {
                Switch(
                    checked = isExam,
                    modifier = Modifier.dismissKeyboardOnClick()
                        .semantics { contentDescription = examToggleDesc },
                    onCheckedChange = { isExam = it }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (fixedAnnotatedWord.hasAnnotation()) {
                OutlinedButton(
                    onClick = { confirmDeleteDialog = true },
                    modifier = Modifier.dismissKeyboardOnClick()
                ) { Text(stringResource(Res.string.delete)) }
            }

            Button(
                onClick = {
                    viewModel.saveWord(
                        annotatedWord = fixedAnnotatedWord,
                        pinyins,
                        notes,
                        themes,
                        isExam,
                        cType = selectedClassType,
                        cLevel = selectedClassLevel) { word, e ->
                        if (toastAndAssessSuccess(word.simplified, ACTION.UPDATE, e)) onSaveSuccess(word.simplified)
                    }
                },
                modifier = Modifier.weight(1f).dismissKeyboardOnClick()
            ) {
                Text(stringResource(Res.string.save))
            }
        }
    }
}

private enum class ACTION {
    UPDATE,
    DELETE
}