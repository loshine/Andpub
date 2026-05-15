package io.github.loshine.andpub.platform

import android.content.Context
import androidx.room.Room
import io.github.loshine.andpub.data.local.AndpubDatabase
import io.github.loshine.andpub.data.local.InMemoryLocalStateStore
import io.github.loshine.andpub.data.local.LocalStateStore
import io.github.loshine.andpub.data.local.RoomLocalStateStore
import io.github.loshine.andpub.data.local.buildAndpubDatabase

private var appContext: Context? = null

fun initAndpubAndroidContext(context: Context) {
    appContext = context.applicationContext
}

actual fun createLocalStateStore(): LocalStateStore {
    val context = appContext ?: return InMemoryLocalStateStore()
    val database = buildAndpubDatabase(
        Room.databaseBuilder<AndpubDatabase>(
            context = context,
            name = context.getDatabasePath("andpub.db").absolutePath,
        )
    )
    return RoomLocalStateStore(database)
}
