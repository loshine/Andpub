package io.github.loshine.andpub.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.loshine.andpub.data.remote.wecom.WeComWebhookRemoteDataSource
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.ChannelSyncStatus
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.SplitApkSlot
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.domain.repository.AndpubRepository
import io.github.loshine.andpub.domain.usecase.CreatePublishTasksUseCase
import io.github.loshine.andpub.domain.usecase.FetchMarketAppInfoUseCase
import io.github.loshine.andpub.domain.usecase.ObserveAndpubStateUseCase
import io.github.loshine.andpub.domain.usecase.UpdateAndpubStateUseCase
import io.github.loshine.andpub.domain.usecase.ValidateChannelCredentialsUseCase
import io.github.loshine.andpub.domain.usecase.ValidatePackageNameUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class ChannelTestUiState(
    val isLoading: Boolean = false,
    val info: MarketAppInfo? = null,
    val error: String? = null,
)

data class AndpubUiState(
    val snapshot: LocalStateSnapshot = LocalStateSnapshot(),
    val message: String? = null,
    val channelTests: Map<String, ChannelTestUiState> = emptyMap(),
) {
    val apps: List<AppRecord> = snapshot.apps
    val channels: List<ChannelRecord> = snapshot.channels
    val publishTasks: List<PublishTaskRecord> = snapshot.publishTasks
    val selectedAppId: String? = snapshot.selectedAppId
    val publishMode: PublishMode = snapshot.publishMode
    val unifiedArtifactDraft: ArtifactDraft = snapshot.unifiedArtifact
    val artifactDrafts: Map<String, ArtifactDraft> = snapshot.channelArtifacts
    val toolSettings: ToolSettings = snapshot.toolSettings

    val selectedApp: AppRecord?
        get() = apps.firstOrNull { it.id == selectedAppId }

    val selectedChannels: List<ChannelRecord>
        get() = selectedAppId?.let { appId ->
            channels.filter { it.appId == appId }
        } ?: emptyList()
}

sealed interface AndpubIntent {
    data class CreateApp(val name: String, val packageName: String) : AndpubIntent
    data class UpdateApp(val appId: String, val name: String, val packageName: String) :
        AndpubIntent

    data class DeleteApp(val appId: String) : AndpubIntent
    data class SelectApp(val appId: String) : AndpubIntent
    data class UpdatePublishMode(val mode: PublishMode) : AndpubIntent
    data class UpdateToolSettings(val settings: ToolSettings) : AndpubIntent
    data class AddOrUpdateChannel(
        val channelId: String?,
        val name: String,
        val marketType: MarketType,
        val marketAppId: String?,
        val credentials: Map<String, String>,
        val extraFields: Map<String, String>,
    ) : AndpubIntent

    data class TestChannelConfig(
        val testKey: String,
        val marketType: MarketType,
        val marketAppId: String?,
        val credentials: Map<String, String>,
        val extraFields: Map<String, String>,
    ) : AndpubIntent

    data class SyncChannel(val channel: ChannelRecord) : AndpubIntent
    data class SyncAllChannels(val notify: Boolean) : AndpubIntent
    data class DeleteChannel(val channelId: String) : AndpubIntent
    data class UpdateUnifiedArtifact(val draft: ArtifactDraft) : AndpubIntent
    data class UpdateChannelArtifact(val channelId: String, val draft: ArtifactDraft) : AndpubIntent
    data class ApplyInspectionToUnified(val path: String, val inspection: ArtifactInspection) :
        AndpubIntent

    data class ApplySplitInspectionToUnified(
        val slot: SplitApkSlot,
        val path: String,
        val inspection: ArtifactInspection,
    ) : AndpubIntent

    data class ApplyInspectionToChannel(
        val channelId: String,
        val path: String,
        val inspection: ArtifactInspection,
    ) : AndpubIntent

    data class ApplySplitInspectionToChannel(
        val channelId: String,
        val slot: SplitApkSlot,
        val path: String,
        val inspection: ArtifactInspection,
    ) : AndpubIntent

    data class ApplyArtifactErrorToUnified(val path: String, val error: Throwable) : AndpubIntent
    data class ApplySplitArtifactErrorToUnified(
        val slot: SplitApkSlot,
        val path: String,
        val error: Throwable,
    ) : AndpubIntent

    data class ApplyArtifactErrorToChannel(
        val channelId: String,
        val path: String,
        val error: Throwable,
    ) : AndpubIntent

    data class ApplySplitArtifactErrorToChannel(
        val channelId: String,
        val slot: SplitApkSlot,
        val path: String,
        val error: Throwable,
    ) : AndpubIntent

    data object CreateMockPublishTasks : AndpubIntent
}

@KoinViewModel
class AndpubViewModel(
    repository: AndpubRepository,
    private val fetchMarketAppInfo: FetchMarketAppInfoUseCase,
    private val weComWebhookRemote: WeComWebhookRemoteDataSource,
    private val validatePackageName: ValidatePackageNameUseCase = ValidatePackageNameUseCase(),
    private val validateChannelCredentials: ValidateChannelCredentialsUseCase = ValidateChannelCredentialsUseCase(),
    private val createPublishTasks: CreatePublishTasksUseCase = CreatePublishTasksUseCase(),
) : ViewModel() {
    private val observeState = ObserveAndpubStateUseCase(repository)
    private val updateState = UpdateAndpubStateUseCase(repository)
    private val message = MutableStateFlow<String?>(null)
    private val channelTests = MutableStateFlow<Map<String, ChannelTestUiState>>(emptyMap())

    val uiState: StateFlow<AndpubUiState> = combine(
        observeState(),
        message,
        channelTests,
    ) { snapshot, currentMessage, tests ->
        AndpubUiState(
            snapshot = snapshot.ensureSelectedApp(),
            message = currentMessage,
            channelTests = tests,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AndpubUiState(),
    )

    fun onIntent(intent: AndpubIntent) {
        when (intent) {
            is AndpubIntent.CreateApp -> createApp(intent.name, intent.packageName)
            is AndpubIntent.UpdateApp -> updateApp(intent.appId, intent.name, intent.packageName)
            is AndpubIntent.DeleteApp -> deleteApp(intent.appId)
            is AndpubIntent.SelectApp -> reduce { it.copy(selectedAppId = intent.appId) }
            is AndpubIntent.UpdatePublishMode -> reduce { it.copy(publishMode = intent.mode) }
            is AndpubIntent.UpdateToolSettings -> updateToolSettings(intent.settings)
            is AndpubIntent.AddOrUpdateChannel -> addOrUpdateChannel(intent)
            is AndpubIntent.TestChannelConfig -> testChannelConfig(intent)
            is AndpubIntent.SyncChannel -> syncChannel(intent.channel)
            is AndpubIntent.SyncAllChannels -> syncAllChannels(intent.notify)
            is AndpubIntent.DeleteChannel -> deleteChannel(intent.channelId)
            is AndpubIntent.UpdateUnifiedArtifact -> reduce {
                it.copy(unifiedArtifact = intent.draft)
            }

            is AndpubIntent.UpdateChannelArtifact -> reduce {
                it.copy(channelArtifacts = it.channelArtifacts + (intent.channelId to intent.draft))
            }

            is AndpubIntent.ApplyInspectionToUnified -> reduce {
                it.copy(
                    unifiedArtifact = it.unifiedArtifact.withInspection(
                        intent.path,
                        intent.inspection
                    )
                )
            }

            is AndpubIntent.ApplySplitInspectionToUnified -> reduce {
                it.copy(
                    unifiedArtifact = it.unifiedArtifact.withSplitInspection(
                        intent.slot,
                        intent.path,
                        intent.inspection,
                    )
                )
            }

            is AndpubIntent.ApplyInspectionToChannel -> reduce {
                val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                it.copy(
                    channelArtifacts = it.channelArtifacts + (
                            intent.channelId to current.withInspection(
                                intent.path,
                                intent.inspection
                            )
                            )
                )
            }

            is AndpubIntent.ApplySplitInspectionToChannel -> reduce {
                val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                it.copy(
                    channelArtifacts = it.channelArtifacts + (
                            intent.channelId to current.withSplitInspection(
                                intent.slot,
                                intent.path,
                                intent.inspection
                            )
                            )
                )
            }

            is AndpubIntent.ApplyArtifactErrorToUnified -> reduce {
                it.copy(
                    unifiedArtifact = it.unifiedArtifact.copy(
                        value = intent.path,
                        message = intent.error.message ?: "产物解析失败",
                    )
                )
            }

            is AndpubIntent.ApplySplitArtifactErrorToUnified -> reduce {
                it.copy(
                    unifiedArtifact = it.unifiedArtifact.withSplitError(
                        intent.slot,
                        intent.path,
                        intent.error
                    )
                )
            }

            is AndpubIntent.ApplyArtifactErrorToChannel -> reduce {
                val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                it.copy(
                    channelArtifacts = it.channelArtifacts + (
                            intent.channelId to current.copy(
                                value = intent.path,
                                message = intent.error.message ?: "产物解析失败",
                            )
                            )
                )
            }

            is AndpubIntent.ApplySplitArtifactErrorToChannel -> reduce {
                val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                it.copy(
                    channelArtifacts = it.channelArtifacts + (
                            intent.channelId to current.withSplitError(
                                intent.slot,
                                intent.path,
                                intent.error
                            )
                            )
                )
            }

            AndpubIntent.CreateMockPublishTasks -> createMockPublishTasks()
        }
    }

    private fun createApp(name: String, packageName: String) {
        val cleanName = name.trim()
        val cleanPackageName = packageName.trim()
        val snapshot = uiState.value.snapshot
        when {
            cleanName.isEmpty() || cleanPackageName.isEmpty() -> {
                message.value = "应用名和包名必填"
            }

            !validatePackageName(cleanPackageName) -> {
                message.value = "包名格式不正确"
            }

            snapshot.apps.any { it.packageName == cleanPackageName } -> {
                message.value = "该包名已经存在"
            }

            else -> reduce("已添加应用") {
                val app = AppRecord(
                    id = it.newId("app"),
                    name = cleanName,
                    packageName = cleanPackageName,
                )
                it.copy(
                    apps = it.apps + app,
                    selectedAppId = app.id,
                )
            }
        }
    }

    private fun updateApp(appId: String, name: String, packageName: String) {
        val cleanName = name.trim()
        val cleanPackageName = packageName.trim()
        val snapshot = uiState.value.snapshot
        when {
            cleanName.isEmpty() || cleanPackageName.isEmpty() -> {
                message.value = "应用名和包名必填"
            }

            !validatePackageName(cleanPackageName) -> {
                message.value = "包名格式不正确"
            }

            snapshot.apps.none { it.id == appId } -> {
                message.value = "应用不存在"
            }

            snapshot.apps.any { it.id != appId && it.packageName == cleanPackageName } -> {
                message.value = "该包名已经存在"
            }

            else -> reduce("已更新应用") {
                it.copy(
                    apps = it.apps.map { app ->
                        if (app.id == appId) {
                            app.copy(name = cleanName, packageName = cleanPackageName)
                        } else {
                            app
                        }
                    }
                )
            }
        }
    }

    private fun deleteApp(appId: String) {
        val snapshot = uiState.value.snapshot
        val app = snapshot.apps.firstOrNull { it.id == appId }
        if (app == null) {
            message.value = "应用不存在"
            return
        }

        reduce("已删除应用 ${app.name}") {
            val deletedChannelIds = it.channels
                .filter { channel -> channel.appId == appId }
                .map { channel -> channel.id }
                .toSet()
            val apps = it.apps.filterNot { app -> app.id == appId }
            it.copy(
                apps = apps,
                channels = it.channels.filterNot { channel -> channel.appId == appId },
                publishTasks = it.publishTasks.filterNot { task -> task.appId == appId },
                channelArtifacts = it.channelArtifacts.filterKeys { channelId -> channelId !in deletedChannelIds },
                selectedAppId = apps.firstOrNull { app -> app.id != appId }?.id,
            )
        }
    }

    private fun updateToolSettings(settings: ToolSettings) {
        reduce("工具路径已更新") {
            it.copy(
                toolSettings = settings.copy(
                    androidSdkPath = settings.androidSdkPath.trim(),
                    bundletoolPath = settings.bundletoolPath.trim(),
                    weComWebhookUrl = settings.weComWebhookUrl.trim(),
                )
            )
        }
    }

    private fun addOrUpdateChannel(intent: AndpubIntent.AddOrUpdateChannel) {
        val app = uiState.value.selectedApp ?: return
        val cleanName = intent.name.trim()
        validateChannelCredentials(intent.marketType, intent.credentials)?.let {
            message.value = it
            return
        }

        reduce("已保存 ${intent.marketType.displayName} 渠道") { snapshot ->
            val existingIndex = intent.channelId?.let { channelId ->
                snapshot.channels.indexOfFirst { it.id == channelId && it.appId == app.id }
            } ?: -1
            val existing = snapshot.channels.getOrNull(existingIndex)
            val marketAppId = intent.marketAppId?.trim()?.takeIf { it.isNotEmpty() }
            val credentials = intent.credentials.mapValues { it.value.trim() }
            val extraFields = intent.extraFields.mapValues { it.value.trim() }
            val configChanged = existing?.let {
                it.marketType != intent.marketType ||
                        it.marketAppId != marketAppId ||
                        it.credentials != credentials ||
                        it.extraFields != extraFields
            } ?: false
            val channel = ChannelRecord(
                id = existing?.id ?: snapshot.newId("channel"),
                appId = app.id,
                name = cleanName,
                marketType = intent.marketType,
                marketAppId = marketAppId,
                credentials = credentials,
                extraFields = extraFields,
                appInfo = existing?.appInfo.takeUnless { configChanged },
                syncStatus = if (configChanged) ChannelSyncStatus.NotSynced else existing?.syncStatus
                    ?: ChannelSyncStatus.NotSynced,
                lastError = null,
            )
            val channels = snapshot.channels.toMutableList()
            if (existingIndex >= 0) {
                channels[existingIndex] = channel
            } else {
                channels += channel
            }
            snapshot.copy(
                channels = channels,
                channelArtifacts = snapshot.channelArtifacts + (channel.id to (snapshot.channelArtifacts[channel.id]
                    ?: ArtifactDraft())),
            )
        }
    }

    private fun deleteChannel(channelId: String) {
        val snapshot = uiState.value.snapshot
        val channel = snapshot.channels.firstOrNull { it.id == channelId }
        if (channel == null) {
            message.value = "渠道不存在"
            return
        }

        reduce("已删除 ${channel.marketType.displayName} 渠道") {
            it.copy(
                channels = it.channels.filterNot { channel -> channel.id == channelId },
                publishTasks = it.publishTasks.filterNot { task -> task.channelId == channelId },
                channelArtifacts = it.channelArtifacts - channelId,
            )
        }
    }

    private fun testChannelConfig(intent: AndpubIntent.TestChannelConfig) {
        val app = uiState.value.selectedApp
        if (app == null) {
            message.value = "请先选择应用"
            return
        }
        validateChannelCredentials(intent.marketType, intent.credentials)?.let {
            message.value = it
            channelTests.updateTest(intent.testKey, ChannelTestUiState(error = it))
            return
        }

        viewModelScope.launch {
            channelTests.updateTest(intent.testKey, ChannelTestUiState(isLoading = true))
            val channel = ChannelRecord(
                id = "test-${intent.marketType.name}",
                appId = app.id,
                marketType = intent.marketType,
                marketAppId = intent.marketAppId?.trim()?.takeIf { it.isNotEmpty() },
                credentials = intent.credentials.mapValues { it.value.trim() },
                extraFields = intent.extraFields.mapValues { it.value.trim() },
            )
            val result = fetchMarketAppInfo(app, channel)
            result.fold(
                onSuccess = {
                    channelTests.updateTest(intent.testKey, ChannelTestUiState(info = it))
                    message.value = "${intent.marketType.displayName} 连接测试成功"
                },
                onFailure = {
                    val error = it.message ?: "测试连接失败"
                    channelTests.updateTest(intent.testKey, ChannelTestUiState(error = error))
                    message.value = "${intent.marketType.displayName} 连接测试失败：$error"
                },
            )
        }
    }

    private fun syncChannel(channel: ChannelRecord) {
        val app = uiState.value.apps.firstOrNull { it.id == channel.appId } ?: return
        viewModelScope.launch {
            syncChannelInfo(app, channel)
            message.value = "${channel.marketType.displayName} 应用信息已刷新"
        }
    }

    private fun syncAllChannels(notify: Boolean) {
        val state = uiState.value
        val app = state.selectedApp
        if (app == null) {
            message.value = "请先选择应用"
            return
        }
        if (state.selectedChannels.isEmpty()) {
            message.value = "请先添加渠道"
            return
        }

        viewModelScope.launch {
            message.value = "正在查询全部渠道..."
            val syncedChannels = state.selectedChannels.map { channel ->
                syncChannelInfo(app, channel)
            }
            if (!notify) {
                message.value = "已查询全部渠道"
                return@launch
            }
            val webhookUrl = uiState.value.toolSettings.weComWebhookUrl.trim()
            if (webhookUrl.isEmpty()) {
                message.value = "已查询全部渠道；未配置企业微信 WebHook，未发送通知"
                return@launch
            }

            runCatching {
                weComWebhookRemote.sendMarkdown(
                    webhookUrl = webhookUrl,
                    content = buildMarketVersionReport(app, syncedChannels),
                )
            }.fold(
                onSuccess = { message.value = "已查询全部渠道并发送企业微信通知" },
                onFailure = { message.value = "已查询全部渠道，企业微信通知失败：${it.message ?: "未知错误"}" },
            )
        }
    }

    private suspend fun syncChannelInfo(
        app: AppRecord,
        channel: ChannelRecord,
    ): ChannelRecord {
        updateState {
            it.withUpdatedChannel(
                channel.copy(
                    appInfo = null,
                    syncStatus = ChannelSyncStatus.Syncing,
                    lastError = null,
                )
            )
        }
        val result = fetchMarketAppInfo(app, channel)
        var syncedChannel = channel
        updateState { snapshot ->
            val latest = snapshot.channels.firstOrNull { it.id == channel.id } ?: channel
            syncedChannel = result.fold(
                onSuccess = {
                    latest.copy(
                        appInfo = it,
                        syncStatus = ChannelSyncStatus.Synced,
                        lastError = null,
                    )
                },
                onFailure = {
                    latest.copy(
                        appInfo = null,
                        syncStatus = ChannelSyncStatus.Failed,
                        lastError = it.message ?: "查询失败",
                    )
                },
            )
            snapshot.withUpdatedChannel(syncedChannel)
        }
        return syncedChannel
    }

    private fun createMockPublishTasks() {
        val state = uiState.value
        val app = state.selectedApp
        if (app == null) {
            message.value = "请先选择应用"
            return
        }
        if (state.selectedChannels.isEmpty()) {
            message.value = "请先添加渠道"
            return
        }

        reduce("已创建 ${state.selectedChannels.size} 个 mock 发布任务") { snapshot ->
            val tasks = createPublishTasks(snapshot, app, state.selectedChannels)
            snapshot.copy(publishTasks = snapshot.publishTasks + tasks)
        }
    }

    private fun updateChannel(channel: ChannelRecord) {
        viewModelScope.launch {
            updateState { it.withUpdatedChannel(channel) }
        }
    }

    private fun reduce(
        nextMessage: String? = null,
        transform: (LocalStateSnapshot) -> LocalStateSnapshot,
    ) {
        viewModelScope.launch {
            runCatching { updateState(transform) }
                .onSuccess { message.value = nextMessage }
                .onFailure { message.value = "保存本地状态失败：${it.message ?: "未知错误"}" }
        }
    }

    private fun LocalStateSnapshot.withUpdatedChannel(channel: ChannelRecord): LocalStateSnapshot {
        val index = channels.indexOfFirst { it.id == channel.id }
        if (index < 0) return this
        val updatedChannels = channels.toMutableList()
        updatedChannels[index] = channel
        return copy(channels = updatedChannels)
    }

    private fun LocalStateSnapshot.ensureSelectedApp(): LocalStateSnapshot =
        copy(
            selectedAppId = selectedAppId?.takeIf { appId ->
                apps.any { it.id == appId }
            } ?: apps.firstOrNull()?.id,
            channelArtifacts = channels.fold(channelArtifacts) { artifacts, channel ->
                if (channel.id in artifacts) artifacts else artifacts + (channel.id to ArtifactDraft())
            },
        )

    private fun LocalStateSnapshot.newId(prefix: String): String =
        "$prefix-${nextAvailableId()}"

    private fun LocalStateSnapshot.nextAvailableId(): Int {
        val ids = apps.map { it.id } + channels.map { it.id } + publishTasks.map { it.id }
        return ids.maxOfOrNull { id ->
            id.substringAfterLast("-").toIntOrNull() ?: 0
        }?.plus(1) ?: 1
    }

    private fun MutableStateFlow<Map<String, ChannelTestUiState>>.updateTest(
        testKey: String,
        state: ChannelTestUiState,
    ) {
        value = value + (testKey to state)
    }

    private fun ArtifactDraft.withInspection(
        path: String,
        inspection: ArtifactInspection
    ): ArtifactDraft =
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

    private fun ArtifactPart.withInspection(
        path: String,
        inspection: ArtifactInspection
    ): ArtifactPart =
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

}

private fun buildMarketVersionReport(
    app: AppRecord,
    channels: List<ChannelRecord>,
): String =
    buildString {
        appendLine("**应用市场版本巡检**")
        appendLine("> 应用：${app.name}")
        appendLine("> 包名：${app.packageName}")
        appendLine()
        channels.forEach { channel ->
            appendLine("**${channel.reportTitle()}**")
            val info = channel.appInfo
            if (info == null) {
                appendLine("- 状态：查询失败")
                appendLine("- 错误：${channel.lastError ?: "未知错误"}")
            } else {
                appendLine("- 线上版本：${info.onlineVersion ?: "-"}")
                appendLine("- 上架状态：${info.releaseStatus ?: "其它状态"}")
            }
            appendLine()
        }
    }.trimEnd()

private fun ChannelRecord.reportTitle(): String =
    name.takeIf { it.isNotBlank() } ?: marketType.displayName
