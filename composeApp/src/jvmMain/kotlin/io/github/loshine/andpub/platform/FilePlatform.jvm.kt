package io.github.loshine.andpub.platform

import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun saveTextFile(
    defaultFileName: String,
    content: String,
): String? = withContext(Dispatchers.IO) {
    val chooser = JFileChooser().apply {
        dialogTitle = "导出应用设置"
        selectedFile = File(defaultFileName)
    }
    val result = chooser.showSaveDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) {
        return@withContext null
    }
    chooser.selectedFile.writeText(content)
    chooser.selectedFile.absolutePath
}
