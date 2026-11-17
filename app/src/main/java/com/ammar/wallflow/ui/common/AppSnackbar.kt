package com.ammar.wallflow.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Host for in-app snackbars. Provided once at the activity root so messages show on every
 * screen, including full-screen destinations. Use it for transient feedback that is shown
 * while no bottom sheet or dialog is open; use a toast for feedback that must stay visible
 * above a modal sheet.
 */
val LocalSnackbarHostState: ProvidableCompositionLocal<SnackbarHostState> =
    staticCompositionLocalOf { SnackbarHostState() }

/** Shows [message], replacing any snackbar that is currently visible. */
fun SnackbarHostState.showMessage(scope: CoroutineScope, message: String) {
    scope.launch { showSnackbar(message) }
}
