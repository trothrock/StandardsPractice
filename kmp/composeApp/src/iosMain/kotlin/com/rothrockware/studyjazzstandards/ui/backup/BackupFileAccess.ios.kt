package com.rothrockware.studyjazzstandards.ui.backup

// Not wired up on iOS yet — needs UIDocumentPickerViewController presented from
// the hosting UIViewController. Gated off via backupFileAccessSupported until built.
actual val backupFileAccessSupported: Boolean = false

actual suspend fun pickBackupFile(): String? = null

actual suspend fun saveBackupFile(suggestedFileName: String, content: String): Boolean = false
