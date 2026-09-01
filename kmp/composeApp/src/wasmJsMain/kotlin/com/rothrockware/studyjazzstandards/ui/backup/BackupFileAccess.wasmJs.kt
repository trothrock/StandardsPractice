package com.rothrockware.studyjazzstandards.ui.backup

// Not wired up on the Compose/Web target yet — needs a browser file input and
// Blob download. Gated off via backupFileAccessSupported until built.
actual val backupFileAccessSupported: Boolean = false

actual suspend fun pickBackupFile(): String? = null

actual suspend fun saveBackupFile(suggestedFileName: String, content: String): Boolean = false
