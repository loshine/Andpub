package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.MarketPublishResult
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.VivoPublishOptions
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RefreshPublishTaskStatusUseCaseTest : StringSpec({
    val app = AppRecord("app-1", "Demo", "com.example.app")
    val xiaomiChannel = channel("xiaomi", MarketType.Xiaomi)

    "unsupported refresh keeps non-vivo task status unchanged" {
        val useCase = RefreshPublishTaskStatusUseCase(
            listOf(
                object : MarketPublisher {
                    override val marketType: MarketType = MarketType.Xiaomi

                    override suspend fun fetchAppInfo(
                        app: AppRecord,
                        channel: ChannelRecord,
                    ): Result<MarketAppInfo> =
                        Result.failure(UnsupportedOperationException())
                }
            )
        )
        val task = task("task-xiaomi", xiaomiChannel, PublishTaskStatus.Ready)

        val result = useCase(
            app = app,
            channel = xiaomiChannel,
            task = task,
            vivoOptions = VivoPublishOptions(),
        )

        result.status shouldBe PublishTaskStatus.Ready
        result.logs.last().message shouldBe "小米应用市场 暂未接入发布状态查询"
    }

    "refresh failure from implemented publisher marks task failed" {
        val vivoChannel = channel("vivo", MarketType.Vivo)
        val useCase = RefreshPublishTaskStatusUseCase(
            listOf(
                object : MarketPublisher {
                    override val marketType: MarketType = MarketType.Vivo

                    override suspend fun fetchAppInfo(
                        app: AppRecord,
                        channel: ChannelRecord,
                    ): Result<MarketAppInfo> =
                        Result.failure(UnsupportedOperationException())

                    override suspend fun refreshPublishStatus(
                        request: MarketPublishRequest,
                    ): Result<MarketPublishResult> =
                        Result.failure(IllegalStateException("remote failed"))
                }
            )
        )

        val result = useCase(
            app = app,
            channel = vivoChannel,
            task = task("task-vivo", vivoChannel, PublishTaskStatus.Submitted),
            vivoOptions = VivoPublishOptions(),
        )

        result.status shouldBe PublishTaskStatus.Failed
        result.logs.last().message shouldBe "remote failed"
    }
})

private fun channel(
    id: String,
    marketType: MarketType,
): ChannelRecord =
    ChannelRecord(
        id = id,
        appId = "app-1",
        marketType = marketType,
        marketAppId = null,
        credentials = emptyMap(),
        extraFields = emptyMap(),
    )

private fun task(
    id: String,
    channel: ChannelRecord,
    status: PublishTaskStatus,
): PublishTaskRecord =
    PublishTaskRecord(
        id = id,
        appId = "app-1",
        channelId = channel.id,
        marketType = channel.marketType,
        publishMode = PublishMode.UnifiedArtifact,
        artifact = ArtifactDraft(),
        status = status,
        logs = emptyList(),
    )
