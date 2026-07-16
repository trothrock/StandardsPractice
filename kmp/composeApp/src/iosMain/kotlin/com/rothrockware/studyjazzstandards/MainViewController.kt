package com.rothrockware.studyjazzstandards

import androidx.compose.ui.window.ComposeUIViewController
import com.rothrockware.studyjazzstandards.data.store.SettingsBlobStore
import com.rothrockware.studyjazzstandards.di.AppContainer
import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSUserDefaults

private val container by lazy {
    AppContainer(SettingsBlobStore(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)))
}

fun MainViewController() = ComposeUIViewController { App(container) }
