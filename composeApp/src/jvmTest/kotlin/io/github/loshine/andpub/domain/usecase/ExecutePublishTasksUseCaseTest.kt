package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.MarketPublishResult
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.VivoPublishOptions
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ExecutePublishTasksUseCaseTest : StringSpec({
    val app = AppRecord("app-1", "Demo", "com.example.app")
    val vivoChannel = channel("vivo", MarketType.Vivo)
    val xiaomiChannel = channel("xiaomi", MarketType.Xiaomi)

    "publish orchestration keeps other channel tasks visible when vivo fails" {
        val useCase = ExecutePublishTasksUseCase(
            listOf(
                fakePublisher(MarketType.Vivo) { Result.failure(IllegalStateException("vivo failed")) },
                fakePublisher(MarketType.Xiaomi) {
                    Result.success(MarketPublishResult(status = PublishTaskStatus.Ready, logs = emptyList()))
                },
            )
        )

        val result = useCase(
            app = app,
            channels = listOf(vivoChannel, xiaomiChannel),
            tasks = listOf(task("task-vivo", vivoChannel), task("task-xiaomi", xiaomiChannel)),
            vivoOptions = VivoPublishOptions(),
        )

        result shouldHaveSize 2
        result.first { it.id == "task-vivo" }.status shouldBe PublishTaskStatus.Failed
        result.first { it.id == "task-xiaomi" }.status shouldBe PublishTaskStatus.Ready
    }

    "single publish keeps task logs and applies submitted publisher result" {
        val useCase = ExecutePublishTasksUseCase(
            listOf(
                fakePublisher(MarketType.Vivo) {
                    Result.success(
                        MarketPublishResult(
                            status = PublishTaskStatus.Submitted,
                            logs = listOf(
                                PublishTaskLog(LogLevel.Info, "submitted", PublishTaskStage.Submit),
                            ),
                            vendorTaskId = "task-1",
                            vendorUploadIds = listOf("serial-1"),
                            environment = "测试环境",
                        )
                    )
                },
            )
        )

        val result = useCase(
            app = app,
            channel = vivoChannel,
            task = task("task-vivo", vivoChannel),
            vivoOptions = VivoPublishOptions(),
        )

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorTaskId shouldBe "task-1"
        result.vendorUploadIds shouldBe listOf("serial-1")
        result.logs.last().message shouldBe "submitted"
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
): PublishTaskRecord =
    PublishTaskRecord(
        id = id,
        appId = "app-1",
        channelId = channel.id,
        marketType = channel.marketType,
        publishMode = PublishMode.UnifiedArtifact,
        artifact = ArtifactDraft(),
        status = PublishTaskStatus.Ready,
        logs = emptyList(),
    )

private fun fakePublisher(
    type: MarketType,
    publishResult: suspend (MarketPublishRequest) -> Result<MarketPublishResult>,
): MarketPublisher =
    object : MarketPublisher {
        override val marketType: MarketType = type

        override suspend fun fetchAppInfo(
            app: AppRecord,
            channel: ChannelRecord,
        ): Result<MarketAppInfo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun publish(request: MarketPublishRequest): Result<MarketPublishResult> =
            publishResult(request)
    }
