package io.github.loshine.andpub.di

import io.github.loshine.andpub.data.local.LocalStateStore
import io.github.loshine.andpub.data.market.HonorMarketPublisher
import io.github.loshine.andpub.data.market.HuaweiMarketPublisher
import io.github.loshine.andpub.data.market.OppoMarketPublisher
import io.github.loshine.andpub.data.market.TencentMarketPublisher
import io.github.loshine.andpub.data.market.VivoMarketPublisher
import io.github.loshine.andpub.data.market.XiaomiMarketPublisher
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.platform.createLocalStateStore
import io.ktor.client.HttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        NetworkModule::class,
        RemoteDataSourceModule::class,
        RepositoryModule::class,
        MarketPublisherModule::class,
        ViewModelModule::class,
    ]
)
@ComponentScan
class AppModule

@Module
class NetworkModule {
    @Single
    fun httpClient(): HttpClient = httpClient()
}

@Module
@ComponentScan("io.github.loshine.andpub.data.remote")
class RemoteDataSourceModule

@Module
class RepositoryModule {
    @Single
    fun localStateStore(): LocalStateStore = createLocalStateStore()
}

@Module
@ComponentScan("io.github.loshine.andpub.data.market")
class MarketPublisherModule {
    @Single
    fun marketPublishers(
        huawei: HuaweiMarketPublisher,
        honor: HonorMarketPublisher,
        xiaomi: XiaomiMarketPublisher,
        oppo: OppoMarketPublisher,
        vivo: VivoMarketPublisher,
        tencent: TencentMarketPublisher,
    ): List<MarketPublisher> = listOf(huawei, honor, xiaomi, oppo, vivo, tencent)
}

@KoinApplication
class AndpubKoinApplication
