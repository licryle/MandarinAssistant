package fr.berliat.hskwidget.ui.application

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.native.ObjCName
import kotlin.experimental.ExperimentalObjCName

/**
 * Entry point for platform intents on iOS (deep links, widget taps, share).
 *
 * No replay: an intent already consumed by a previous `AppViewModel` must not
 * be re-handled by a new one (that used to push a stale screen after the real
 * navigation). Android does not use this bus — its `MainActivity` forwards
 * intents straight to its own `AppViewModel`.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppIntentBus", exact = true)
object AppIntentBus {
    private val _intents = Channel<AppIntent>(Channel.BUFFERED)
    val intents: Flow<AppIntent> = _intents.receiveAsFlow()

    fun emit(intent: AppIntent) {
        _intents.trySend(intent)
    }
}
