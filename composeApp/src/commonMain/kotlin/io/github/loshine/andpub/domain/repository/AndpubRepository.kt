package io.github.loshine.andpub.domain.repository

import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import kotlinx.coroutines.flow.Flow

interface AndpubRepository {
    val snapshots: Flow<LocalStateSnapshot>

    suspend fun update(transform: (LocalStateSnapshot) -> LocalStateSnapshot)
}
