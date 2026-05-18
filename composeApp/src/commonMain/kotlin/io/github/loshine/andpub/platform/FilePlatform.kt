package io.github.loshine.andpub.platform

expect suspend fun saveTextFile(
    defaultFileName: String,
    content: String,
): String?
