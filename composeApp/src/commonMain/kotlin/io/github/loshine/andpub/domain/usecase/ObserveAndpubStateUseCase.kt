package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.repository.AndpubRepository

class ObserveAndpubStateUseCase(
    private val repository: AndpubRepository,
) {
    operator fun invoke() = repository.snapshots
}
