package io.github.loshine.andpub.platform

actual suspend fun saveTextFile(
    defaultFileName: String,
    content: String,
): String? =
    throw UnsupportedOperationException("iOS 端暂未接入文件导出")
