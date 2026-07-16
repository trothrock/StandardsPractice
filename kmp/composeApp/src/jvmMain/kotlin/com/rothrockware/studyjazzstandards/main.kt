package com.rothrockware.studyjazzstandards

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.rothrockware.studyjazzstandards.data.store.FileBlobStore
import com.rothrockware.studyjazzstandards.di.AppContainer

fun main() {
    val container = AppContainer(FileBlobStore())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Jazz Standards Practice",
        ) {
            App(container)
        }
    }
}
