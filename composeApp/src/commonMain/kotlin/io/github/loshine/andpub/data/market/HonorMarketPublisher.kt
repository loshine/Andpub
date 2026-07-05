package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.honor.HonorRemoteDataSource
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
class HonorMarketPublisher(
    private val remote: HonorRemoteDataSource,
    private val readFile: suspend (String) -> Result<ByteArray> = ::readBinaryFile,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Honor

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val token = remote.obtainToken(
                clientId = channel.requiredCredential("clientId"),
                clientSecret = channel.requiredCredential("clientSecret"),
            ).accessToken
            val appId = channel.marketAppId
                ?: remote.getAppId(token, app.packageName).apps.firstOrNull()?.appId
                ?: error("荣耀未返回 APPID")
            val detail = remote.getAppDetail(token, appId)
            val currentRelease = remote.getCurrentRelease(token, appId)
            MarketAppInfo(
                marketAppId = appId,
                packageName = detail.packageName ?: app.packageName,
                appName = detail.appName ?: app.name,
                onlineVersion = currentRelease?.versionName ?: detail.versionName,
                reviewingVersion = currentRelease?.versionName,
                auditStatus = currentRelease?.auditResult?.let { auditStatusText(it) } ?: currentRelease?.auditStatus,
                releaseStatus = currentRelease?.releaseStatus ?: detail.versionName?.let { "已上架" },
                updatedAtText = "荣耀 API",
            )
        }

    override suspend fun publish(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val artifact = request.task.artifact
            if (artifact.packageType != PackageType.Apk) {
                error("荣耀仅支持统一 APK 发布，当前包类型：${artifact.packageType.displayName}")
            }

            val localPath = resolveLocalPath(artifact)
            val logs = mutableListOf<PublishTaskLog>()

            val token = remote.obtainToken(
                clientId = request.channel.requiredCredential("clientId"),
                clientSecret = request.channel.requiredCredential("clientSecret"),
            ).accessToken
            logs.emit(request, PublishTaskLog(LogLevel.Info, "荣耀 Token 获取成功", PublishTaskStage.Validation))

            val appId = request.channel.marketAppId
                ?: remote.getAppId(token, request.app.packageName).apps.firstOrNull()?.appId
                ?: error("荣耀未返回 APPID，请在渠道配置中填写市场应用 ID")
            logs.emit(request, PublishTaskLog(LogLevel.Info, "荣耀 APPID：$appId", PublishTaskStage.Validation))

            val fileName = localPath.fileName()
            logs.emit(
                request,
                PublishTaskLog(LogLevel.Info, "读取 APK：$fileName", PublishTaskStage.Upload),
            )
            val fileBytes = readFile(localPath).getOrElse {
                error("读取 APK 失败：${it.message ?: "未知错误"}")
            }
            val fileSize = fileBytes.size.toLong()
            val fileSha256 = artifact.sha256.takeIf { it.isNotBlank() }
                ?: error("缺少 APK SHA-256，请重新选择文件以生成校验值")

            logs.emit(
                request,
                PublishTaskLog(
                    LogLevel.Info,
                    "获取荣耀文件上传路径，文件大小：${fileSize.readableBytes()}",
                    PublishTaskStage.Upload,
                    progressPercent = 0,
                    progressKey = "upload:apk:universal",
                    progressLabel = "APK 上传",
                ),
            )
            val uploadPathBody = """[{"fileName":"$fileName","fileType":100,"fileSize":$fileSize,"fileSha256":"$fileSha256"}]"""
            val uploadPaths = remote.getFileUploadUrl(token, appId, uploadPathBody)
            val uploadPath = uploadPaths.files.firstOrNull()
                ?: error("荣耀未返回文件上传路径")
            val uploadUrl = uploadPath.uploadUrl ?: error("荣耀未返回 uploadUrl")
            val objectId = uploadPath.objectId ?: error("荣耀未返回 objectId")

            remote.uploadFile(token, appId, objectId, fileName, fileBytes)
            logs.emit(
                request,
                PublishTaskLog(
                    LogLevel.Info,
                    "APK 上传完成，objectId=$objectId",
                    PublishTaskStage.Upload,
                    progressPercent = 100,
                    progressKey = "upload:apk:universal",
                    progressLabel = "APK 上传",
                ),
            )

            val fileInfoBody = """{"bindingFileList":[{"objectId":$objectId}]}"""
            remote.updateFileInfo(token, appId, fileInfoBody)
            logs.emit(request, PublishTaskLog(LogLevel.Info, "APK 文件已绑定到应用", PublishTaskStage.Submit))

            logs.emit(request, PublishTaskLog(LogLevel.Info, "提交荣耀审核", PublishTaskStage.Submit))
            remote.submitAudit(token, appId, "{}")
            logs.emit(request, PublishTaskLog(LogLevel.Info, "荣耀审核提交成功", PublishTaskStage.Result))

            MarketPublishResult(
                status = PublishTaskStatus.Submitted,
                logs = logs,
                vendorUploadIds = listOf(objectId),
            )
        }

    override suspend fun refreshPublishStatus(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val token = remote.obtainToken(
                clientId = request.channel.requiredCredential("clientId"),
                clientSecret = request.channel.requiredCredential("clientSecret"),
            ).accessToken
            val appId = request.channel.marketAppId
                ?: remote.getAppId(token, request.app.packageName).apps.firstOrNull()?.appId
                ?: error("荣耀未返回 APPID")
            val body = """{"appIdList":["$appId"]}"""
            val result = remote.getAuditResult(token, body)
            val message = result.message?.takeIf { it.isNotBlank() } ?: "荣耀审核状态已刷新"
            MarketPublishResult(
                status = PublishTaskStatus.Submitted,
                logs = listOf(
                    PublishTaskLog(
                        level = LogLevel.Info,
                        message = "荣耀审核状态：$message",
                        stage = PublishTaskStage.Result,
                    ),
                ),
            )
        }

    private fun resolveLocalPath(artifact: io.github.loshine.andpub.domain.model.ArtifactDraft): String =
        artifact.resolveLocalPath("荣耀不支持直接 URL 提交，请切换为本地文件模式，或将 artifact 来源设为本地文件")

    private fun auditStatusText(status: Int): String =
        when (status) {
            0 -> "审核中"
            1 -> "审核通过"
            2 -> "审核不通过"
            3 -> "其他非审核状态"
            4 -> "编辑中，未提交审核"
            else -> "未知审核状态($status)"
        }
}

