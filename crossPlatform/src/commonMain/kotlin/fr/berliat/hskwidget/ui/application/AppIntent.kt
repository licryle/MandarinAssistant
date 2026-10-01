package fr.berliat.hskwidget.ui.application

import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.domain.SearchQuery
import io.github.vinceglb.filekit.PlatformFile

sealed class AppIntent {
    data class Search(val query: SearchQuery, val source: IntentSources = IntentSources.IN_APP) : AppIntent()
    data class SearchTTS(val query: SearchQuery, val source: IntentSources = IntentSources.IN_APP) : AppIntent()
    data class WidgetConfiguration(val widgetId: Int) : AppIntent()
    data class ImageOCR(val file: PlatformFile, val source: IntentSources = IntentSources.SHARE) : AppIntent() {
        constructor(path: String, source: IntentSources = IntentSources.SHARE) : this(PlatformFile(path), source)
    }
    data object OCRCapture : AppIntent()
}
