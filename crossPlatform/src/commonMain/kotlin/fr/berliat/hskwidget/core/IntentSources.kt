package fr.berliat.hskwidget.core

import kotlinx.serialization.Serializable

@Serializable
enum class IntentSources {
    IN_APP,
    WIDGET,
    SHARE;
}
