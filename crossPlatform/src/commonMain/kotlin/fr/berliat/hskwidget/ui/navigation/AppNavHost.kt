package fr.berliat.hskwidget.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.withContext

import fr.berliat.hskwidget.domain.SearchQuery
import fr.berliat.hskwidget.core.IntentSources
import fr.berliat.hskwidget.ui.application.AppViewModel
import fr.berliat.hskwidget.ui.screens.OCR.CaptureImageScreen
import fr.berliat.hskwidget.ui.screens.reader.ReaderScreen
import fr.berliat.hskwidget.ui.screens.about.AboutScreen
import fr.berliat.hskwidget.ui.screens.annotate.AnnotateScreen
import fr.berliat.hskwidget.ui.screens.config.ConfigScreen
import fr.berliat.hskwidget.ui.screens.dictionary.DictionarySearchScreen
import fr.berliat.hskwidget.ui.screens.support.SupportScreen
import fr.berliat.hskwidget.ui.screens.widget.WidgetsListScreen
import fr.berliat.hskwidget.ui.screens.wordlist.WordListScreen
import fr.berliat.hskwidget.ui.components.LoadingView
import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.ui.screens.config.ConfigViewModel
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.absolutePath

import kotlin.reflect.typeOf

@Composable
fun AppNavHost(viewModel : AppViewModel) {
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current

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

            // A route's `search` is a one-shot command, consumed only when this
            // entry is first shown. Re-applying it on every recomposition (e.g.
            // coming back from Annotate) would bring back a stale query over
            // whatever the user typed or cleared in the AppBar meanwhile.
            var routeSearchApplied by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (routeSearchApplied) return@LaunchedEffect
                routeSearchApplied = true
                args.search?.let { raw ->
                    viewModel.appConfig.searchQuery.value = SearchQuery.processSearchQuery(raw)
                }
            }

            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            DictionarySearchScreen(
                initialIntentSource = args.source,
                onAnnotate = { word ->
                    navController.navigate(Screen.Annotate(word.simplified))
                },
                onSearchTerm = { term -> navController.navigate(
                    Screen.Dictionary(
                        SearchQuery.fromString(term).copy(exactWord = true).toString())
                    )
                },
                onNavigate = { screen -> navController.navigate(screen) }
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

            ConfigScreen(
                viewModel = ConfigViewModel(
                    appConfig = HSKAppServices.appPreferences,
                    widgetProvider = FlashcardWidgetProvider(),
                    ankiDelegate = HSKAppServices.ankiDelegate,
                    gDriveBackup = HSKAppServices.gDriveBackup,
                    onRestoreSuccess = { viewModel.navigationManager.navigate(Screen.Dictionary("")) }
                ),
            )
        }

        composable<Screen.OCRCapture> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.OCRCapture>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            CaptureImageScreen(onImageReady = { imageFile: PlatformFile ->
                navController.navigate(Screen.Reader(
                    preText = args.preText,
                    imageFilePath = imageFile.absolutePath(),
                    allowPicture = true,
                    couldAnnotate = false
                ))
            })
        }

        composable<Screen.Reader> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Reader>()
            LaunchedEffect(args) {
                viewModel.navigationManager.registerScreenVisit(args)
            }

            // File existence is disk I/O: never run it during composition (StrictMode
            // DiskReadViolation / main-thread jank). Resolve it on an IO dispatcher and
            // gate the screen until the check completes so we neither crash nor show a
            // spurious "no text" warning or start OCR on a missing file.
            val imageFilePath = args.imageFilePath
            var imageFile by remember(imageFilePath) { mutableStateOf<PlatformFile?>(null) }
            var isFileCheckDone by remember(imageFilePath) { mutableStateOf(imageFilePath == null) }

            LaunchedEffect(imageFilePath) {
                if (imageFilePath == null) {
                    imageFile = null
                    isFileCheckDone = true
                } else {
                    imageFile = withContext(AppDispatchers.IO) {
                        val f = PlatformFile(imageFilePath)
                        if (f.exists()) f else null
                    }
                    isFileCheckDone = true
                }
            }

            if (!isFileCheckDone) {
                LoadingView()
            } else {
                ReaderScreen(
                    preText = args.preText,
                    imageFile = imageFile,
                    allowPicture = args.allowPicture,
                    couldAnnotate = args.couldAnnotate,
                    onFavoriteClick = { word -> navController.navigate(Screen.Annotate(word.simplified)) },
                    onClickOCRAdd = { preText -> navController.navigate(Screen.OCRCapture(preText)) },
                    onSearchTerm = { query -> navController.navigate(Screen.Dictionary(SearchQuery.fromString(query).copy(exactWord = true).toString())) }
                )
            }
        }
    }
}
