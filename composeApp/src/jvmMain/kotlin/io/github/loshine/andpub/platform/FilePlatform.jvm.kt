package io.github.loshine.andpub.platform

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun saveTextFile(
    defaultFileName: String,
    content: String,
): String? = withContext(Dispatchers.IO) {
    var directory: String? = null
    var filename: String? = null
    EventQueue.invokeAndWait {
        FileDialog(null as Frame?, "导出应用设置", FileDialog.SAVE).also { dialog ->
            dialog.file = defaultFileName
            dialog.isVisible = true
            directory = dialog.directory
            filename = dialog.file
            dialog.dispose()
        }
    }
    val dir = directory ?: return@withContext null
    val file = filename ?: return@withContext null
    val target = File(dir, file)
    target.writeText(content)
    target.absolutePath
}

actual suspend fun openTextFile(title: String): String? = withContext(Dispatchers.IO) {
    var directory: String? = null
    var filename: String? = null
    EventQueue.invokeAndWait {
        FileDialog(null as Frame?, title, FileDialog.LOAD).also { dialog ->
            dialog.filenameFilter = FilenameFilter { _, name ->
                name.endsWith(".json", ignoreCase = true)
            }
            dialog.isVisible = true
            directory = dialog.directory
            filename = dialog.file
            dialog.dispose()
        }
    }
    val dir = directory ?: return@withContext null
    val file = filename ?: return@withContext null
    File(dir, file).readText()
}
