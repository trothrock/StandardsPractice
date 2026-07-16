package com.rothrockware.studyjazzstandards.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.rothrockware.studyjazzstandards.ui.JazzViewModel

val LocalSnackbarHost = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}

/** Routes a view model's toast channel into the app-level snackbar. */
@Composable
fun CollectToasts(vm: JazzViewModel) {
    val host = LocalSnackbarHost.current
    LaunchedEffect(vm) {
        vm.toasts.collect { host.showSnackbar(it) }
    }
}
