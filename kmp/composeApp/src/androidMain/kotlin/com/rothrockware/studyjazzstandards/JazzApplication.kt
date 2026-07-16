package com.rothrockware.studyjazzstandards

import android.app.Application
import android.content.Context
import com.rothrockware.studyjazzstandards.data.store.SettingsBlobStore
import com.rothrockware.studyjazzstandards.di.AppContainer
import com.russhwolf.settings.SharedPreferencesSettings

class JazzApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            SettingsBlobStore(
                SharedPreferencesSettings(
                    getSharedPreferences("jazz_sr", Context.MODE_PRIVATE),
                ),
            ),
        )
    }
}
