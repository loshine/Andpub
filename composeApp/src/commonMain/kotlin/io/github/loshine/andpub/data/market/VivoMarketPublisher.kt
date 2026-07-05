package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.vivo.VivoRemoteDataSource
import io.github.loshine.andpub.data.remote.vivo.VivoSplitUpdateRequest
import io.github.loshine.andpub.data.remote.vivo.VivoUnifiedUpdateRequest
import io.github.loshine.andpub.data.remote.vivo.VivoUploadResult
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactPart
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
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.domain.model.vivoEnvironment
import io.github.loshine.andpub.platform.readBinaryFile
import org.koin.core.annotation.Single

@Single
class VivoMarketPublisher(
    private val remote: VivoRemoteDataSource,
    private val readFile: suspend (String) -> Result<ByteArray> = ::readBinaryFile,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Vivo

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val data = remote.queryAppDetails(
                accessKey = channel.requiredCredential("accessKey"),
                accessSecret = channel.requiredCredential("accessSecret"),
                packageName = app.packageName,
                environment = channel.vivoEnvironment(),
            )
            val saleStatus = data.saleStatus ?: data.onlineStatus
            MarketAppInfo(
                marketAppId = channel.marketAppId ?: app.packageName,
                packageName = data.packageName ?: app.packageName,
                appName = data.appName ?: app.name,
                onlineVersion = data.versionName,
                reviewingVersion = data.versionName,
                auditStatus = auditStatusText(data.status, data.unPassReason),
                releaseStatus = releaseStatusText(saleStatus, data.onlineType)
                    ?: data.saleStatus?.toString()
                    ?: data.onlineStatus?.toString(),
                updatedAtText = "vivo API",
            )
        }

    override suspend fun publish(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val environment = request.channel.vivoEnvironment()
            val options = request.vivoOptions
            if (environment == VivoApiEnvironment.Production &&
                !options.productionConfirmed
            ) {
                error("vivo 正式环境发布需要先确认目标环境")
            }
            val onlineType = options.onlineType ?: error("缺少 vivo 发布字段：onlineType")
            val compatibleDevice = options.compatibleDevice ?: error("缺少 vivo 发布字段：compatibleDevice")
            val accessKey = request.channel.requiredCredential("accessKey")
            val accessSecret = request.channel.requiredCredential("accessSecret")
            val logs = mutableListOf<PublishTaskLog>()
            logs.emit(
                request,
                PublishTaskLog(
                    level = LogLevel.Info,
                    message = "vivo API 环境：${environment.displayName}",
                    stage = PublishTaskStage.Validation,
                ),
            )
            logs.emit(
                request,
                proveAppExists(
                    accessKey = accessKey,
                    accessSecret = accessSecret,
                    packageName = request.app.packageName,
                    environment = environment,
                ),
            )

            when (request.task.artifact.packageType) {
                PackageType.Apk -> publishUnified(
                    request = request,
                    accessKey = accessKey,
                    accessSecret = accessSecret,
                    onlineType = onlineType.code,
                    compatibleDevice = compatibleDevice.code,
                    logs = logs,
                )
                PackageType.SplitApk -> publishSplit(
                    request = request,
                    accessKey = accessKey,
                    accessSecret = accessSecret,
                    onlineType = onlineType.code,
                    compatibleDevice = compatibleDevice.code,
                    logs = logs,
                )
                PackageType.Aab -> error("vivo 不支持 AAB 发布")
            }
        }.mapFailureWithEnvironment(request.channel)

    override suspend fun refreshPublishStatus(request: MarketPublishRequest): Result<MarketPublishResult> =
        runCatching {
            val environment = request.channel.vivoEnvironment()
            val status = remote.queryTaskStatus(
                accessKey = request.channel.requiredCredential("accessKey"),
                accessSecret = request.channel.requiredCredential("accessSecret"),
                packageName = request.app.packageName,
                packetType = 0,
                environment = environment,
            )
            val taskStatus = status.taskStatus ?: error("vivo 任务状态未返回 status")
            val mappedStatus = when (taskStatus) {
                1, 2 -> PublishTaskStatus.Submitted
                3 -> PublishTaskStatus.Accepted
                4 -> PublishTaskStatus.Failed
                else -> PublishTaskStatus.Submitted
            }
            val statusText = when (taskStatus) {
                1 -> "待处理"
                2 -> "正在处理中"
                3 -> "处理成功"
                4 -> "处理失败"
                else -> "未知状态($taskStatus)"
            }
            val reason = status.errorReason?.takeIf { it.isNotBlank() }
            MarketPublishResult(
                status = mappedStatus,
                logs = listOf(
                    PublishTaskLog(
                        level = if (mappedStatus == PublishTaskStatus.Failed) LogLevel.Error else LogLevel.Info,
                        message = listOfNotNull("vivo 任务状态：$statusText", reason).joinToString("，"),
                        stage = PublishTaskStage.Result,
                    )
                ),
                environment = environment.displayName,
            )
        }.mapFailureWithEnvironment(request.channel)

    private suspend fun proveAppExists(
        accessKey: String,
        accessSecret: String,
        packageName: String,
        environment: VivoApiEnvironment,
    ): PublishTaskLog {
        val detail = runCatching {
            remote.queryAppDetails(
                accessKey = accessKey,
                accessSecret = accessSecret,
                packageName = packageName,
                environment = environment,
            )
        }.getOrElse {
            if (environment == VivoApiEnvironment.Sandbox && it.isUnsupportedSandboxAppDetail()) {
                return PublishTaskLog(
                    level = LogLevel.Warning,
                    message = "vivo 测试环境不支持 app.query.details，已跳过应用存在性预校验",
                    stage = PublishTaskStage.Validation,
                )
            }
            error("无法确认 vivo 应用已存在；首次创建不支持：${it.message ?: "未知错误"}")
        }
        require(detail.packageName == null || detail.packageName == packageName) {
            "vivo 应用包名不匹配：${detail.packageName}"
        }
        return PublishTaskLog(LogLevel.Info, "已确认 vivo 应用存在", PublishTaskStage.Validation)
    }

    private suspend fun publishUnified(
        request: MarketPublishRequest,
        accessKey: String,
        accessSecret: String,
        onlineType: String,
        compatibleDevice: String,
        logs: MutableList<PublishTaskLog>,
    ): MarketPublishResult {
        val artifact = request.task.artifact
        val versionCode = artifact.versionCode ?: error("缺少 vivo 发布字段：versionCode")
        val fileMd5 = artifact.md5.takeIf { it.isNotBlank() } ?: error("缺少 vivo 发布字段：fileMd5")
        val path = artifact.value.takeIf { it.isNotBlank() } ?: error("缺少 APK 文件")
        logs.emit(request, PublishTaskLog(LogLevel.Info, "读取 APK 文件：${path.fileName()}", PublishTaskStage.Upload))
        val bytes = readFile(path).getOrElse { error("读取 APK 文件失败：${it.message ?: "未知错误"}") }
        val environment = request.channel.vivoEnvironment()
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "开始上传 APK：${path.fileName()}，${bytes.size.toLong().readableBytes()}",
                PublishTaskStage.Upload,
                progressPercent = 0,
                progressKey = "upload:apk:universal",
                progressLabel = "APK 上传",
            )
        )
        val upload = remote.uploadApk(
            accessKey = accessKey,
            accessSecret = accessSecret,
            packageName = request.app.packageName,
            fileName = path.fileName(),
            fileBytes = bytes,
            fileMd5 = fileMd5,
            environment = environment,
        )
        val serial = upload.serialNumber ?: error("vivo 上传 APK 未返回 serialnumber")
        val uploadMd5 = upload.md5?.takeIf { it.isNotBlank() } ?: fileMd5
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "APK 上传完成：serialnumber=$serial，versionCode=$versionCode，md5=$uploadMd5",
                PublishTaskStage.Upload,
                progressPercent = 100,
                progressKey = "upload:apk:universal",
                progressLabel = "APK 上传",
            ),
        )
        logs.emit(
            request,
            PublishTaskLog(LogLevel.Info, "开始提交 vivo 更新审核", PublishTaskStage.Submit)
        )
        val submit = remote.syncUpdateApp(
            accessKey = accessKey,
            accessSecret = accessSecret,
            request = VivoUnifiedUpdateRequest(
                packageName = request.app.packageName,
                versionCode = versionCode,
                apk = serial,
                fileMd5 = uploadMd5,
                onlineType = onlineType,
                compatibleDevice = compatibleDevice,
            ),
            environment = environment,
        )
        logs.emit(
            request,
            PublishTaskLog(
                level = LogLevel.Info,
                message = "vivo 更新提交成功${submit.taskId?.let { "：taskId=$it" }.orEmpty()}",
                stage = PublishTaskStage.Submit,
            ),
        )
        return MarketPublishResult(
            status = PublishTaskStatus.Submitted,
            logs = logs,
            vendorTaskId = submit.taskId,
            vendorUploadIds = listOf(serial),
            environment = environment.displayName,
        )
    }

    private suspend fun publishSplit(
        request: MarketPublishRequest,
        accessKey: String,
        accessSecret: String,
        onlineType: String,
        compatibleDevice: String,
        logs: MutableList<PublishTaskLog>,
    ): MarketPublishResult {
        val environment = request.channel.vivoEnvironment()
        val upload32 = uploadSplitPart(
            label = "32 位 APK",
            methodName = "app.upload.apk.app.32",
            part = request.task.artifact.split32,
            progressKey = "upload:apk:32",
            upload = { fileName, bytes, md5 ->
                remote.uploadApk32(accessKey, accessSecret, request.app.packageName, fileName, bytes, md5, environment = environment)
            },
            logs = logs,
            request = request,
        )
        val upload64 = uploadSplitPart(
            label = "64 位 APK",
            methodName = "app.upload.apk.app.64",
            part = request.task.artifact.split64,
            progressKey = "upload:apk:64",
            upload = { fileName, bytes, md5 ->
                remote.uploadApk64(accessKey, accessSecret, request.app.packageName, fileName, bytes, md5, environment = environment)
            },
            logs = logs,
            request = request,
        )
        logs.emit(request, PublishTaskLog(LogLevel.Info, "开始提交 vivo 32/64 更新审核", PublishTaskStage.Submit))
        val submit = remote.syncUpdateSubpackageApp(
            accessKey = accessKey,
            accessSecret = accessSecret,
            request = VivoSplitUpdateRequest(
                packageName = request.app.packageName,
                apk32 = upload32,
                apk64 = upload64,
                onlineType = onlineType,
                compatibleDevice = compatibleDevice,
            ),
            environment = environment,
        )
        logs.emit(
            request,
            PublishTaskLog(
                level = LogLevel.Info,
                message = "vivo 32/64 更新提交成功${submit.taskId?.let { "：taskId=$it" }.orEmpty()}",
                stage = PublishTaskStage.Submit,
            ),
        )
        return MarketPublishResult(
            status = PublishTaskStatus.Submitted,
            logs = logs,
            vendorTaskId = submit.taskId,
            vendorUploadIds = listOf(upload32, upload64),
            environment = environment.displayName,
        )
    }

    private suspend fun uploadSplitPart(
        label: String,
        methodName: String,
        part: ArtifactPart,
        progressKey: String,
        upload: suspend (String, ByteArray, String) -> VivoUploadResult,
        logs: MutableList<PublishTaskLog>,
        request: MarketPublishRequest,
    ): String {
        val path = part.value.takeIf { it.isNotBlank() } ?: error("缺少 $label 文件")
        val md5 = part.md5.takeIf { it.isNotBlank() } ?: error("缺少 $label MD5")
        logs.emit(request, PublishTaskLog(LogLevel.Info, "读取 $label 文件：${path.fileName()}", PublishTaskStage.Upload))
        val bytes = readFile(path).getOrElse { error("读取 $label 文件失败：${it.message ?: "未知错误"}") }
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "开始上传 $label：$methodName，${path.fileName()}，${bytes.size.toLong().readableBytes()}",
                PublishTaskStage.Upload,
                progressPercent = 0,
                progressKey = progressKey,
                progressLabel = "$label 上传",
            ),
        )
        val result = upload(path.fileName(), bytes, md5)
        val serial = result.serialNumber ?: error("vivo 上传 $label 未返回 serialnumber")
        logs.emit(
            request,
            PublishTaskLog(
                LogLevel.Info,
                "$label 上传完成：serialnumber=$serial",
                PublishTaskStage.Upload,
                progressPercent = 100,
                progressKey = progressKey,
                progressLabel = "$label 上传",
            ),
        )
        return serial
    }

    private fun auditStatusText(
        status: Int?,
        unPassReason: String?,
    ): String? {
        val statusText = when (status) {
            1 -> "草稿"
            2 -> "待审核"
            3 -> "审核通过"
            4 -> "审核不通过"
            5 -> "撤销审核"
            null -> return null
            else -> "未知审核状态($status)"
        }
        val reason = unPassReason?.takeIf { status == 4 && it.isNotBlank() }
        return listOfNotNull(statusText, reason).joinToString("：")
    }

    private fun releaseStatusText(
        saleStatus: Int?,
        onlineType: Int?,
    ): String? {
        val saleText = when (saleStatus) {
            0 -> "待上架"
            1 -> "已上架"
            2 -> "已下架"
            null -> return null
            else -> "未知上架状态($saleStatus)"
        }
        val typeText = onlineType?.let { onlineTypeText(it) }
        return listOfNotNull(saleText, typeText).joinToString("，")
    }

    private fun onlineTypeText(onlineType: Int): String =
        when (onlineType) {
            1 -> "实时上架"
            2 -> "定时上架"
            else -> "未知上架类型($onlineType)"
        }
}

private fun <T> Result<T>.mapFailureWithEnvironment(channel: ChannelRecord): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = {
            Result.failure(
                IllegalStateException(
                    "vivo ${channel.vivoEnvironment().displayName} 发布失败：${it.message ?: "未知错误"}",
                    it,
                )
            )
        },
    )

private fun Throwable.isUnsupportedSandboxAppDetail(): Boolean {
    val text = message.orEmpty()
    return "code=10010" in text || "此功能不存在" in text
}
