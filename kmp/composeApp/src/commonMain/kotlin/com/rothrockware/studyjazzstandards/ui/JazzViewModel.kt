package com.rothrockware.studyjazzstandards.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Base VM: fire-and-forget toast messages collected into the app Snackbar. */
abstract class JazzViewModel : ViewModel() {
    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    protected fun toast(message: String) {
        _toasts.trySend(message)
    }
}
