package fr.berliat.hskwidget.ui.navigation

import fr.berliat.hskwidget.core.Logging.logAnalyticsScreenView

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

import kotlin.reflect.KClass

/** What the host should do with its own navigation stack. */
sealed interface NavigationEvent {
    /** Push [screen] on top of the host back stack. */
    data class Push(val screen: Screen) : NavigationEvent

    /**
     * Go back to [previous]. A null [previous] means the mirrored stack is at
     * its root while the host can still pop, so the host should just pop once.
     */
    data class Back(val previous: Screen?) : NavigationEvent
}

object NavigationManager {
    /**
     * One-shot navigation commands. A channel (rather than a replaying shared
     * flow) so a command that was already handled is not replayed to a new
     * collector after the host is recreated, which used to push duplicates.
     */
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    const val STACK_SCREEN_DEPTH = 20

    /** The app's start destination, always kept at the bottom of the mirror. */
    private val ROOT_SCREEN = Screen.Dictionary()
    val stackScreen = ArrayDeque<Screen>(listOf(ROOT_SCREEN))

    private val _currentScreen = MutableStateFlow<Screen>(ROOT_SCREEN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow() // Expose as StateFlow

    fun registerScreenVisit(screen: Screen) {
        _currentScreen.update { screen }
        logCurrentScreen()

        if (screen == ROOT_SCREEN) {
            // Back on the app's anchor (drawer "Dictionary", or a pop back to
            // the start destination): keep a single root and drop whatever
            // stale history was left above it. Pushing a second root used to
            // make back() target an entry that was no longer on top.
            stackScreen.clear()
            stackScreen.add(ROOT_SCREEN)
            return
        }

        // Collapse earlier visits of the same screen so repeated round trips
        // (Dictionary -> Reader -> Dictionary ...) cannot pile up.
        stackScreen.removeAll { it == screen }

        if (stackScreen.size >= STACK_SCREEN_DEPTH) {
            // Never trim the root: it is the anchor every back() falls back to.
            stackScreen.removeAt(1)
        }
        if (stackScreen.lastOrNull() != screen) {
            stackScreen.add(screen)
        }
    }

    fun navigate(screen: Screen) {
        // Commands are emitted synchronously and in call order: dispatching
        // them on an IO scope could reorder a push and a back.
        _navigationEvents.trySend(NavigationEvent.Push(screen))
    }

    /**
     * Returns to an entry that is already in the stack instead of pushing a
     * duplicate of it (used by the "continue reading" reminder). Falls back to
     * a normal push when the screen is no longer in the mirror.
     */
    fun backTo(screen: Screen) {
        val index = stackScreen.indexOfLast { it == screen }
        if (index < 0) {
            // Not in the mirror anymore: a push is the only way there.
            navigate(screen)
            return
        }
        if (index == stackScreen.lastIndex) {
            // Already the current screen, nothing to unwind.
            return
        }

        while (stackScreen.size > index + 1) {
            stackScreen.removeLast()
        }
        _currentScreen.update { screen }
        logCurrentScreen()
        _navigationEvents.trySend(NavigationEvent.Back(screen))
    }

    /**
     * Pops the current screen and asks the host to go back to the previous one.
     * Keeping this here means every back gesture (system, toolbar, save
     * callbacks) unwinds through the same stack.
     */
    fun back() {
        val previous = if (stackScreen.size > 1) {
            stackScreen.removeLast()
            // Collapse consecutive duplicates left behind by re-entering a screen.
            while (stackScreen.size > 1 &&
                stackScreen.last() == stackScreen[stackScreen.size - 2]) {
                stackScreen.removeLast()
            }
            stackScreen.last()
        } else {
            null
        }

        // Even without a logical previous (mirror at its root) the host may
        // still pop a duplicate entry, so keep the reported screen on the
        // mirror's top instead of leaving it on the popped screen.
        val current = previous ?: stackScreen.firstOrNull()
        current?.let {
            _currentScreen.update { it }
            logCurrentScreen()
        }

        _navigationEvents.trySend(NavigationEvent.Back(previous))
    }

    fun inBackStack(screenClass: KClass<out Screen>): Boolean {
        return getFromBackStack(screenClass).isNotEmpty()
    }

    fun getFromBackStack(screenClass: KClass<out Screen>): List<Screen> {
        return stackScreen.filter { it::class == screenClass }
    }

    private fun logCurrentScreen() {
        logAnalyticsScreenView(_currentScreen.value::class.simpleName ?: "UNKNOWN_SCREEN")
    }
}