package io.github.loshine.andpub.platform

import androidx.room.Room
import io.github.loshine.andpub.domain.storage.AndpubDatabase
import io.github.loshine.andpub.domain.storage.LocalStateStore
import io.github.loshine.andpub.domain.storage.RoomLocalStateStore
import io.github.loshine.andpub.domain.storage.buildAndpubDatabase
import java.io.File

actual fun createLocalStateStore(): LocalStateStore {
    val appDir = File(System.getProperty("user.home"), ".andpub")
    appDir.mkdirs()
    val database = buildAndpubDatabase(
        Room.databaseBuilder<AndpubDatabase>(
            name = File(appDir, "andpub.db").absolutePath,
        )
    )
    return RoomLocalStateStore(database)
}
