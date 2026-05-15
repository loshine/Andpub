package io.github.loshine.andpub.di

import io.github.loshine.andpub.data.local.LocalStateStore
import io.github.loshine.andpub.data.market.HonorMarketPublisher
import io.github.loshine.andpub.data.market.HuaweiMarketPublisher
import io.github.loshine.andpub.data.market.OppoMarketPublisher
import io.github.loshine.andpub.data.market.TencentMarketPublisher
import io.github.loshine.andpub.data.market.VivoMarketPublisher
import io.github.loshine.andpub.data.market.XiaomiMarketPublisher
import io.github.loshine.andpub.data.remote.honor.HonorRemoteDataSource
import io.github.loshine.andpub.data.remote.huawei.HuaweiRemoteDataSource
import io.github.loshine.andpub.data.remote.oppo.OppoRemoteDataSource
import io.github.loshine.andpub.data.remote.tencent.TencentRemoteDataSource
import io.github.loshine.andpub.data.remote.vivo.VivoRemoteDataSource
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiRemoteDataSource
import io.github.loshine.andpub.data.repository.DefaultAndpubRepository
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.repository.AndpubRepository
import io.github.loshine.andpub.domain.usecase.FetchMarketAppInfoUseCase
import io.github.loshine.andpub.network.httpClient
import io.github.loshine.andpub.platform.createLocalStateStore
import io.github.loshine.andpub.presentation.AndpubViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<HttpClient> { httpClient() }
    single<LocalStateStore> { createLocalStateStore() }
    single<AndpubRepository> { DefaultAndpubRepository(get()) }

    single { HuaweiRemoteDataSource(get()) }
    single { HonorRemoteDataSource(get()) }
    single { XiaomiRemoteDataSource(get()) }
    single { OppoRemoteDataSource(get()) }
    single { VivoRemoteDataSource(get()) }
    single { TencentRemoteDataSource(get()) }

    single { HuaweiMarketPublisher(get()) }
    single { HonorMarketPublisher(get()) }
    single { XiaomiMarketPublisher(get()) }
    single { OppoMarketPublisher(get()) }
    single { VivoMarketPublisher(get()) }
    single { TencentMarketPublisher(get()) }
    single<List<MarketPublisher>> {
        listOf(
            get<HuaweiMarketPublisher>(),
            get<HonorMarketPublisher>(),
            get<XiaomiMarketPublisher>(),
            get<OppoMarketPublisher>(),
            get<VivoMarketPublisher>(),
            get<TencentMarketPublisher>(),
        )
    }

    factory { FetchMarketAppInfoUseCase(get()) }
    viewModel { AndpubViewModel(get(), get()) }
}
