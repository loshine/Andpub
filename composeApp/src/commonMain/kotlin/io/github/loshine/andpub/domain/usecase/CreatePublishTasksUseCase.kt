package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.github.loshine.andpub.domain.model.MarketCapability
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.platform.ArtifactDownloadTarget
import io.github.loshine.andpub.platform.ArtifactTransferProgress
import io.github.loshine.andpub.platform.currentPublishTimeFolder
import io.github.loshine.andpub.platform.downloadArtifactFromUrl
import io.github.loshine.andpub.platform.inspectLocalArtifact
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class CreatePublishTasksUseCase(
    private val downloadUrlArtifact: suspend (String, ArtifactDownloadTarget, (ArtifactTransferProgress) -> Unit) -> Result<String> = ::downloadArtifactFromUrl,
    private val inspectArtifact: suspend (String, String, String) -> Result<ArtifactInspection> = ::inspectLocalArtifact,
) {
    fun createPendingTasks(
        snapshot: LocalStateSnapshot,
        app: AppRecord,
        channels: List<ChannelRecord>,
    ): List<PublishTaskRecord> {
        var nextId = snapshot.nextAvailableId()
        return channels.map { channel ->
            PublishTaskRecord(
                id = "task-${nextId++}",
                appId = app.id,
                channelId = channel.id,
                marketType = channel.marketType,
                publishMode = snapshot.publishMode,
                artifact = snapshot.artifactFor(channel),
                status = PublishTaskStatus.Validating,
                logs = listOf(
                    PublishTaskLog(
                        level = LogLevel.Info,
                        message = "${channel.marketType.displayName} 正在准备并检查产物",
                        stage = PublishTaskStage.Validation,
                    ),
                ),
            )
        }
    }

    suspend operator fun invoke(
        snapshot: LocalStateSnapshot,
        app: AppRecord,
        channels: List<ChannelRecord>,
        onTaskLog: (String, PublishTaskLog) -> Unit = { _, _ -> },
    ): List<PublishTaskRecord> = coroutineScope {
        var nextId = snapshot.nextAvailableId()
        val publishTimeFolder = currentPublishTimeFolder()
        channels.map { channel ->
            val taskId = "task-${nextId++}"
            async {
                createPublishTaskRecord(
                    snapshot = snapshot,
                    app = app,
                    channel = channel,
                    taskId = taskId,
                    publishTimeFolder = publishTimeFolder,
                    onTaskLog = onTaskLog,
                )
            }
        }.awaitAll()
    }

    private suspend fun createPublishTaskRecord(
        snapshot: LocalStateSnapshot,
        app: AppRecord,
        channel: ChannelRecord,
        taskId: String,
        publishTimeFolder: String,
        onTaskLog: (String, PublishTaskLog) -> Unit,
    ): PublishTaskRecord {
        val artifact = snapshot.artifactFor(channel)
        val prepared = runCatching {
            prepareArtifact(channel, artifact, snapshot.toolSettings, publishTimeFolder) { log ->
                onTaskLog(taskId, log)
            }
        }.getOrElse {
            PreparedArtifact(
                artifact = artifact,
                logs = listOf(
                    PublishTaskLog(
                        LogLevel.Error,
                        "${channel.marketType.displayName} 发布任务创建失败：${it.message ?: "未知错误"}",
                    )
                ),
            )
        }
        val logs = prepared.logs + validateArtifact(app.packageName, channel, prepared.artifact)
        return PublishTaskRecord(
            id = taskId,
            appId = app.id,
            channelId = channel.id,
            marketType = channel.marketType,
            publishMode = snapshot.publishMode,
            artifact = prepared.artifact,
            status = if (logs.any { it.level == LogLevel.Error }) {
                PublishTaskStatus.Failed
            } else {
                PublishTaskStatus.Ready
            },
            logs = logs,
        )
    }

    private suspend fun prepareArtifact(
        channel: ChannelRecord,
        artifact: ArtifactDraft,
        toolSettings: ToolSettings,
        publishTimeFolder: String,
        onProgressLog: (PublishTaskLog) -> Unit,
    ): PreparedArtifact {
        val capability = MarketDefinitions.schemaOf(channel.marketType).capability
        if (!artifact.packageType.isSupportedBy(capability)) {
            return PreparedArtifact(artifact)
        }
        if (artifact.packageType == PackageType.SplitApk) {
            return prepareSplitUrlArtifact(channel, artifact, capability, toolSettings, publishTimeFolder, onProgressLog)
        }
        if (artifact.sourceType != ArtifactSourceType.Url) {
            return PreparedArtifact(artifact)
        }
        if (!artifact.value.isHttpUrl()) {
            return PreparedArtifact(artifact)
        }

        val originalUrl = artifact.value
        val progressKey = artifact.downloadProgressKey()
        val progressLabel = artifact.downloadProgressLabel()
        val progressLogs = mutableListOf<PublishTaskLog>()
        val emitProgressLog = { log: PublishTaskLog ->
            progressLogs += log
            onProgressLog(log)
        }
        emitProgressLog(
            PublishTaskLog(
                level = LogLevel.Info,
                message = "${channel.marketType.displayName} 开始下载 URL 产物",
                stage = PublishTaskStage.Download,
                progressPercent = 0,
                progressKey = progressKey,
                progressLabel = progressLabel,
            )
        )
        val downloadResult = downloadUrlArtifact(
            originalUrl,
            artifact.downloadTarget(channel, publishTimeFolder),
        ) {
            emitProgressLog(
                downloadProgressLog(
                    progressKey = progressKey,
                    progressLabel = progressLabel,
                    messageLabel = "${channel.marketType.displayName} URL 产物",
                    progress = it,
                )
            )
        }
        val localPath = downloadResult.getOrElse {
            return PreparedArtifact(
                artifact = artifact,
                logs = progressLogs + listOf(
                    PublishTaskLog(
                        LogLevel.Error,
                        "${channel.marketType.displayName} URL 产物下载检查失败：${it.message ?: "未知错误"}",
                    )
                ),
            )
        }

        val inspected = inspectArtifact(
            localPath,
            toolSettings.androidSdkPath,
            toolSettings.bundletoolPath,
        ).requireManifest("URL 产物")
        return inspected.fold(
            onSuccess = {
                val uploadByUrl = capability.supportsUserUrl
                PreparedArtifact(
                    artifact = if (uploadByUrl) {
                        artifact.withUrlInspection(originalUrl, localPath, it)
                    } else {
                        artifact.withDownloadedInspection(localPath, it)
                    },
                    logs = progressLogs + listOf(
                        PublishTaskLog(
                            LogLevel.Info,
                            if (uploadByUrl) {
                                "${channel.marketType.displayName} URL 产物已下载并检查，后续将直接提交 URL"
                            } else {
                                "${channel.marketType.displayName} 不支持 URL 直传，已下载并检查后按本地文件上传"
                            },
                        )
                    ),
                )
            },
            onFailure = {
                val uploadByUrl = capability.supportsUserUrl
                PreparedArtifact(
                    artifact = if (uploadByUrl) {
                        artifact.copy(
                            downloadedPath = localPath,
                            message = "URL 已下载，产物解析失败：${it.message ?: "未知错误"}",
                        )
                    } else {
                        artifact.copy(
                            sourceType = ArtifactSourceType.LocalFile,
                            value = localPath,
                            downloadedPath = localPath,
                            message = "URL 已下载，产物解析失败：${it.message ?: "未知错误"}",
                        )
                    },
                    logs = progressLogs + listOf(
                        PublishTaskLog(
                            LogLevel.Error,
                            "${channel.marketType.displayName} URL 产物已下载但检查失败：${it.message ?: "未知错误"}",
                        )
                    ),
                )
            },
        )
    }

    private suspend fun prepareSplitUrlArtifact(
        channel: ChannelRecord,
        artifact: ArtifactDraft,
        capability: MarketCapability,
        toolSettings: ToolSettings,
        publishTimeFolder: String,
        onProgressLog: (PublishTaskLog) -> Unit,
    ): PreparedArtifact {
        if (!artifact.split32.value.isHttpUrl() || !artifact.split64.value.isHttpUrl()) {
            return PreparedArtifact(artifact)
        }

        val (downloaded32, downloaded64) = coroutineScope {
            val part32 = async {
                downloadSplitPart(
                    channel = channel,
                    slotName = "32 位 APK",
                    part = artifact.split32,
                    uploadByUrl = capability.supportsUserUrl,
                    toolSettings = toolSettings,
                    publishTimeFolder = publishTimeFolder,
                    variantFolder = "32",
                    onProgressLog = onProgressLog,
                )
            }
            val part64 = async {
                downloadSplitPart(
                    channel = channel,
                    slotName = "64 位 APK",
                    part = artifact.split64,
                    uploadByUrl = capability.supportsUserUrl,
                    toolSettings = toolSettings,
                    publishTimeFolder = publishTimeFolder,
                    variantFolder = "64",
                    onProgressLog = onProgressLog,
                )
            }
            part32.await() to part64.await()
        }
        val logs = downloaded32.logs + downloaded64.logs
        if (logs.any { it.level == LogLevel.Error }) {
            return PreparedArtifact(artifact, logs)
        }
        return PreparedArtifact(
            artifact = artifact.copy(
                sourceType = if (capability.supportsUserUrl) ArtifactSourceType.Url else ArtifactSourceType.LocalFile,
                split32 = downloaded32.part,
                split64 = downloaded64.part,
            ),
            logs = logs,
        )
    }

    private suspend fun downloadSplitPart(
        channel: ChannelRecord,
        slotName: String,
        part: ArtifactPart,
        uploadByUrl: Boolean,
        toolSettings: ToolSettings,
        publishTimeFolder: String,
        variantFolder: String,
        onProgressLog: (PublishTaskLog) -> Unit,
    ): PreparedSplitPart {
        if (!part.value.isHttpUrl()) {
            return PreparedSplitPart(part)
        }

        val originalUrl = part.value
        val progressKey = splitDownloadProgressKey(variantFolder)
        val progressLabel = "$slotName 下载"
        val progressLogs = mutableListOf<PublishTaskLog>()
        val emitProgressLog = { log: PublishTaskLog ->
            progressLogs += log
            onProgressLog(log)
        }
        emitProgressLog(
            PublishTaskLog(
                level = LogLevel.Info,
                message = "${channel.marketType.displayName} $slotName 开始下载",
                stage = PublishTaskStage.Download,
                progressPercent = 0,
                progressKey = progressKey,
                progressLabel = progressLabel,
            )
        )
        val localPath = downloadUrlArtifact(
            originalUrl,
            splitDownloadTarget(channel, publishTimeFolder, variantFolder),
        ) {
            emitProgressLog(
                downloadProgressLog(
                    progressKey = progressKey,
                    progressLabel = progressLabel,
                    messageLabel = "${channel.marketType.displayName} $slotName",
                    progress = it,
                )
            )
        }.getOrElse {
            return PreparedSplitPart(
                part = part,
                logs = progressLogs + listOf(
                    PublishTaskLog(
                        LogLevel.Error,
                        "${channel.marketType.displayName} $slotName URL 下载检查失败：${it.message ?: "未知错误"}",
                    )
                ),
            )
        }
        val inspected = inspectArtifact(
            localPath,
            toolSettings.androidSdkPath,
            toolSettings.bundletoolPath,
        ).requireManifest(slotName)
        return inspected.fold(
            onSuccess = {
                PreparedSplitPart(
                    part = if (uploadByUrl) {
                        part.withUrlInspection(originalUrl, localPath, it)
                    } else {
                        ArtifactPart().withDownloadedInspection(localPath, it)
                    },
                    logs = progressLogs + listOf(
                        PublishTaskLog(
                            LogLevel.Info,
                            if (uploadByUrl) {
                                "${channel.marketType.displayName} $slotName URL 已下载并检查，后续将直接提交 URL"
                            } else {
                                "${channel.marketType.displayName} 不支持 URL 直传，$slotName 已下载并检查后按本地文件上传"
                            },
                        )
                    ),
                )
            },
            onFailure = {
                PreparedSplitPart(
                    part = if (uploadByUrl) {
                        part.copy(
                            downloadedPath = localPath,
                            message = "URL 已下载，产物解析失败：${it.message ?: "未知错误"}",
                        )
                    } else {
                        part.copy(
                            value = localPath,
                            downloadedPath = localPath,
                            message = "URL 已下载，产物解析失败：${it.message ?: "未知错误"}",
                        )
                    },
                    logs = progressLogs + listOf(
                        PublishTaskLog(
                            LogLevel.Error,
                            "${channel.marketType.displayName} $slotName URL 已下载但检查失败：${it.message ?: "未知错误"}",
                        )
                    ),
                )
            },
        )
    }

    private fun validateArtifact(
        packageName: String,
        channel: ChannelRecord,
        artifact: ArtifactDraft,
    ): List<PublishTaskLog> {
        val logs = mutableListOf<PublishTaskLog>()
        val capability = MarketDefinitions.schemaOf(channel.marketType).capability
        if (!artifact.packageType.isSupportedBy(capability)) {
            logs += PublishTaskLog(LogLevel.Error, "${channel.marketType.displayName} 不支持 ${artifact.packageType.displayName}")
        }
        if (artifact.packageType == PackageType.SplitApk) {
            logs += validateSplitArtifact(packageName, artifact)
            return logs.ifEmpty {
                listOf(PublishTaskLog(LogLevel.Info, "32/64 APK 校验通过，等待后续提交审核能力接入"))
            }
        }

        if (artifact.value.isBlank()) {
            logs += PublishTaskLog(LogLevel.Error, "缺少产物文件或 URL")
        }
        if (artifact.sourceType == ArtifactSourceType.Url) {
            val urlValid = artifact.value.isHttpUrl()
            if (!urlValid) {
                logs += PublishTaskLog(LogLevel.Error, "URL 格式不正确")
            }
            if (urlValid && capability.supportsUserUrl) {
                logs += PublishTaskLog(LogLevel.Info, "${channel.marketType.displayName} 支持 URL 拉包，将直接提交 URL")
            }
            if (artifact.packageName == null) {
                logs += PublishTaskLog(LogLevel.Warning, "URL 产物未读取到 manifest 信息，无法校验包名和版本")
            }
        }
        if (artifact.packageName != null && artifact.packageName != packageName) {
            logs += PublishTaskLog(LogLevel.Error, "产物包名 ${artifact.packageName} 与应用包名 $packageName 不一致")
        }
        if (artifact.packageName == null && artifact.sourceType == ArtifactSourceType.LocalFile) {
            logs += PublishTaskLog(LogLevel.Warning, "未读取到 manifest 信息，无法校验包名和版本")
        }
        if (logs.isEmpty()) {
            logs += PublishTaskLog(LogLevel.Info, "产物校验通过，等待后续提交审核能力接入")
        }
        return logs
    }

    private fun validateSplitArtifact(
        packageName: String,
        artifact: ArtifactDraft,
    ): List<PublishTaskLog> {
        val logs = mutableListOf<PublishTaskLog>()
        if (artifact.sourceType == ArtifactSourceType.Url) {
            if (!artifact.split32.value.isHttpUrl()) {
                logs += PublishTaskLog(LogLevel.Error, "32 位 APK URL 格式不正确")
            }
            if (!artifact.split64.value.isHttpUrl()) {
                logs += PublishTaskLog(LogLevel.Error, "64 位 APK URL 格式不正确")
            }
            logs += PublishTaskLog(LogLevel.Info, "32/64 APK URL 产物已下载检查，将按渠道能力直传或使用本地文件上传")
        }
        logs += validateSplitPart("32 位 APK", packageName, artifact.split32)
        logs += validateSplitPart("64 位 APK", packageName, artifact.split64)

        val packageNames = listOfNotNull(artifact.split32.packageName, artifact.split64.packageName).distinct()
        if (packageNames.size > 1) {
            logs += PublishTaskLog(LogLevel.Error, "32/64 APK 包名不一致：${packageNames.joinToString()}")
        }

        val versionCodes = listOfNotNull(artifact.split32.versionCode, artifact.split64.versionCode).distinct()
        if (versionCodes.size > 1) {
            logs += PublishTaskLog(LogLevel.Warning, "32/64 APK versionCode 不一致：${versionCodes.joinToString()}")
        }

        val versionNames = listOfNotNull(artifact.split32.versionName, artifact.split64.versionName).distinct()
        if (versionNames.size > 1) {
            logs += PublishTaskLog(LogLevel.Warning, "32/64 APK versionName 不一致：${versionNames.joinToString()}")
        }
        return logs
    }

    private fun validateSplitPart(
        label: String,
        packageName: String,
        part: ArtifactPart,
    ): List<PublishTaskLog> {
        val logs = mutableListOf<PublishTaskLog>()
        if (part.value.isBlank()) {
            logs += PublishTaskLog(LogLevel.Error, "缺少 $label 文件")
        }
        if (part.packageName != null && part.packageName != packageName) {
            logs += PublishTaskLog(LogLevel.Error, "$label 包名 ${part.packageName} 与应用包名 $packageName 不一致")
        }
        if (part.packageName == null && part.value.isNotBlank()) {
            logs += PublishTaskLog(LogLevel.Warning, "$label 未读取到 manifest 信息，无法校验包名和版本")
        }
        return logs
    }

    private fun PackageType.isSupportedBy(capability: MarketCapability): Boolean =
        when (this) {
            PackageType.Apk -> capability.supportsUnifiedApk
            PackageType.SplitApk -> capability.supportsSplitApk
            PackageType.Aab -> capability.supportsAab
        }

    private fun ArtifactDraft.withDownloadedInspection(
        path: String,
        inspection: ArtifactInspection,
    ): ArtifactDraft =
        copy(
            sourceType = ArtifactSourceType.LocalFile,
            value = path,
            downloadedPath = path,
            md5 = inspection.md5,
            sha1 = inspection.sha1,
            sha256 = inspection.sha256,
            packageName = inspection.packageName,
            versionName = inspection.versionName,
            versionCode = inspection.versionCode,
            abiList = inspection.abiList,
            message = buildString {
                append("URL 已下载为 ${inspection.fileName}，${inspection.fileSizeBytes} bytes")
                if (inspection.warnings.isNotEmpty()) {
                    append("；")
                    append(inspection.warnings.joinToString("；"))
                }
            },
        )

    private fun ArtifactDraft.withUrlInspection(
        url: String,
        downloadedPath: String,
        inspection: ArtifactInspection,
    ): ArtifactDraft =
        copy(
            sourceType = ArtifactSourceType.Url,
            value = url,
            downloadedPath = downloadedPath,
            md5 = inspection.md5,
            sha1 = inspection.sha1,
            sha256 = inspection.sha256,
            packageName = inspection.packageName,
            versionName = inspection.versionName,
            versionCode = inspection.versionCode,
            abiList = inspection.abiList,
            message = buildString {
                append("URL 已下载检查 ${inspection.fileName}，${inspection.fileSizeBytes} bytes")
                if (inspection.warnings.isNotEmpty()) {
                    append("；")
                    append(inspection.warnings.joinToString("；"))
                }
            },
        )

    private fun ArtifactPart.withDownloadedInspection(
        path: String,
        inspection: ArtifactInspection,
    ): ArtifactPart =
        copy(
            value = path,
            downloadedPath = path,
            md5 = inspection.md5,
            sha1 = inspection.sha1,
            sha256 = inspection.sha256,
            packageName = inspection.packageName,
            versionName = inspection.versionName,
            versionCode = inspection.versionCode,
            abiList = inspection.abiList,
            message = buildString {
                append("URL 已下载为 ${inspection.fileName}，${inspection.fileSizeBytes} bytes")
                if (inspection.warnings.isNotEmpty()) {
                    append("；")
                    append(inspection.warnings.joinToString("；"))
                }
            },
        )

    private fun ArtifactPart.withUrlInspection(
        url: String,
        downloadedPath: String,
        inspection: ArtifactInspection,
    ): ArtifactPart =
        copy(
            value = url,
            downloadedPath = downloadedPath,
            md5 = inspection.md5,
            sha1 = inspection.sha1,
            sha256 = inspection.sha256,
            packageName = inspection.packageName,
            versionName = inspection.versionName,
            versionCode = inspection.versionCode,
            abiList = inspection.abiList,
            message = buildString {
                append("URL 已下载检查 ${inspection.fileName}，${inspection.fileSizeBytes} bytes")
                if (inspection.warnings.isNotEmpty()) {
                    append("；")
                    append(inspection.warnings.joinToString("；"))
                }
            },
        )

    private fun Result<ArtifactInspection>.requireManifest(label: String): Result<ArtifactInspection> =
        fold(
            onSuccess = { inspection ->
                if (inspection.packageName != null) {
                    Result.success(inspection)
                } else {
                    val reason = inspection.warnings.joinToString("；")
                        .ifBlank { "未读取到 manifest 包名，无法校验包名和版本" }
                    Result.failure(IllegalStateException("$label 检查失败：$reason"))
                }
            },
            onFailure = { Result.failure(it) },
        )

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://") || startsWith("https://")

    private fun LocalStateSnapshot.artifactFor(channel: ChannelRecord): ArtifactDraft =
        when (publishMode) {
            PublishMode.UnifiedArtifact -> unifiedArtifact
            PublishMode.PerChannelArtifact -> channelArtifacts[channel.id] ?: ArtifactDraft()
        }

    private fun LocalStateSnapshot.nextAvailableId(): Int {
        val ids = apps.map { it.id } + channels.map { it.id } + publishTasks.map { it.id }
        return ids.maxOfOrNull { id ->
            id.substringAfterLast("-").toIntOrNull() ?: 0
        }?.plus(1) ?: 1
    }

    private fun downloadProgressLog(
        progressKey: String,
        progressLabel: String,
        messageLabel: String,
        progress: ArtifactTransferProgress,
    ): PublishTaskLog {
        val percent = progress.percent()
        val totalText = progress.totalBytes?.let { " / ${it.readableBytes()}" }.orEmpty()
        val percentText = percent?.let { " ($it%)" }.orEmpty()
        return PublishTaskLog(
            level = LogLevel.Info,
            message = "$messageLabel 下载中：${progress.bytesTransferred.readableBytes()}$totalText$percentText",
            stage = PublishTaskStage.Download,
            progressPercent = percent,
            progressKey = progressKey,
            progressLabel = progressLabel,
        )
    }

    private fun ArtifactTransferProgress.percent(): Int? {
        val total = totalBytes?.takeIf { it > 0L } ?: return null
        return ((bytesTransferred * 100) / total).coerceIn(0, 100).toInt()
    }

    private fun Long.readableBytes(): String {
        val mb = this / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            "${(mb * 10).toInt() / 10.0} MB"
        } else {
            "${this / 1024} KB"
        }
    }

    private fun ArtifactDraft.downloadTarget(
        channel: ChannelRecord,
        publishTimeFolder: String,
    ): ArtifactDownloadTarget =
        ArtifactDownloadTarget(
            marketFolder = channel.marketType.name.lowercase(),
            publishTimeFolder = publishTimeFolder,
            artifactFolder = when (packageType) {
                PackageType.Aab -> "bundle"
                PackageType.Apk -> "apk"
                PackageType.SplitApk -> "apk"
            },
            variantFolder = when (packageType) {
                PackageType.Apk -> "universal"
                PackageType.Aab -> null
                PackageType.SplitApk -> null
            },
        )

    private fun ArtifactDraft.downloadProgressKey(): String =
        when (packageType) {
            PackageType.Aab -> "download:bundle"
            PackageType.Apk -> "download:apk:universal"
            PackageType.SplitApk -> "download:apk:split"
        }

    private fun ArtifactDraft.downloadProgressLabel(): String =
        when (packageType) {
            PackageType.Aab -> "Bundle 下载"
            PackageType.Apk -> "APK 下载"
            PackageType.SplitApk -> "APK 下载"
        }

    private fun splitDownloadProgressKey(variantFolder: String): String =
        "download:apk:$variantFolder"

    private fun splitDownloadTarget(
        channel: ChannelRecord,
        publishTimeFolder: String,
        variantFolder: String,
    ): ArtifactDownloadTarget =
        ArtifactDownloadTarget(
            marketFolder = channel.marketType.name.lowercase(),
            publishTimeFolder = publishTimeFolder,
            artifactFolder = "apk",
            variantFolder = variantFolder,
        )

    private data class PreparedArtifact(
        val artifact: ArtifactDraft,
        val logs: List<PublishTaskLog> = emptyList(),
    )

    private data class PreparedSplitPart(
        val part: ArtifactPart,
        val logs: List<PublishTaskLog> = emptyList(),
    )
}
