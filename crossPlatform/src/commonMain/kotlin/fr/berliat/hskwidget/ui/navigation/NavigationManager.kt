package fr.berliat.hskwidget.ui.navigation

import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.Logging.logAnalyticsScreenView

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _navigationEvents = MutableSharedFlow<NavigationEvent>(replay = 1)
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()

    const val STACK_SCREEN_DEPTH = 20
    val stackScreen = ArrayDeque<Screen>(listOf(Screen.Dictionary()))

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dictionary())
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow() // Expose as StateFlow

    fun registerScreenVisit(screen: Screen) {
        _currentScreen.update { screen }
        logCurrentScreen()

        // Collapse earlier visits of the same screen so repeated round trips
        // (Dictionary -> Reader -> Dictionary ...) cannot pile up. The first
        // entry is the app's anchor and is never dropped.
        val root = stackScreen.firstOrNull()
        stackScreen.removeAll { it == screen && it !== root }

        if (stackScreen.size >= STACK_SCREEN_DEPTH) {
            stackScreen.removeFirst()
        }
        if (stackScreen.lastOrNull() != screen) {
            stackScreen.add(screen)
        }
    }

    fun navigate(screen: Screen) {
        scope.launch(AppDispatchers.IO) {
            _navigationEvents.emit(NavigationEvent.Push(screen))
        }
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

        previous?.let {
            _currentScreen.update { it }
            logCurrentScreen()
        }

        scope.launch(AppDispatchers.IO) {
            _navigationEvents.emit(NavigationEvent.Back(previous))
        }
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