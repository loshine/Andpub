package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.tencent.TencentRemoteDataSource
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
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
class TencentMarketPublisher(
    private val remote: TencentRemoteDataSource,
    private val readFile: suspend (String) -> Result<ByteArray> = ::readBinaryFile,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Tencent

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val appId = channel.marketAppId ?: error("腾讯查询应用详情需要填写市场侧应用 ID")
            val detail = remote.queryAppDetail(
                userId = channel.requiredCredential("userId"),
                accessSecret = channel.requiredCredential("accessSecret"),
                packageName = app.packageName,
                appId = appId,
            )
            val updateStatus = remote.queryAppUpdateStatus(
                userId = channel.requiredCredential("userId"),
                accessSecret = channel.requiredCredential("accessSecret"),
                packageName = app.packageName,
                appId = appId,
            )
            val auditStatus = updateStatus?.auditStatus
            val onlineVersion = detail.versionName ?: fetchStoreVersion(app.packageName)
            MarketAppInfo(
                marketAppId = appId,
                packageName = detail.packageName ?: app.packageName,
                appName = detail.appName ?: app.name,
                onlineVersion = onlineVersion ?: "腾讯接口和应用宝网页均未返回线上版本",
                reviewingVersion = null,
                auditStatus = auditStatus?.let { auditStatusText(it) },
                releaseStatus = releaseStatusText(auditStatus),
                updatedAtText = "腾讯 API",
            )
        }

    override suspend fun publish(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val artifact = request.task.artifact
            val userId = request.channel.requiredCredential("userId")
            val accessSecret = request.channel.requiredCredential("accessSecret")
            val appId = request.channel.marketAppId
                ?: error("腾讯发布需要填写市场侧应用 ID，请在渠道配置中填写 app_id")
            val logs = mutableListOf<PublishTaskLog>()

            when (artifact.packageType) {
                PackageType.Apk -> publishUnified(request, userId, accessSecret, appId, logs)
                PackageType.SplitApk -> publishSplit(request, userId, accessSecret, appId, logs)
                PackageType.Aab -> error("腾讯不支持 AAB 发布")
            }
        }

    override suspend fun refreshPublishStatus(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val appId = request.channel.marketAppId
                ?: error("腾讯刷新状态需要填写市场侧应用 ID")
            val status = remote.queryAppUpdateStatus(
                userId = request.channel.requiredCredential("userId"),
                accessSecret = request.channel.requiredCredential("accessSecret"),
                packageName = request.app.packageName,
                appId = appId,
            )
            val auditStatus = status?.auditStatus
            MarketPublishResult(
                status = if (auditStatus == 3) PublishTaskStatus.Accepted else PublishTaskStatus.Submitted,
                logs = listOf(
                    PublishTaskLog(
                        level = LogLevel.Info,
                        message = "腾讯审核状态：${auditStatus?.let { auditStatusText(it) } ?: "已提交"}",
                        stage = PublishTaskStage.Result,
                    ),
                ),
            )
        }

    private suspend fun publishUnified(
        request: MarketPublishRequest,
        userId: String,
        accessSecret: String,
        appId: String,
        logs: MutableList<PublishTaskLog>,
    ): MarketPublishResult {
        val artifact = request.task.artifact
        val localPath = resolveLocalPath(artifact)
        val fileName = localPath.fileName()

        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "读取 APK：$fileName",
                PublishTaskStage.Upload,
                progressPercent = 0,
                progressKey = "upload:apk:universal",
                progressLabel = "APK 上传",
            ),
        )
        val fileBytes = readFile(localPath).getOrElse {
            error("读取 APK 失败：${it.message ?: "未知错误"}")
        }

        logs.emit(request, PublishTaskLog(LogLevel.Info, "获取腾讯 COS 上传凭证", PublishTaskStage.Upload))
        val uploadInfo = remote.getFileUploadInfo(
            userId = userId,
            accessSecret = accessSecret,
            packageName = request.app.packageName,
            appId = appId,
            fileType = "apk",
            fileName = fileName,
        )
        val presignedUrl = uploadInfo.preSignUrl ?: error("腾讯未返回预签名上传 URL")
        val serialNumber = uploadInfo.serialNumber ?: error("腾讯未返回文件流水号")

        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "上传 APK 至腾讯 COS，文件大小：${fileBytes.size.toLong().readableBytes()}",
                PublishTaskStage.Upload,
                progressPercent = 50,
                progressKey = "upload:apk:universal",
                progressLabel = "APK 上传",
            ),
        )
        remote.uploadToPresignedUrl(presignedUrl, fileBytes)
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "APK 上传完成，流水号：$serialNumber",
                PublishTaskStage.Upload,
                progressPercent = 100,
                progressKey = "upload:apk:universal",
                progressLabel = "APK 上传",
            ),
        )

        logs.emit(request, PublishTaskLog(LogLevel.Info, "提交腾讯版本更新", PublishTaskStage.Submit))
        remote.updateApp(
            userId = userId,
            accessSecret = accessSecret,
            packageName = request.app.packageName,
            appId = appId,
            params = mapOf(
                "apk32_flag" to "1",
                "apk32_file_serial_number" to serialNumber,
                "apk32_file_md5" to artifact.md5.takeIf { it.isNotBlank() }.orEmpty(),
                "deploy_type" to "1",
            ),
        )
        logs.emit(request, PublishTaskLog(LogLevel.Info, "腾讯版本提交成功", PublishTaskStage.Result))

        return MarketPublishResult(
            status = PublishTaskStatus.Submitted,
            logs = logs,
            vendorUploadIds = listOf(serialNumber),
        )
    }

    private suspend fun publishSplit(
        request: MarketPublishRequest,
        userId: String,
        accessSecret: String,
        appId: String,
        logs: MutableList<PublishTaskLog>,
    ): MarketPublishResult {
        val artifact = request.task.artifact

        val (serial32, md532) = uploadApkPart(
            request, userId, accessSecret, appId,
            localPath = artifact.split32.value.takeIf { it.isNotBlank() } ?: error("缺少 32 位 APK 文件"),
            md5 = artifact.split32.md5,
            label = "32 位 APK",
            progressKey = "upload:apk:32",
            logs = logs,
        )
        val (serial64, md564) = uploadApkPart(
            request, userId, accessSecret, appId,
            localPath = artifact.split64.value.takeIf { it.isNotBlank() } ?: error("缺少 64 位 APK 文件"),
            md5 = artifact.split64.md5,
            label = "64 位 APK",
            progressKey = "upload:apk:64",
            logs = logs,
        )

        logs.emit(request, PublishTaskLog(LogLevel.Info, "提交腾讯 32/64 版本更新", PublishTaskStage.Submit))
        remote.updateApp(
            userId = userId,
            accessSecret = accessSecret,
            packageName = request.app.packageName,
            appId = appId,
            params = mapOf(
                "apk32_flag" to "1",
                "apk32_file_serial_number" to serial32,
                "apk32_file_md5" to md532,
                "apk64_flag" to "1",
                "apk64_file_serial_number" to serial64,
                "apk64_file_md5" to md564,
                "deploy_type" to "1",
            ),
        )
        logs.emit(request, PublishTaskLog(LogLevel.Info, "腾讯 32/64 版本提交成功", PublishTaskStage.Result))

        return MarketPublishResult(
            status = PublishTaskStatus.Submitted,
            logs = logs,
            vendorUploadIds = listOf(serial32, serial64),
        )
    }

    private suspend fun uploadApkPart(
        request: MarketPublishRequest,
        userId: String,
        accessSecret: String,
        appId: String,
        localPath: String,
        md5: String,
        label: String,
        progressKey: String,
        logs: MutableList<PublishTaskLog>,
    ): Pair<String, String> {
        val fileName = localPath.fileName()
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "读取 $label：$fileName",
                PublishTaskStage.Upload,
                progressPercent = 0,
                progressKey = progressKey,
                progressLabel = "$label 上传",
            ),
        )
        val fileBytes = readFile(localPath).getOrElse {
            error("读取 $label 失败：${it.message ?: "未知错误"}")
        }
        val uploadInfo = remote.getFileUploadInfo(
            userId = userId,
            accessSecret = accessSecret,
            packageName = request.app.packageName,
            appId = appId,
            fileType = "apk",
            fileName = fileName,
        )
        val presignedUrl = uploadInfo.preSignUrl ?: error("腾讯未返回 $label 预签名上传 URL")
        val serialNumber = uploadInfo.serialNumber ?: error("腾讯未返回 $label 文件流水号")

        remote.uploadToPresignedUrl(presignedUrl, fileBytes)
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "$label 上传完成，流水号：$serialNumber",
                PublishTaskStage.Upload,
                progressPercent = 100,
                progressKey = progressKey,
                progressLabel = "$label 上传",
            ),
        )
        return Pair(serialNumber, md5.takeIf { it.isNotBlank() }.orEmpty())
    }

    private fun resolveLocalPath(artifact: io.github.loshine.andpub.domain.model.ArtifactDraft): String =
        artifact.resolveLocalPath("腾讯不支持直接 URL 发布，请切换为本地文件模式")

    private suspend fun fetchStoreVersion(packageName: String): String? =
        runCatching {
            parseTencentStoreVersion(remote.getStorePage(packageName))
        }.getOrNull()

    private fun auditStatusText(status: Int): String =
        when (status) {
            1 -> "审核中"
            2 -> "审核驳回"
            3 -> "审核通过"
            8 -> "开发者主动撤销"
            else -> "未知审核状态($status)"
        }

    private fun releaseStatusText(auditStatus: Int?): String =
        when (auditStatus) {
            1 -> "已上架，更新审核中"
            2 -> "已上架，更新审核驳回"
            3 -> "已上架，更新审核通过"
            8 -> "已上架，更新已撤销"
            else -> "已上架"
        }

    private fun parseTencentStoreVersion(html: String): String? {
        val text = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("<[^>]+>"), "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        val versionPattern = Regex("""[0-9]+(?:[._-][0-9A-Za-z]+)+""")
        Regex("""版本号\s*[:：]?\s*(${versionPattern.pattern})""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            if (line == "版本号") {
                lines.drop(index + 1)
                    .firstOrNull { versionPattern.matches(it) }
                    ?.let { return it }
            }
        }
        return Regex("""\bV\s*(${versionPattern.pattern})\b""")
            .find(text)
            ?.groupValues
            ?.get(1)
    }
}
