package fr.berliat.hskwidget.ui.navigation

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.bakery_dining_24px
import fr.berliat.hskwidget.format_list_bulleted_add_24px
import fr.berliat.hskwidget.ic_dictionary_24dp
import fr.berliat.hskwidget.menu_dictionary
import fr.berliat.hskwidget.menu_lists
import fr.berliat.hskwidget.baseline_info_24
import fr.berliat.hskwidget.baseline_widgets_24
import fr.berliat.hskwidget.menu_about
import fr.berliat.hskwidget.menu_ocr
import fr.berliat.hskwidget.menu_settings
import fr.berliat.hskwidget.menu_widgets
import fr.berliat.hskwidget.photo_camera_24px
import fr.berliat.hskwidget.settings_24px

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

@Serializable
sealed class Screen() {
    @Serializable
    data class Dictionary(
        val search: String? = null,
        val source: IntentSources = IntentSources.IN_APP
    ): Screen()

    @Serializable
    data object Lists : Screen()

    @Serializable
    data class Annotate(val simplifiedWord: String): Screen()

    @Serializable
    data class Widgets(val widgetId: Int? = null, val expectsActivityResult: Boolean = false): Screen()

    @Serializable
    data object Config : Screen()

    @Serializable
    data object About : Screen()

    @Serializable
    data object Support : Screen()

    @Serializable
    data class OCRCapture(val preText: String = "") : Screen()

    @Serializable
    data class OCRDisplay(val preText: String = "", val imageFilePath: String? = null) : Screen()

    fun decoratedScreen(): DecoratedScreen {
        return DecoratedScreen.fromScreen(this)
    }
}

@Composable
expect fun supportTitle(): String

sealed class DecoratedScreen(val screen: Screen,
                             val title: @Composable () -> String,
                             val icon: DrawableResource,
                             val disableDrawer: Boolean = false) {
    data object Dictionary: DecoratedScreen(
        Screen.Dictionary(),
        { stringResource(Res.string.menu_dictionary) },
        Res.drawable.ic_dictionary_24dp)

    data object Annotate: DecoratedScreen(
        Screen.Annotate(""),
        { stringResource(Res.string.menu_dictionary) },
        Res.drawable.ic_dictionary_24dp)

    data object Lists : DecoratedScreen(
        Screen.Lists,
        { stringResource(Res.string.menu_lists) },
        Res.drawable.format_list_bulleted_add_24px)

    data object Widgets: DecoratedScreen(
        Screen.Widgets(),
        { stringResource(Res.string.menu_widgets) },
        Res.drawable.baseline_widgets_24)

    data object Config : DecoratedScreen(
        Screen.Config,
        { stringResource(Res.string.menu_settings) },
        Res.drawable.settings_24px)

    data object Support : DecoratedScreen(
        Screen.Support,
        { supportTitle() },
        Res.drawable.bakery_dining_24px)

    data object About : DecoratedScreen(
        Screen.About,
        { stringResource(Res.string.menu_about) },
        Res.drawable.baseline_info_24)

    data object OCRCapture : DecoratedScreen(
        Screen.OCRCapture(),
        { stringResource(Res.string.menu_ocr) },
        Res.drawable.photo_camera_24px,
        disableDrawer = true)

    data object OCRDisplay : DecoratedScreen(
        Screen.OCRDisplay(),
        { stringResource(Res.string.menu_ocr) },
        Res.drawable.photo_camera_24px)

    companion object {
        fun fromScreen(screen: Screen): DecoratedScreen = when(screen) {
            is Screen.About -> About
            is Screen.Support -> Support
            is Screen.Annotate -> Annotate
            is Screen.Config -> Config
            is Screen.Dictionary -> Dictionary
            is Screen.Lists -> Lists
            is Screen.OCRCapture -> OCRCapture
            is Screen.OCRDisplay -> OCRDisplay
            is Screen.Widgets -> Widgets
        }
    }
}