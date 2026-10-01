package fr.berliat.hskwidget.ui.screens.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import fr.berliat.hskwidget.core.Logging
import fr.berliat.hskwidget.core.Utils
import fr.berliat.hskwidget.core.Utils.BackgroundRestrictionType
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import kotlin.time.Duration.Companion.milliseconds

open class WidgetsListViewModel: ViewModel() {
    private val _widgetIds = MutableStateFlow<List<Int>>(emptyList())
    val widgetIds: StateFlow<List<Int>> = _widgetIds

    private val _showAddWidgetInstructions = MutableStateFlow(false)
    val showAddWidgetInstructions: StateFlow<Boolean> = _showAddWidgetInstructions.asStateFlow()

    private val _backgroundRestriction = MutableStateFlow(BackgroundRestrictionType.NO_RESTRICTION)
    val backgroundRestriction: StateFlow<BackgroundRestrictionType> = _backgroundRestriction.asStateFlow()

    init {
        viewModelScope.launch {
            val provider = FlashcardWidgetProvider()
            while (true) {
                val ids = provider.getWidgetIds()
                _widgetIds.value = ids
                delay(500.milliseconds)
            }
        }
        updateBatteryOptimizationStatus()
    }

    fun speakWord(word: String) = Utils.playWordInBackground(word)

    fun addNewWidget() {
        val supported = Utils.attemptAddDesktopWidget()
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.WIDGET_ADD_ATTEMPT,
            mapOf("SUPPORTED" to supported.toString())
        )
        if (!supported) {
            _showAddWidgetInstructions.value = true
        }
    }

    fun dismissAddWidgetInstructions() {
        _showAddWidgetInstructions.value = false
    }

    fun updateBatteryOptimizationStatus() {
        _backgroundRestriction.value = Utils.isBackgroundRestricted()
    }

    fun fixBatteryOptimization() {
        Logging.logAnalyticsEvent(
            Logging.ANALYTICS_EVENTS.WIDGET_BATTERY_FIX_CLICK,
            mapOf("RESTRICTION" to _backgroundRestriction.value.name)
        )
        Utils.openBatteryOptimizationSettings()
    }
}
