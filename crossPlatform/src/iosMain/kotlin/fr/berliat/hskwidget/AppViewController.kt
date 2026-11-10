package fr.berliat.hskwidget

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController

import fr.berliat.hskwidget.ui.application.AppView
import fr.berliat.hskwidget.ui.application.AppViewModel
import platform.UIKit.UIViewController

private val viewModel = AppViewModel()

fun AppViewController(): UIViewController = ComposeUIViewController(
		configure = {
			// This will prevent the crash by disabling the strict plist sanity check
			enforceStrictPlistSanityCheck = false
		}
	) {

	// Idempotent; kept out of the composition body so recompositions don't call it.
	LaunchedEffect(Unit) { viewModel.init() }
    // This is a composable context, so we can call our composable functions
    AppView(
        viewModel = viewModel
    )
}
