package com.rothrockware.studyjazzstandards.ui.backup

/** Whether this platform can show native open/save dialogs for backup files. */
expect val backupFileAccessSupported: Boolean

/** Shows a native "open" dialog and returns the picked file's text, or null if canceled. */
expect suspend fun pickBackupFile(): String?

/** Shows a native "save" dialog pre-filled with [suggestedFileName] and writes [content]. */
expect suspend fun saveBackupFile(suggestedFileName: String, content: String): Boolean
