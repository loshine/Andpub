package io.github.loshine.andpub.domain.market

import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType

interface MarketPublisher {
    val marketType: MarketType

    suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo>
}
