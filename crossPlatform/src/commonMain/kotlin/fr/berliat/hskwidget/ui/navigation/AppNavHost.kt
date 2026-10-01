package fr.berliat.hskwidget.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.ui.application.AppViewModel
import fr.berliat.hskwidget.ui.screens.OCR.CaptureImageScreen
import fr.berliat.hskwidget.ui.screens.OCR.DisplayOCRScreen
import fr.berliat.hskwidget.ui.screens.about.AboutScreen
import fr.berliat.hskwidget.ui.screens.annotate.AnnotateScreen
import fr.berliat.hskwidget.ui.screens.config.ConfigScreen
import fr.berliat.hskwidget.ui.screens.dictionary.DictionarySearchScreen
import fr.berliat.hskwidget.ui.screens.support.SupportScreen
import fr.berliat.hskwidget.ui.screens.widget.WidgetsListScreen
import fr.berliat.hskwidget.ui.screens.wordlist.WordListScreen

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.absolutePath

import kotlin.reflect.typeOf

@Composable
fun AppNavHost(viewModel : AppViewModel) {
    val navController = rememberNavController()
    val isViewModelReady by viewModel.isReady.collectAsState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(navController) {
        viewModel.navigationManager.navigationEvents.collect { route ->
            // Only clear focus if we are navigating to a screen other than the Dictionary.
            // This prevents the keyboard from dismissing during live search updates or
            // when navigating to results while typing.
            if (route !is Screen.Dictionary) {
                focusManager.clearFocus()
            }
            navController.navigate(route)
        }
    }

    NavHost(navController = navController, startDestination = Screen.Dictionary()) {
        composable<Screen.Dictionary>(
            typeMap = mapOf(typeOf<IntentSources>() to IntentSourcesNavType)
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Dictionary>()

            // Queue the search update until the viewModel is ready
            LaunchedEffect(args.search, isViewModelReady) {
                if (isViewModelReady && args.search != null) {
                    val currentSearch = viewModel.appConfig.searchQuery.value.toString()
                    if (args.search != currentSearch) {
                         viewModel.appConfig.searchQuery.value =
                            SearchQuery.processSearchQuery(args.search)
                    }
                }
            }

            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            DictionarySearchScreen(
                initialIntentSource = args.source,
                onAnnotate = { word ->
                    navController.navigate(Screen.Annotate(word))
                }
            )
        }

        composable<Screen.Annotate> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Annotate>()

            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            AnnotateScreen(
                word = args.simplifiedWord,
                onSaveSuccess = { navController.popBackStack() },
                onDeleteSuccess = { navController.popBackStack() },
            )
        }

        composable<Screen.Lists> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Lists>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            WordListScreen(
                onClickList = { list ->
                    val sq = SearchQuery(inListName = list.name)
                    navController.navigate(Screen.Dictionary(sq.toString()))
                }
            )
        }

        composable<Screen.Support> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Support>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            SupportScreen()
        }

        composable<Screen.About> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.About>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            AboutScreen()
        }

        composable<Screen.Widgets> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Widgets>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            WidgetsListScreen(
                selectedWidgetId = args.widgetId,
                onWidgetPreferenceSaved = {
                    if (args.expectsActivityResult && args.widgetId != null) {
                        viewModel.finalizeWidgetConfiguration(args.widgetId)
                    }
                },
                expectsActivityResult = args.expectsActivityResult
            )
        }

        composable<Screen.Config> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Config>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            ConfigScreen()
        }

        composable<Screen.OCRCapture> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.OCRCapture>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            CaptureImageScreen(onImageReady = { imageFile: PlatformFile ->
                navController.navigate(Screen.OCRDisplay(
                    preText = args.preText,
                    imageFilePath = imageFile.absolutePath()
                ))
            })
        }

        composable<Screen.OCRDisplay> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.OCRDisplay>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            val imageFile = args.imageFilePath?.let {
                val f = PlatformFile(it)
                if (f.exists()) {
                    f
                } else {
                    null
                }
            }

            DisplayOCRScreen(
                preText = args.preText,
                imageFile = imageFile,
                onFavoriteClick = { word -> navController.navigate(Screen.Annotate(word.simplified)) },
                onClickOCRAdd = { preText -> navController.navigate(Screen.OCRCapture(preText)) }
            )
        }
    }
}
