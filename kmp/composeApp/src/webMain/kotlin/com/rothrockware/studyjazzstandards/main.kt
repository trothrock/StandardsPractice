package com.rothrockware.studyjazzstandards

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.rothrockware.studyjazzstandards.di.AppContainer

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = AppContainer(webBlobStore())
    ComposeViewport {
        App(container)
    }
}
