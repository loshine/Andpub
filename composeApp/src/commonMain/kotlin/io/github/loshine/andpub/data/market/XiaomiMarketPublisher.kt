package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.xiaomi.XiaomiPackageInfo
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiRemoteDataSource
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single

@Single
class XiaomiMarketPublisher(
    private val remote: XiaomiRemoteDataSource,
    private val readFile: suspend (String) -> Result<ByteArray> = ::readBinaryFile,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Xiaomi

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val result = remote.queryApp(
                userName = channel.requiredCredential("userName"),
                password = channel.requiredCredential("password"),
                publicKey = channel.requiredCredential("publicKey"),
                packageName = app.packageName,
            )
            val info = result.packageInfo
            MarketAppInfo(
                marketAppId = channel.marketAppId ?: app.packageName,
                packageName = info?.packageName ?: app.packageName,
                appName = info?.appName ?: app.name,
                onlineVersion = info?.versionName,
                reviewingVersion = "小米接口暂不支持获取审核中版本",
                auditStatus = "小米暂不支持获取审核状态",
                releaseStatus = releaseStatusText(info, result.create),
                updatedAtText = "小米 API",
            )
        }

    override suspend fun publish(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val artifact = request.task.artifact
            if (artifact.packageType != PackageType.Apk) {
                error("小米仅支持统一 APK 发布，当前包类型：${artifact.packageType.displayName}")
            }

            val userName = request.channel.requiredCredential("userName")
            val password = request.channel.requiredCredential("password")
            val publicKey = request.channel.requiredCredential("publicKey")
            val logs = mutableListOf<PublishTaskLog>()

            logs.emit(request, PublishTaskLog(LogLevel.Info, "查询小米应用状态", PublishTaskStage.Validation))
            val queryResult = remote.queryApp(
                userName = userName,
                password = password,
                publicKey = publicKey,
                packageName = request.app.packageName,
            )
            if (queryResult.updateVersion != true) {
                error("小米应用不允许更新版本（updateVersion=false），请先在小米开发者中心确认应用已上架")
            }
            logs.emit(request, PublishTaskLog(LogLevel.Info, "已确认小米应用可更新", PublishTaskStage.Validation))

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

            val requestData = buildJsonObject {
                put("userName", userName)
                put("synchroType", 1)
                putJsonObject("appInfo") {
                    put("appName", request.app.name)
                    put("packageName", request.app.packageName)
                    artifact.versionName?.let { put("versionName", it) }
                }
            }.let { Json.encodeToString(it) }

            logs.emit(
                request,
                PublishTaskLog(
                    LogLevel.Info,
                    "提交小米更新，文件大小：${fileBytes.size.toLong().readableBytes()}",
                    PublishTaskStage.Submit,
                    progressPercent = 50,
                    progressKey = "upload:apk:universal",
                    progressLabel = "APK 上传",
                ),
            )
            val result = remote.pushAppWithFile(
                requestData = requestData,
                password = password,
                publicKey = publicKey,
                apkFileName = fileName,
                apkBytes = fileBytes,
                apkMd5 = artifact.md5.takeIf { it.isNotBlank() }
                    ?: error("缺少 APK MD5，请重新选择文件以生成校验值"),
            )
            logs.emit(
                request,
                PublishTaskLog(
                    LogLevel.Info,
                    "小米更新提交成功：${result.message ?: "已受理"}",
                    PublishTaskStage.Result,
                    progressPercent = 100,
                    progressKey = "upload:apk:universal",
                    progressLabel = "APK 上传",
                ),
            )
            MarketPublishResult(
                status = PublishTaskStatus.Submitted,
                logs = logs,
            )
        }

    private fun resolveLocalPath(artifact: io.github.loshine.andpub.domain.model.ArtifactDraft): String {
        if (artifact.sourceType == ArtifactSourceType.LocalFile) {
            return artifact.value.takeIf { it.isNotBlank() } ?: error("缺少 APK 文件路径")
        }
        return artifact.downloadedPath.takeIf { it.isNotBlank() }
            ?: error("小米不支持直接 URL 发布，请切换为本地文件模式")
    }

    private fun releaseStatusText(
        packageInfo: XiaomiPackageInfo?,
        create: Boolean?,
    ): String =
        when {
            packageInfo != null -> "已存在"
            create == true -> "未创建，可新增"
            create == false -> "未创建，不可新增"
            else -> "未返回上架状态"
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

private fun Long.readableBytes(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        "${(mb * 10).toInt() / 10.0} MB"
    } else {
        "${this / 1024} KB"
    }
}
