package io.github.loshine.andpub.platform

expect suspend fun saveTextFile(
    defaultFileName: String,
    content: String,
): String?

/**
 * Opens a system file picker, returns the selected file content as text,
 * or null if the user cancelled.
 */
expect suspend fun openTextFile(title: String = "选择文件"): String?
