package com.rothrockware.studyjazzstandards.di

import androidx.compose.runtime.staticCompositionLocalOf
import com.rothrockware.studyjazzstandards.data.DefaultJazzRepository
import com.rothrockware.studyjazzstandards.data.JazzRepository
import com.rothrockware.studyjazzstandards.data.store.BlobStore

/** Manual constructor injection — one shared repository per app instance. */
class AppContainer(store: BlobStore) {
    val repository: JazzRepository = DefaultJazzRepository(store)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
