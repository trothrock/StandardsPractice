package com.rothrockware.studyjazzstandards.data.store

import com.russhwolf.settings.Settings

/**
 * A single string slot holding the whole serialized database — the KMP
 * equivalent of the web app's one localStorage key.
 *
 * Abstracted (rather than using Settings directly) because the JVM
 * Preferences backend caps values at 8 KB, so desktop uses a file instead.
 */
interface BlobStore {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

const val STORAGE_KEY: String = "jazz_sr_v3"

class SettingsBlobStore(
    private val settings: Settings,
    private val key: String = STORAGE_KEY,
) : BlobStore {
    override fun read(): String? = settings.getStringOrNull(key)
    override fun write(value: String) = settings.putString(key, value)
    override fun clear() = settings.remove(key)
}

/** In-memory store for tests and previews. */
class InMemoryBlobStore(private var value: String? = null) : BlobStore {
    override fun read(): String? = value
    override fun write(value: String) {
        this.value = value
    }
    override fun clear() {
        value = null
    }
}
