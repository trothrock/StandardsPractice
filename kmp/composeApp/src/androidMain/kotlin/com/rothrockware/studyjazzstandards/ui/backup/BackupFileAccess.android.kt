package com.rothrockware.studyjazzstandards.ui.backup

// Not wired up on Android yet — needs a SAF document-picker launcher tied to the
// hosting Activity. Gated off via backupFileAccessSupported until that's built.
actual val backupFileAccessSupported: Boolean = false

actual suspend fun pickBackupFile(): String? = null

actual suspend fun saveBackupFile(suggestedFileName: String, content: String): Boolean = false
