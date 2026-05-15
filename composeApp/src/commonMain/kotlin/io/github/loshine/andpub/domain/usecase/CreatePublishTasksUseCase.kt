package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
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
import io.github.loshine.andpub.domain.model.PublishTaskStatus

class CreatePublishTasksUseCase {
    operator fun invoke(
        snapshot: LocalStateSnapshot,
        app: AppRecord,
        channels: List<ChannelRecord>,
    ): List<PublishTaskRecord> {
        var nextId = snapshot.nextAvailableId()
        return channels.map { channel ->
            val artifact = when (snapshot.publishMode) {
                PublishMode.UnifiedArtifact -> snapshot.unifiedArtifact
                PublishMode.PerChannelArtifact -> snapshot.channelArtifacts[channel.id] ?: ArtifactDraft()
            }
            val logs = validateArtifact(app.packageName, channel, artifact)
            PublishTaskRecord(
                id = "task-${nextId++}",
                appId = app.id,
                channelId = channel.id,
                marketType = channel.marketType,
                publishMode = snapshot.publishMode,
                artifact = artifact,
                status = if (logs.any { it.level == LogLevel.Error }) {
                    PublishTaskStatus.Failed
                } else {
                    PublishTaskStatus.Ready
                },
                logs = logs,
            )
        }
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
            val urlValid = artifact.value.startsWith("http://") || artifact.value.startsWith("https://")
            if (!urlValid) {
                logs += PublishTaskLog(LogLevel.Error, "URL 格式不正确")
            }
            if (!capability.supportsUserUrl) {
                logs += PublishTaskLog(LogLevel.Error, "${channel.marketType.displayName} 不支持用户 URL 拉包")
            }
            logs += PublishTaskLog(LogLevel.Warning, "URL 产物无法在本地完整解析包名和版本，请确认 hash 与市场解析结果")
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
            logs += PublishTaskLog(LogLevel.Error, "32/64 APK 当前阶段仅支持本地文件")
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

    private fun LocalStateSnapshot.nextAvailableId(): Int {
        val ids = apps.map { it.id } + channels.map { it.id } + publishTasks.map { it.id }
        return ids.maxOfOrNull { id ->
            id.substringAfterLast("-").toIntOrNull() ?: 0
        }?.plus(1) ?: 1
    }
}
