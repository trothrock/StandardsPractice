package com.rothrockware.studyjazzstandards.ui.backup

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual val backupFileAccessSupported: Boolean = true

actual suspend fun pickBackupFile(): String? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Import Backup", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".json") }
    dialog.isVisible = true
    val dir = dialog.directory
    val name = dialog.file
    if (dir == null || name == null) null else File(dir, name).readText()
}

actual suspend fun saveBackupFile(suggestedFileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Export Backup", FileDialog.SAVE)
    dialog.file = suggestedFileName
    dialog.isVisible = true
    val dir = dialog.directory
    var name = dialog.file
    if (dir == null || name == null) {
        false
    } else {
        if (!name.endsWith(".json")) name += ".json"
        File(dir, name).writeText(content)
        true
    }
}
