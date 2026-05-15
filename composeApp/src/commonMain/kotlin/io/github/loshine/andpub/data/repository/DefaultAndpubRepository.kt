package io.github.loshine.andpub.data.repository

import io.github.loshine.andpub.data.local.LocalStateStore
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.github.loshine.andpub.domain.repository.AndpubRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single(binds = [AndpubRepository::class])
class DefaultAndpubRepository(
    private val localStateStore: LocalStateStore,
) : AndpubRepository {
    private val mutex = Mutex()

    override val snapshots: Flow<LocalStateSnapshot> =
        localStateStore.observe().map { it ?: LocalStateSnapshot() }

    override suspend fun update(transform: (LocalStateSnapshot) -> LocalStateSnapshot) {
        mutex.withLock {
            val current = localStateStore.load() ?: LocalStateSnapshot()
            localStateStore.save(transform(current))
        }
    }
}
