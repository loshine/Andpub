package io.github.loshine.andpub.di

import io.github.loshine.andpub.data.market.ApiMarketPublisher
import io.github.loshine.andpub.data.repository.DefaultAndpubRepository
import io.github.loshine.andpub.domain.usecase.FetchMarketAppInfoUseCase
import io.github.loshine.andpub.platform.createLocalStateStore
import io.github.loshine.andpub.presentation.AndpubViewModel

fun createAndpubViewModel(): AndpubViewModel {
    val repository = DefaultAndpubRepository(createLocalStateStore())
    return AndpubViewModel(
        repository = repository,
        fetchMarketAppInfo = FetchMarketAppInfoUseCase(ApiMarketPublisher.createAll()),
    )
}
