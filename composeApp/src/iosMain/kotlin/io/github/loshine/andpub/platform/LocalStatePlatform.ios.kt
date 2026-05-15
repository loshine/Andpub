package io.github.loshine.andpub.platform

import androidx.room.Room
import io.github.loshine.andpub.data.local.AndpubDatabase
import io.github.loshine.andpub.data.local.LocalStateStore
import io.github.loshine.andpub.data.local.RoomLocalStateStore
import io.github.loshine.andpub.data.local.buildAndpubDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun createLocalStateStore(): LocalStateStore {
    val database = buildAndpubDatabase(
        Room.databaseBuilder<AndpubDatabase>(
            name = "${documentDirectory()}/andpub.db",
        )
    )
    return RoomLocalStateStore(database)
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
