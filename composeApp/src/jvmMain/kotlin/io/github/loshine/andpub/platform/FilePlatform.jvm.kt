package io.github.loshine.andpub.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
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

actual suspend fun openTextFile(title: String): String? = withContext(Dispatchers.IO) {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter("JSON 文件 (*.json)", "json")
        isAcceptAllFileFilterUsed = true
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return@withContext null
    chooser.selectedFile.readText()
}
