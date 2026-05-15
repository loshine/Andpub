package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import org.koin.core.annotation.Factory

@Factory
class FetchMarketAppInfoUseCase(
    publishers: List<MarketPublisher>,
) {
    private val publisherMap = publishers.associateBy { it.marketType }

    suspend operator fun invoke(
        app: AppRecord,
        channel: ChannelRecord,
    ): Result<MarketAppInfo> {
        val publisher = publisherMap[channel.marketType]
            ?: return Result.failure(IllegalStateException("未找到市场适配器"))
        return publisher.fetchAppInfo(app, channel)
    }
}
