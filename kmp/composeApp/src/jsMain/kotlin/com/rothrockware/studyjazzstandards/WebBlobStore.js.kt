package com.rothrockware.studyjazzstandards

import com.rothrockware.studyjazzstandards.data.store.BlobStore
import com.rothrockware.studyjazzstandards.data.store.SettingsBlobStore
import com.russhwolf.settings.StorageSettings

actual fun webBlobStore(): BlobStore = SettingsBlobStore(StorageSettings())
