package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.VivoPublishOptions
import org.koin.core.annotation.Factory

@Factory
class RefreshPublishTaskStatusUseCase(
    publishers: List<MarketPublisher>,
) {
    private val publisherMap = publishers.associateBy { it.marketType }

    suspend operator fun invoke(
        app: AppRecord,
        channel: ChannelRecord,
        task: PublishTaskRecord,
        vivoOptions: VivoPublishOptions,
    ): PublishTaskRecord {
        val publisher = publisherMap[channel.marketType]
            ?: return task.failed("未找到市场适配器")
        return publisher.refreshPublishStatus(
            MarketPublishRequest(
                app = app,
                channel = channel,
                task = task,
                vivoOptions = vivoOptions,
            )
        ).fold(
            onSuccess = { result ->
                task.copy(
                    status = result.status,
                    logs = task.logs + result.logs,
                    vendorTaskId = result.vendorTaskId ?: task.vendorTaskId,
                    vendorUploadIds = result.vendorUploadIds.ifEmpty { task.vendorUploadIds },
                    publishEnvironment = result.environment ?: task.publishEnvironment,
                )
            },
            onFailure = {
                if (it is UnsupportedOperationException) {
                    task.withInfo(it.message ?: "当前市场暂未接入发布状态查询")
                } else {
                    task.failed(it.message ?: "刷新发布状态失败")
                }
            },
        )
    }

    private fun PublishTaskRecord.withInfo(message: String): PublishTaskRecord =
        copy(
            logs = logs + PublishTaskLog(LogLevel.Info, message, PublishTaskStage.Result),
        )

    private fun PublishTaskRecord.failed(message: String): PublishTaskRecord =
        copy(
            status = PublishTaskStatus.Failed,
            logs = logs + PublishTaskLog(LogLevel.Error, message, PublishTaskStage.Result),
        )
}
