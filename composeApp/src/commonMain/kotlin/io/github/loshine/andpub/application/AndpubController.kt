package io.github.loshine.andpub.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.loshine.andpub.domain.market.ApiMarketPublisher
import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelSyncStatus
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.SplitApkSlot
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.domain.storage.LocalStateStore
import io.github.loshine.andpub.platform.createLocalStateStore

class AndpubController(
    publishers: List<MarketPublisher> = ApiMarketPublisher.createAll(),
    private val localStateStore: LocalStateStore = createLocalStateStore(),
) {
    private val publisherMap = publishers.associateBy { it.marketType }
    private var nextId = 1

    val apps = mutableStateListOf<AppRecord>()
    val channels = mutableStateListOf<ChannelRecord>()
    val publishTasks = mutableStateListOf<PublishTaskRecord>()
    val artifactDrafts = mutableStateMapOf<String, ArtifactDraft>()

    var selectedAppId by mutableStateOf<String?>(null)
        private set

    var publishMode by mutableStateOf(PublishMode.UnifiedArtifact)
        private set
    var unifiedArtifactDraft by mutableStateOf(ArtifactDraft())
        private set
    var toolSettings by mutableStateOf(ToolSettings())
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var stateVersion by mutableStateOf(0)
        private set

    val selectedApp: AppRecord?
        get() = apps.firstOrNull { it.id == selectedAppId }

    val selectedChannels: List<ChannelRecord>
        get() = selectedAppId?.let { appId ->
            channels.filter { it.appId == appId }
        } ?: emptyList()

    suspend fun loadState() {
        runCatching { localStateStore.load() }
            .onSuccess { snapshot ->
                if (snapshot != null) {
                    restore(snapshot)
                }
            }
            .onFailure {
                message = "读取本地状态失败：${it.message ?: "未知错误"}"
            }
    }

    suspend fun saveState() {
        val snapshot = LocalStateSnapshot(
            apps = apps.toList(),
            channels = channels.toList(),
            publishTasks = publishTasks.toList(),
            channelArtifacts = artifactDrafts.toMap(),
            unifiedArtifact = unifiedArtifactDraft,
            toolSettings = toolSettings,
            selectedAppId = selectedAppId,
            publishMode = publishMode,
        )
        runCatching { localStateStore.save(snapshot) }
            .onFailure {
                message = "保存本地状态失败：${it.message ?: "未知错误"}"
            }
    }

    fun createApp(name: String, packageName: String) {
        val cleanName = name.trim()
        val cleanPackageName = packageName.trim()
        if (cleanName.isEmpty() || cleanPackageName.isEmpty()) {
            message = "应用名和包名必填"
            return
        }
        if (!isValidPackageName(cleanPackageName)) {
            message = "包名格式不正确"
            return
        }
        if (apps.any { it.packageName == cleanPackageName }) {
            message = "该包名已经存在"
            return
        }

        val app = AppRecord(
            id = newId("app"),
            name = cleanName,
            packageName = cleanPackageName,
        )
        apps += app
        selectedAppId = app.id
        message = "已添加应用"
        markChanged()
    }

    fun selectApp(appId: String) {
        selectedAppId = appId
        message = null
        markChanged()
    }

    fun updatePublishMode(mode: PublishMode) {
        publishMode = mode
        markChanged()
    }

    fun addOrUpdateChannel(
        marketType: MarketType,
        marketAppId: String?,
        credentials: Map<String, String>,
        extraFields: Map<String, String>,
    ) {
        val app = selectedApp ?: return
        val schema = MarketDefinitions.schemaOf(marketType)
        val missingField = schema.credentialFields.firstOrNull { field ->
            field.required && credentials[field.key].orEmpty().isBlank()
        }
        if (missingField != null) {
            message = "${marketType.displayName} 缺少 ${missingField.label}"
            return
        }

        val existingIndex = channels.indexOfFirst {
            it.appId == app.id && it.marketType == marketType
        }
        val channel = ChannelRecord(
            id = channels.getOrNull(existingIndex)?.id ?: newId("channel"),
            appId = app.id,
            marketType = marketType,
            marketAppId = marketAppId?.trim()?.takeIf { it.isNotEmpty() },
            credentials = credentials.mapValues { it.value.trim() },
            extraFields = extraFields.mapValues { it.value.trim() },
            appInfo = channels.getOrNull(existingIndex)?.appInfo,
            syncStatus = channels.getOrNull(existingIndex)?.syncStatus ?: ChannelSyncStatus.NotSynced,
            lastError = null,
        )

        if (existingIndex >= 0) {
            channels[existingIndex] = channel
        } else {
            channels += channel
            artifactDrafts[channel.id] = ArtifactDraft()
        }
        message = "已保存 ${marketType.displayName} 渠道"
        markChanged()
    }

    suspend fun testChannelConfig(
        marketType: MarketType,
        marketAppId: String?,
        credentials: Map<String, String>,
        extraFields: Map<String, String>,
    ): Result<MarketAppInfo> {
        val app = selectedApp
            ?: return Result.failure(IllegalStateException("请先选择应用"))
        val schema = MarketDefinitions.schemaOf(marketType)
        val missingField = schema.credentialFields.firstOrNull { field ->
            field.required && credentials[field.key].orEmpty().isBlank()
        }
        if (missingField != null) {
            val error = IllegalArgumentException("${marketType.displayName} 缺少 ${missingField.label}")
            message = error.message
            return Result.failure(error)
        }
        val publisher = publisherMap[marketType]
            ?: return Result.failure(IllegalStateException("未找到市场适配器"))

        val channel = ChannelRecord(
            id = "test-${marketType.name}",
            appId = app.id,
            marketType = marketType,
            marketAppId = marketAppId?.trim()?.takeIf { it.isNotEmpty() },
            credentials = credentials.mapValues { it.value.trim() },
            extraFields = extraFields.mapValues { it.value.trim() },
        )
        val result = publisher.fetchAppInfo(app, channel)
        message = result.fold(
            onSuccess = { "${marketType.displayName} 连接测试成功" },
            onFailure = { "${marketType.displayName} 连接测试失败：${it.message ?: "未知错误"}" },
        )
        return result
    }

    suspend fun syncChannel(channel: ChannelRecord) {
        val app = apps.firstOrNull { it.id == channel.appId } ?: return
        val publisher = publisherMap[channel.marketType]
        if (publisher == null) {
            updateChannel(
                channel.copy(
                    syncStatus = ChannelSyncStatus.Failed,
                    lastError = "未找到市场适配器",
                )
            )
            return
        }

        updateChannel(channel.copy(syncStatus = ChannelSyncStatus.Syncing, lastError = null))
        val result = publisher.fetchAppInfo(app, channel)
        val latestChannel = channels.firstOrNull { it.id == channel.id } ?: channel
        updateChannel(
            result.fold(
                onSuccess = {
                    latestChannel.copy(
                        appInfo = it,
                        syncStatus = ChannelSyncStatus.Synced,
                        lastError = null,
                    )
                },
                onFailure = {
                    latestChannel.copy(
                        syncStatus = ChannelSyncStatus.Failed,
                        lastError = it.message ?: "查询失败",
                    )
                },
            )
        )
        message = "${channel.marketType.displayName} 应用信息已刷新"
    }

    fun updateUnifiedArtifact(draft: ArtifactDraft) {
        unifiedArtifactDraft = draft
        markChanged()
    }

    fun updateChannelArtifact(channelId: String, draft: ArtifactDraft) {
        artifactDrafts[channelId] = draft
        markChanged()
    }

    fun updateToolSettings(settings: ToolSettings) {
        toolSettings = settings.copy(
            androidSdkPath = settings.androidSdkPath.trim(),
            bundletoolPath = settings.bundletoolPath.trim(),
        )
        message = "工具路径已更新"
        markChanged()
    }

    fun applyInspectionToUnified(path: String, inspection: ArtifactInspection) {
        unifiedArtifactDraft = unifiedArtifactDraft.withInspection(path, inspection)
        markChanged()
    }

    fun applySplitInspectionToUnified(slot: SplitApkSlot, path: String, inspection: ArtifactInspection) {
        unifiedArtifactDraft = unifiedArtifactDraft.withSplitInspection(slot, path, inspection)
        markChanged()
    }

    fun applyInspectionToChannel(channelId: String, path: String, inspection: ArtifactInspection) {
        val current = artifactDrafts[channelId] ?: ArtifactDraft()
        artifactDrafts[channelId] = current.withInspection(path, inspection)
        markChanged()
    }

    fun applySplitInspectionToChannel(
        channelId: String,
        slot: SplitApkSlot,
        path: String,
        inspection: ArtifactInspection,
    ) {
        val current = artifactDrafts[channelId] ?: ArtifactDraft()
        artifactDrafts[channelId] = current.withSplitInspection(slot, path, inspection)
        markChanged()
    }

    fun applyArtifactErrorToUnified(path: String, error: Throwable) {
        unifiedArtifactDraft = unifiedArtifactDraft.copy(
            value = path,
            message = error.message ?: "产物解析失败",
        )
        markChanged()
    }

    fun applySplitArtifactErrorToUnified(slot: SplitApkSlot, path: String, error: Throwable) {
        unifiedArtifactDraft = unifiedArtifactDraft.withSplitError(slot, path, error)
        markChanged()
    }

    fun applyArtifactErrorToChannel(channelId: String, path: String, error: Throwable) {
        val current = artifactDrafts[channelId] ?: ArtifactDraft()
        artifactDrafts[channelId] = current.copy(
            value = path,
            message = error.message ?: "产物解析失败",
        )
        markChanged()
    }

    fun applySplitArtifactErrorToChannel(
        channelId: String,
        slot: SplitApkSlot,
        path: String,
        error: Throwable,
    ) {
        val current = artifactDrafts[channelId] ?: ArtifactDraft()
        artifactDrafts[channelId] = current.withSplitError(slot, path, error)
        markChanged()
    }

    fun createMockPublishTasks() {
        val app = selectedApp
        if (app == null) {
            message = "请先选择应用"
            return
        }
        if (selectedChannels.isEmpty()) {
            message = "请先添加渠道"
            return
        }

        val createdTasks = selectedChannels.mapNotNull { channel ->
            val artifact = when (publishMode) {
                PublishMode.UnifiedArtifact -> unifiedArtifactDraft
                PublishMode.PerChannelArtifact -> artifactDrafts[channel.id] ?: ArtifactDraft()
            }
            val logs = validateArtifact(app.packageName, channel, artifact)
            PublishTaskRecord(
                id = newId("task"),
                appId = app.id,
                channelId = channel.id,
                marketType = channel.marketType,
                publishMode = publishMode,
                artifact = artifact,
                status = if (logs.any { it.level == LogLevel.Error }) {
                    PublishTaskStatus.Failed
                } else {
                    PublishTaskStatus.Ready
                },
                logs = logs,
            )
        }

        publishTasks += createdTasks
        message = "已创建 ${createdTasks.size} 个 mock 发布任务"
        markChanged()
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

    private fun updateChannel(channel: ChannelRecord) {
        val index = channels.indexOfFirst { it.id == channel.id }
        if (index >= 0) {
            channels[index] = channel
            markChanged()
        }
    }

    private fun restore(snapshot: LocalStateSnapshot) {
        apps.clear()
        apps.addAll(snapshot.apps)

        channels.clear()
        channels.addAll(snapshot.channels)

        publishTasks.clear()
        publishTasks.addAll(snapshot.publishTasks)

        artifactDrafts.clear()
        artifactDrafts.putAll(snapshot.channelArtifacts)
        channels.forEach { channel ->
            if (channel.id !in artifactDrafts) {
                artifactDrafts[channel.id] = ArtifactDraft()
            }
        }

        unifiedArtifactDraft = snapshot.unifiedArtifact
        toolSettings = snapshot.toolSettings
        selectedAppId = snapshot.selectedAppId?.takeIf { appId ->
            apps.any { it.id == appId }
        } ?: apps.firstOrNull()?.id
        publishMode = snapshot.publishMode
        nextId = nextAvailableId()
        message = null
        stateVersion = 0
    }

    private fun nextAvailableId(): Int {
        val ids = apps.map { it.id } + channels.map { it.id } + publishTasks.map { it.id }
        return ids.maxOfOrNull { id ->
            id.substringAfterLast("-").toIntOrNull() ?: 0
        }?.plus(1) ?: 1
    }

    private fun markChanged() {
        stateVersion += 1
    }

    private fun ArtifactDraft.withInspection(path: String, inspection: ArtifactInspection): ArtifactDraft =
        copy(
            sourceType = ArtifactSourceType.LocalFile,
            value = path,
            md5 = inspection.md5,
            sha1 = inspection.sha1,
            sha256 = inspection.sha256,
            packageName = inspection.packageName,
            versionName = inspection.versionName,
            versionCode = inspection.versionCode,
            abiList = inspection.abiList,
            message = buildString {
                append("已读取 ${inspection.fileName}，${inspection.fileSizeBytes} bytes")
                if (inspection.warnings.isNotEmpty()) {
                    append("；")
                    append(inspection.warnings.joinToString("；"))
                }
            },
        )

    private fun ArtifactDraft.withSplitInspection(
        slot: SplitApkSlot,
        path: String,
        inspection: ArtifactInspection,
    ): ArtifactDraft {
        val part = ArtifactPart().withInspection(path, inspection)
        return when (slot) {
            SplitApkSlot.Arm32 -> copy(
                sourceType = ArtifactSourceType.LocalFile,
                packageType = PackageType.SplitApk,
                split32 = part,
            )
            SplitApkSlot.Arm64 -> copy(
                sourceType = ArtifactSourceType.LocalFile,
                packageType = PackageType.SplitApk,
                split64 = part,
            )
        }
    }

    private fun ArtifactDraft.withSplitError(
        slot: SplitApkSlot,
        path: String,
        error: Throwable,
    ): ArtifactDraft {
        val part = when (slot) {
            SplitApkSlot.Arm32 -> split32
            SplitApkSlot.Arm64 -> split64
        }.copy(
            value = path,
            message = error.message ?: "产物解析失败",
        )
        return when (slot) {
            SplitApkSlot.Arm32 -> copy(
                sourceType = ArtifactSourceType.LocalFile,
                packageType = PackageType.SplitApk,
                split32 = part,
            )
            SplitApkSlot.Arm64 -> copy(
                sourceType = ArtifactSourceType.LocalFile,
                packageType = PackageType.SplitApk,
                split64 = part,
            )
        }
    }

    private fun ArtifactPart.withInspection(path: String, inspection: ArtifactInspection): ArtifactPart =
        copy(
            value = path,
            md5 = inspection.md5,
            sha1 = inspection.sha1,
            sha256 = inspection.sha256,
            packageName = inspection.packageName,
            versionName = inspection.versionName,
            versionCode = inspection.versionCode,
            abiList = inspection.abiList,
            message = buildString {
                append("已读取 ${inspection.fileName}，${inspection.fileSizeBytes} bytes")
                if (inspection.warnings.isNotEmpty()) {
                    append("；")
                    append(inspection.warnings.joinToString("；"))
                }
            },
        )

    private fun PackageType.isSupportedBy(capability: io.github.loshine.andpub.domain.model.MarketCapability): Boolean =
        when (this) {
            PackageType.Apk -> capability.supportsUnifiedApk
            PackageType.SplitApk -> capability.supportsSplitApk
            PackageType.Aab -> capability.supportsAab
        }

    private fun isValidPackageName(value: String): Boolean {
        val parts = value.split(".")
        return parts.size >= 2 && parts.all { part ->
            part.isNotEmpty() &&
                part.first().isLetter() &&
                part.all { it.isLetterOrDigit() || it == '_' }
        }
    }

    private fun newId(prefix: String): String = "$prefix-${nextId++}"
}
