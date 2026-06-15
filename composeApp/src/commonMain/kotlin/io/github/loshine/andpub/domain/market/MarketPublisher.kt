package io.github.loshine.andpub.domain.market

import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.MarketPublishResult
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.LogLevel

interface MarketPublisher {
    val marketType: MarketType

    suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo>

    suspend fun publish(request: MarketPublishRequest): Result<MarketPublishResult> =
        Result.success(
            MarketPublishResult(
                status = PublishTaskStatus.Ready,
                logs = listOf(
                    PublishTaskLog(
                        level = LogLevel.Info,
                        message = "${request.channel.marketType.displayName} 暂未接入厂商发布，当前仅完成下载和校验",
                        stage = PublishTaskStage.Submit,
                    )
                ),
            )
        )

    suspend fun refreshPublishStatus(request: MarketPublishRequest): Result<MarketPublishResult> =
        Result.failure(UnsupportedOperationException("${request.channel.marketType.displayName} 暂未接入发布状态查询"))
}
