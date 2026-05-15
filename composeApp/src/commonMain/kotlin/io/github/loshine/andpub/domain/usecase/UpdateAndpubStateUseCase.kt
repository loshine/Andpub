package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.github.loshine.andpub.domain.repository.AndpubRepository

class UpdateAndpubStateUseCase(
    private val repository: AndpubRepository,
) {
    suspend operator fun invoke(transform: (LocalStateSnapshot) -> LocalStateSnapshot) {
        repository.update(transform)
    }
}
