package com.rothrockware.studyjazzstandards.data.store

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop storage: java.util.prefs.Preferences caps values at 8 KB, far
 * smaller than the database blob, so the JVM target persists to a file.
 */
class FileBlobStore(
    private val file: File = File(
        System.getProperty("user.home"),
        ".studyjazzstandards/$STORAGE_KEY.json",
    ),
) : BlobStore {

    override fun read(): String? =
        if (file.isFile) file.readText() else null

    override fun write(value: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(value)
        Files.move(
            tmp.toPath(), file.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
        )
    }

    override fun clear() {
        file.delete()
    }
}
