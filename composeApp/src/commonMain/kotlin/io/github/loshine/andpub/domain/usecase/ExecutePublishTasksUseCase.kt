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
class ExecutePublishTasksUseCase(
    publishers: List<MarketPublisher>,
) {
    private val publisherMap = publishers.associateBy { it.marketType }

    suspend operator fun invoke(
        app: AppRecord,
        channels: List<ChannelRecord>,
        tasks: List<PublishTaskRecord>,
        vivoOptions: VivoPublishOptions,
        onTaskLog: (String, PublishTaskLog) -> Unit = { _, _ -> },
    ): List<PublishTaskRecord> {
        val channelMap = channels.associateBy { it.id }
        return tasks.map { task ->
            val channel = channelMap[task.channelId]
            when {
                task.status == PublishTaskStatus.Failed -> task
                channel == null -> task.failed("渠道不存在")
                else -> invoke(app, channel, task, vivoOptions) { log -> onTaskLog(task.id, log) }
            }
        }
    }

    suspend operator fun invoke(
        app: AppRecord,
        channel: ChannelRecord,
        task: PublishTaskRecord,
        vivoOptions: VivoPublishOptions,
        onTaskLog: (PublishTaskLog) -> Unit = {},
    ): PublishTaskRecord {
        if (task.status == PublishTaskStatus.Failed) return task
        val publisher = publisherMap[channel.marketType]
            ?: return task.failed("未找到市场适配器")
        return publisher.publish(
            MarketPublishRequest(
                app = app,
                channel = channel,
                task = task,
                vivoOptions = vivoOptions,
                onLog = onTaskLog,
            )
        ).fold(
            onSuccess = { result ->
                task.copy(
                    status = result.status,
                    logs = task.logs + result.logs,
                    vendorTaskId = result.vendorTaskId,
                    vendorUploadIds = result.vendorUploadIds,
                    publishEnvironment = result.environment,
                )
            },
            onFailure = {
                task.copy(
                    status = PublishTaskStatus.Failed,
                    logs = task.logs + PublishTaskLog(
                        level = LogLevel.Error,
                        message = it.message ?: "发布失败",
                        stage = PublishTaskStage.Result,
                    ),
                )
            },
        )
    }

    private fun PublishTaskRecord.failed(message: String): PublishTaskRecord =
        copy(
            status = PublishTaskStatus.Failed,
            logs = logs + PublishTaskLog(
                level = LogLevel.Error,
                message = message,
                stage = PublishTaskStage.Result,
            ),
        )
}
