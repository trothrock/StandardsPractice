package com.rothrockware.studyjazzstandards

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "StudyJazzStandards",
    ) {
        App()
    }
}