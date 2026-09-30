package fr.berliat.hskwidget.core

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Defines the types of snackbar messages.
 * Each type will have different styling (colors, icons, etc.)
 */
enum class SnackbarType {
    /** Informational message (default) */
    INFO,

    /** Warning message */
    WARNING,

    /** Error message */
    ERROR,

    /** Success message */
    SUCCESS
}

/**
 * Data class representing a snackbar message with optional action and callbacks.
 *
 * @param messageRes The string resource for the message to display
 * @param messageArgs Arguments to format the message string resource
 * @param type The type of snackbar (INFO, WARNING, ERROR) which determines styling
 * @param duration How long to show the snackbar
 * @param actionLabelRes Optional string resource for an action button label
 * @param onAction Optional callback invoked when the action is clicked
 * @param onDismiss Optional callback invoked when the snackbar is dismissed
 */
data class SnackbarMessage(
    val messageRes: StringResource,
    val messageArgs: List<String> = emptyList(),
    val type: SnackbarType = SnackbarType.INFO,
    val duration: SnackbarDuration = SnackbarDuration.Long,
    val actionLabelRes: StringResource? = null,
    val onAction: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null
)

/**
 * Global message bus for showing snackbars throughout the app.
 * Uses a Flow-based pattern to decouple snackbar requests from the UI layer.
 *
 * Usage:
 * - Call [show] from anywhere in the app to queue a snackbar message
 * - Collect [messages] in your Scaffold to display snackbars
 */
object SnackbarManager {
    private val _messages = MutableSharedFlow<SnackbarMessage>(
        replay = 0,
        extraBufferCapacity = 10, // Buffer up to 10 messages
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /**
     * Flow of snackbar messages. Collect this in your UI layer to display snackbars.
     */
    val messages: SharedFlow<SnackbarMessage> = _messages.asSharedFlow()

    /**
     * Suspend until the UI collector ([AppSnackbarHost]) is active, so a
     * boot-time emission isn't lost: with `replay = 0` an emission with no
     * active collector is dropped. Times out silently when nothing will ever
     * collect (e.g. widget-only init).
     */
    suspend fun awaitCollector(timeoutMs: Duration = 10_000.milliseconds) {
        withTimeoutOrNull(timeoutMs) { _messages.subscriptionCount.first { it > 0 } }
    }
    
    /**
     * Show a snackbar with the given parameters.
     *
     * @param messageRes The string resource for the message to display
     * @param messageArgs Arguments to format the message (default: empty)
     * @param type The type of snackbar (INFO, WARNING, ERROR) - determines styling
     * @param duration How long to show the snackbar (default: Long)
     * @param actionLabelRes Optional string resource for an action button label
     * @param onAction Optional callback invoked when the action is clicked
     * @param onDismiss Optional callback invoked when the snackbar is dismissed
     */
    fun show(
        type: SnackbarType,
        messageRes: StringResource,
        messageArgs: List<String> = emptyList(),
        duration: SnackbarDuration = SnackbarDuration.Short,
        actionLabelRes: StringResource? = null,
        onAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        _messages.tryEmit(
            SnackbarMessage(
                messageRes = messageRes,
                messageArgs = messageArgs,
                type = type,
                duration = duration,
                actionLabelRes = actionLabelRes,
                onAction = onAction,
                onDismiss = onDismiss
            )
        )
    }
}
