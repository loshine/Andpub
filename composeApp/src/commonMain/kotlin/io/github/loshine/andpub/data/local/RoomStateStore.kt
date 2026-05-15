package io.github.loshine.andpub.data.local

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface LocalStateStore {
    fun observe(): Flow<LocalStateSnapshot?>
    suspend fun load(): LocalStateSnapshot?
    suspend fun save(snapshot: LocalStateSnapshot)
}

@Entity
data class LocalStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface LocalStateDao {
    @Query("SELECT value FROM LocalStateEntity WHERE key = :key")
    fun observeValue(key: String): Flow<String?>

    @Query("SELECT value FROM LocalStateEntity WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putValue(entity: LocalStateEntity)
}

@Database(
    entities = [LocalStateEntity::class],
    version = 1,
)
@ConstructedBy(AndpubDatabaseConstructor::class)
abstract class AndpubDatabase : RoomDatabase() {
    abstract fun localStateDao(): LocalStateDao
}

@Suppress("KotlinNoActualForExpect")
expect object AndpubDatabaseConstructor : RoomDatabaseConstructor<AndpubDatabase> {
    override fun initialize(): AndpubDatabase
}

fun buildAndpubDatabase(
    builder: RoomDatabase.Builder<AndpubDatabase>,
): AndpubDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()

class RoomLocalStateStore(
    private val database: AndpubDatabase,
) : LocalStateStore {
    override fun observe(): Flow<LocalStateSnapshot?> =
        database.localStateDao()
            .observeValue(STATE_KEY)
            .map { value -> value?.let { json.decodeFromString<LocalStateSnapshot>(it) } }

    override suspend fun load(): LocalStateSnapshot? {
        val value = database.localStateDao().getValue(STATE_KEY) ?: return null
        return json.decodeFromString<LocalStateSnapshot>(value)
    }

    override suspend fun save(snapshot: LocalStateSnapshot) {
        database.localStateDao().putValue(
            LocalStateEntity(
                key = STATE_KEY,
                value = json.encodeToString(snapshot),
            )
        )
    }

    private companion object {
        const val STATE_KEY = "state"

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

class InMemoryLocalStateStore : LocalStateStore {
    private var snapshot: LocalStateSnapshot? = null
    private val snapshots = MutableStateFlow<LocalStateSnapshot?>(null)

    override fun observe(): Flow<LocalStateSnapshot?> = snapshots

    override suspend fun load(): LocalStateSnapshot? = snapshot

    override suspend fun save(snapshot: LocalStateSnapshot) {
        this.snapshot = snapshot
        snapshots.value = snapshot
    }
}
