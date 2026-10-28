package fr.berliat.hskwidget.ui.application.content

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import fr.berliat.hskwidget.data.store.AppPreferencesStore
import fr.berliat.hskwidget.domain.SearchQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class AppBarViewModel(
    prefsStore: AppPreferencesStore,
    private val onQueryChange: (String) -> Unit
) : ViewModel() {
    val searchQuery = prefsStore.searchQuery.asStateFlow()

    private val _localText = MutableStateFlow(TextFieldValue(searchQuery.value.toString()))
    val localText: StateFlow<TextFieldValue> = _localText.asStateFlow()

    private var debounceJob: Job? = null

    init {
        // Sync external query changes (deep links, list consult) into the field.
        // Skips the reset when the field already holds the same query, so
        // in-flight typing is never clobbered by its own debounced write.
        viewModelScope.launch {
            searchQuery.collect { remote ->
                if (SearchQuery.fromString(_localText.value.text) != remote) {
                    val text = remote.toString()
                    _localText.value = TextFieldValue(text, TextRange(text.length))
                }
            }
        }
    }

    fun onQueryChange(newValue: TextFieldValue) {
        _localText.value = newValue
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300.milliseconds) // 300ms debounce
            val currentText = _localText.value.text
            if (currentText != searchQuery.value.toString()) {
                onQueryChange(currentText)
            }
        }
    }

    fun clearSearch() {
        onQueryChange(TextFieldValue(""))
    }

    fun moveCursorToEnd() {
        val current = _localText.value
        _localText.value = current.copy(selection = TextRange(current.text.length))
    }
}
