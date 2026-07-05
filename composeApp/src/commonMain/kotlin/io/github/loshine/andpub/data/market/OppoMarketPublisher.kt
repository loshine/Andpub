package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.oppo.OppoRemoteDataSource
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.MarketPublishResult
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.platform.readBinaryFile
import org.koin.core.annotation.Single

@Single
class OppoMarketPublisher(
    private val remote: OppoRemoteDataSource,
    private val readFile: suspend (String) -> Result<ByteArray> = ::readBinaryFile,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Oppo

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val clientSecret = channel.requiredCredential("clientSecret")
            val accessToken = remote.obtainToken(
                clientId = channel.requiredCredential("clientId"),
                clientSecret = clientSecret,
            ).accessToken
            val data = remote.getAppInfo(accessToken, clientSecret, app.packageName).appInfo
            val auditStatus = data?.auditStatus
            val releaseStatus = data?.releaseStatus?.takeIf { it.isNotBlank() }
            MarketAppInfo(
                marketAppId = data?.appId ?: channel.marketAppId ?: app.packageName,
                packageName = data?.packageName ?: app.packageName,
                appName = data?.appName ?: app.name,
                onlineVersion = data?.versionName,
                reviewingVersion = data?.versionName,
                auditStatus = data?.auditStatusName ?: auditStatus?.let { statusText(it) },
                releaseStatus = releaseStatus?.let { statusText(it) }
                    ?: auditStatus?.let { statusText(it) },
                updatedAtText = "OPPO API",
            )
        }

    override suspend fun publish(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val clientId = request.channel.requiredCredential("clientId")
            val clientSecret = request.channel.requiredCredential("clientSecret")
            val logs = mutableListOf<PublishTaskLog>()

            val accessToken = remote.obtainToken(clientId, clientSecret).accessToken
            logs.emit(request, PublishTaskLog(LogLevel.Info, "OPPO Token 获取成功", PublishTaskStage.Validation))

            when (request.task.artifact.packageType) {
                PackageType.Apk -> publishUnified(request, accessToken, clientSecret, logs)
                PackageType.SplitApk -> publishSplit(request, accessToken, clientSecret, logs)
                PackageType.Aab -> error("OPPO 不支持 AAB 发布")
            }
        }

    override suspend fun refreshPublishStatus(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val clientId = request.channel.requiredCredential("clientId")
            val clientSecret = request.channel.requiredCredential("clientSecret")
            val accessToken = remote.obtainToken(clientId, clientSecret).accessToken
            val taskId = request.task.vendorTaskId
                ?: return@runCatching MarketPublishResult(
                    status = request.task.status,
                    logs = listOf(
                        PublishTaskLog(
                            level = LogLevel.Warning,
                            message = "OPPO 无任务 ID，无法刷新状态",
                            stage = PublishTaskStage.Result,
                        ),
                    ),
                )
            val result = remote.getTaskState(accessToken, clientSecret, taskId)
            val message = if (result.success == true) "任务处理成功" else result.message ?: "任务处理中"
            val status = if (result.success == true) PublishTaskStatus.Accepted else PublishTaskStatus.Submitted
            MarketPublishResult(
                status = status,
                logs = listOf(
                    PublishTaskLog(
                        level = LogLevel.Info,
                        message = "OPPO 任务状态：$message",
                        stage = PublishTaskStage.Result,
                    ),
                ),
                vendorTaskId = taskId,
            )
        }

    private suspend fun publishUnified(
        request: MarketPublishRequest,
        accessToken: String,
        clientSecret: String,
        logs: MutableList<PublishTaskLog>,
    ): MarketPublishResult {
        val artifact = request.task.artifact
        val localPath = resolveLocalPath(artifact)
        val versionCode = artifact.versionCode?.toString() ?: error("缺少 versionCode")
        val fileName = localPath.fileName()

        logs.emit(request, PublishTaskLog(LogLevel.Info, "读取 APK：$fileName", PublishTaskStage.Upload))
        val fileBytes = readFile(localPath).getOrElse { error("读取 APK 失败：${it.message ?: "未知错误"}") }

        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "获取 OPPO 上传配置",
                PublishTaskStage.Upload,
                progressPercent = 0,
                progressKey = "upload:apk:universal",
                progressLabel = "APK 上传",
            ),
        )
        val config = remote.getUploadUrl(accessToken, clientSecret, "apk")
        val uploadUrl = config.uploadUrl ?: error("OPPO 未返回 upload_url")
        val sign = config.sign ?: error("OPPO 未返回 sign")

        val upload = remote.uploadFile(uploadUrl, sign, "apk", fileName, fileBytes)
        val uploadedUrl = upload.url ?: error("OPPO 上传未返回 url")
        val uploadMd5 = upload.md5 ?: artifact.md5.takeIf { it.isNotBlank() } ?: error("OPPO 上传未返回 md5")

        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "APK 上传完成：${uploadedUrl.substringAfterLast('/')}",
                PublishTaskStage.Upload,
                progressPercent = 100,
                progressKey = "upload:apk:universal",
                progressLabel = "APK 上传",
            ),
        )

        val apkUrlJson = """[{"url":"$uploadedUrl","md5":"$uploadMd5","cpu_code":0}]"""
        logs.emit(request, PublishTaskLog(LogLevel.Info, "提交 OPPO 版本更新", PublishTaskStage.Submit))
        val publishResult = remote.publishApp(
            accessToken = accessToken,
            clientSecret = clientSecret,
            params = mapOf(
                "pkg_name" to request.app.packageName,
                "version_code" to versionCode,
                "apk_url" to apkUrlJson,
            ),
        )
        if (publishResult.success != true) {
            error("OPPO 提交发布失败：${publishResult.message ?: "未知错误"}")
        }
        val taskId = publishResult.taskId
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "OPPO 版本提交成功${taskId?.let { "，taskId=$it" }.orEmpty()}",
                PublishTaskStage.Result,
            ),
        )
        return MarketPublishResult(
            status = PublishTaskStatus.Submitted,
            logs = logs,
            vendorTaskId = taskId,
            vendorUploadIds = listOfNotNull(uploadedUrl),
        )
    }

    private suspend fun publishSplit(
        request: MarketPublishRequest,
        accessToken: String,
        clientSecret: String,
        logs: MutableList<PublishTaskLog>,
    ): MarketPublishResult {
        val artifact = request.task.artifact
        val versionCode = artifact.versionCode?.toString() ?: error("缺少 versionCode")

        val (url32, md532) = uploadSplitPart(
            request, accessToken, clientSecret, artifact.split32,
            "32 位 APK", "upload:apk:32", logs,
        )
        val (url64, md564) = uploadSplitPart(
            request, accessToken, clientSecret, artifact.split64,
            "64 位 APK", "upload:apk:64", logs,
        )

        val apkUrlJson = """[{"url":"$url32","md5":"$md532","cpu_code":32},{"url":"$url64","md5":"$md564","cpu_code":64}]"""
        logs.emit(request, PublishTaskLog(LogLevel.Info, "提交 OPPO 32/64 版本更新", PublishTaskStage.Submit))
        val publishResult = remote.updateMultiPackageApp(
            accessToken = accessToken,
            clientSecret = clientSecret,
            params = mapOf(
                "pkg_name" to request.app.packageName,
                "version_code" to versionCode,
                "apk_url" to apkUrlJson,
            ),
        )
        if (publishResult.success != true) {
            error("OPPO 32/64 提交发布失败：${publishResult.message ?: "未知错误"}")
        }
        val taskId = publishResult.taskId
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "OPPO 32/64 版本提交成功${taskId?.let { "，taskId=$it" }.orEmpty()}",
                PublishTaskStage.Result,
            ),
        )
        return MarketPublishResult(
            status = PublishTaskStatus.Submitted,
            logs = logs,
            vendorTaskId = taskId,
            vendorUploadIds = listOf(url32, url64),
        )
    }

    private suspend fun uploadSplitPart(
        request: MarketPublishRequest,
        accessToken: String,
        clientSecret: String,
        part: ArtifactPart,
        label: String,
        progressKey: String,
        logs: MutableList<PublishTaskLog>,
    ): Pair<String, String> {
        val localPath = part.value.takeIf { it.isNotBlank() } ?: error("缺少 $label 文件")
        val fileName = localPath.fileName()
        logs.emit(request, PublishTaskLog(LogLevel.Info, "读取 $label：$fileName", PublishTaskStage.Upload))
        val fileBytes = readFile(localPath).getOrElse { error("读取 $label 失败：${it.message ?: "未知错误"}") }

        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "获取 OPPO $label 上传配置",
                PublishTaskStage.Upload,
                progressPercent = 0,
                progressKey = progressKey,
                progressLabel = "$label 上传",
            ),
        )
        val config = remote.getUploadUrl(accessToken, clientSecret, "apk")
        val uploadUrl = config.uploadUrl ?: error("OPPO 未返回 $label upload_url")
        val sign = config.sign ?: error("OPPO 未返回 $label sign")

        val upload = remote.uploadFile(uploadUrl, sign, "apk", fileName, fileBytes)
        val uploadedUrl = upload.url ?: error("OPPO $label 上传未返回 url")
        val uploadMd5 = upload.md5 ?: part.md5.takeIf { it.isNotBlank() } ?: error("OPPO $label 上传未返回 md5")

        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "$label 上传完成",
                PublishTaskStage.Upload,
                progressPercent = 100,
                progressKey = progressKey,
                progressLabel = "$label 上传",
            ),
        )
        return Pair(uploadedUrl, uploadMd5)
    }

    private fun resolveLocalPath(artifact: io.github.loshine.andpub.domain.model.ArtifactDraft): String {
        if (artifact.sourceType == ArtifactSourceType.LocalFile) {
            return artifact.value.takeIf { it.isNotBlank() } ?: error("缺少 APK 文件路径")
        }
        return artifact.downloadedPath.takeIf { it.isNotBlank() }
            ?: error("OPPO 不支持直接 URL 发布，请切换为本地文件模式")
    }

    private fun statusText(status: String): String =
        when (status) {
            "0" -> "未发布"
            "1" -> "审核中"
            "2" -> "审核通过"
            "3" -> "测试不通过"
            "4" -> "运营审核中"
            "5" -> "运营打回"
            "6" -> "运营通过"
            "7" -> "定时发布"
            "00" -> "资质审核中"
            "11" -> "资质审核通过"
            "-11" -> "资质审核不通过"
            "-22" -> "报备提交成功"
            "22" -> "已冻结"
            "111" -> "上线"
            "222" -> "下线"
            "444" -> "审核不通过"
            else -> status
        }
}

private fun String.fileName(): String =
    trim().substringAfterLast('/').substringAfterLast('\\').ifBlank { "artifact.apk" }

private fun MutableList<PublishTaskLog>.emit(
    request: MarketPublishRequest,
    log: PublishTaskLog,
) {
    add(log)
    request.onLog(log)
}
