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
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.SplitApkSlot
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.domain.model.VivoPublishOptions
import io.github.loshine.andpub.domain.model.displayAuditStatus
import io.github.loshine.andpub.domain.model.displayOnlineVersion
import io.github.loshine.andpub.domain.model.displayReviewingVersion
import io.github.loshine.andpub.domain.repository.AndpubRepository
import io.github.loshine.andpub.domain.usecase.BuildAppSettingsExportUseCase
import io.github.loshine.andpub.domain.usecase.ImportAppSettingsUseCase
import io.github.loshine.andpub.domain.usecase.CreatePublishTasksUseCase
import io.github.loshine.andpub.domain.usecase.ExecutePublishTasksUseCase
import io.github.loshine.andpub.domain.usecase.FetchMarketAppInfoUseCase
import io.github.loshine.andpub.domain.usecase.ObserveAndpubStateUseCase
import io.github.loshine.andpub.domain.usecase.RefreshPublishTaskStatusUseCase
import io.github.loshine.andpub.domain.usecase.UpdateAndpubStateUseCase
import io.github.loshine.andpub.domain.usecase.ValidateChannelCredentialsUseCase
import io.github.loshine.andpub.domain.usecase.ValidatePackageNameUseCase
import io.github.loshine.andpub.platform.openTextFile
import io.github.loshine.andpub.platform.saveTextFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

data class UiMessage(
    val id: Long,
    val text: String,
    val isError: Boolean = false,
)

data class BusyFlags(
    val importing: Boolean = false,
    val exporting: Boolean = false,
    val syncingAll: Boolean = false,
) {
    val anyBusy: Boolean get() = importing || exporting || syncingAll
}

data class AndpubUiState(
    val snapshot: LocalStateSnapshot = LocalStateSnapshot(),
    val uiMessage: UiMessage? = null,
    val busy: BusyFlags = BusyFlags(),
    val isCreatingPublishTasks: Boolean = false,
    val vivoProductionConfirmed: Boolean = false,
    val channelTests: Map<String, ChannelTestUiState> = emptyMap(),
) {
    val message: String? get() = uiMessage?.text
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

    val publishTargetChannels: List<ChannelRecord>
        get() {
            val targetIds = snapshot.publishChannelIds.toSet()
            return selectedChannels.filter { it.id in targetIds }
        }
}

sealed interface AndpubIntent {
    data class CreateApp(val name: String, val packageName: String) : AndpubIntent
    data class UpdateApp(val appId: String, val name: String, val packageName: String) :
        AndpubIntent

    data class DeleteApp(val appId: String) : AndpubIntent
    data class SelectApp(val appId: String) : AndpubIntent
    data object ExportSelectedAppSettings : AndpubIntent
    data object ImportAppSettings : AndpubIntent
    data class UpdatePublishMode(val mode: PublishMode) : AndpubIntent
    data class UpdateVivoPublishOptions(val options: VivoPublishOptions) : AndpubIntent
    data class UpdateVivoProductionConfirmed(val confirmed: Boolean) : AndpubIntent
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
    data class TogglePublishChannel(val channelId: String, val selected: Boolean) : AndpubIntent
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

    data object CreatePublishTasks : AndpubIntent
    data object RetryFailedPublishTasks : AndpubIntent
    data class RefreshPublishTaskStatus(val taskId: String) : AndpubIntent
    /** Clears the current snackbar only when [messageId] still matches. */
    data class DismissMessage(val messageId: Long) : AndpubIntent
}

@KoinViewModel
class AndpubViewModel(
    repository: AndpubRepository,
    private val fetchMarketAppInfo: FetchMarketAppInfoUseCase,
    private val executePublishTasks: ExecutePublishTasksUseCase,
    private val refreshPublishTaskStatus: RefreshPublishTaskStatusUseCase,
    private val weComWebhookRemote: WeComWebhookRemoteDataSource,
    private val validatePackageName: ValidatePackageNameUseCase = ValidatePackageNameUseCase(),
    private val validateChannelCredentials: ValidateChannelCredentialsUseCase = ValidateChannelCredentialsUseCase(),
    private val createPublishTaskRecords: CreatePublishTasksUseCase = CreatePublishTasksUseCase(),
    private val buildAppSettingsExport: BuildAppSettingsExportUseCase = BuildAppSettingsExportUseCase(),
    private val importAppSettingsUseCase: ImportAppSettingsUseCase = ImportAppSettingsUseCase(),
) : ViewModel() {
    private val observeState = ObserveAndpubStateUseCase(repository)
    private val updateState = UpdateAndpubStateUseCase(repository)
    private val message = MutableStateFlow<UiMessage?>(null)
    private var messageSeq = 0L
    private val busyFlags = MutableStateFlow(BusyFlags())
    private val isCreatingPublishTasks = MutableStateFlow(false)
    private val vivoProductionConfirmed = MutableStateFlow(false)
    private val channelTests = MutableStateFlow<Map<String, ChannelTestUiState>>(emptyMap())

    private data class EphemeralUi(
        val message: UiMessage? = null,
        val busy: BusyFlags = BusyFlags(),
        val isCreatingPublishTasks: Boolean = false,
        val vivoProductionConfirmed: Boolean = false,
        val channelTests: Map<String, ChannelTestUiState> = emptyMap(),
    )

    val uiState: StateFlow<AndpubUiState> = combine(
        observeState(),
        combine(message, busyFlags, isCreatingPublishTasks, vivoProductionConfirmed, channelTests) {
                currentMessage, busy, creatingTasks, productionConfirmed, tests ->
            EphemeralUi(currentMessage, busy, creatingTasks, productionConfirmed, tests)
        },
    ) { snapshot, ephemeral ->
        AndpubUiState(
            snapshot = snapshot.ensureSelectedApp().withoutPersistedVivoProductionConfirmation(),
            uiMessage = ephemeral.message,
            busy = ephemeral.busy,
            isCreatingPublishTasks = ephemeral.isCreatingPublishTasks,
            vivoProductionConfirmed = ephemeral.vivoProductionConfirmed,
            channelTests = ephemeral.channelTests,
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
            is AndpubIntent.SelectApp -> {
                clearVivoProductionConfirmation()
                reduce { it.copy(selectedAppId = intent.appId) }
            }
            AndpubIntent.ExportSelectedAppSettings -> exportSelectedAppSettings()
            AndpubIntent.ImportAppSettings -> importAppSettings()
            is AndpubIntent.UpdatePublishMode -> {
                clearVivoProductionConfirmation()
                reduce { it.copy(publishMode = intent.mode) }
            }
            is AndpubIntent.UpdateVivoPublishOptions -> {
                clearVivoProductionConfirmation()
                reduce {
                    it.copy(vivoPublishOptions = intent.options.withoutProductionConfirmation())
                }
            }
            is AndpubIntent.UpdateVivoProductionConfirmed -> {
                vivoProductionConfirmed.value = intent.confirmed
            }
            is AndpubIntent.UpdateToolSettings -> updateToolSettings(intent.settings)
            is AndpubIntent.AddOrUpdateChannel -> {
                clearVivoProductionConfirmation()
                addOrUpdateChannel(intent)
            }
            is AndpubIntent.TestChannelConfig -> testChannelConfig(intent)
            is AndpubIntent.SyncChannel -> syncChannel(intent.channel)
            is AndpubIntent.SyncAllChannels -> syncAllChannels(intent.notify)
            is AndpubIntent.DeleteChannel -> {
                clearVivoProductionConfirmation()
                deleteChannel(intent.channelId)
            }
            is AndpubIntent.TogglePublishChannel -> {
                clearVivoProductionConfirmation()
                togglePublishChannel(intent.channelId, intent.selected)
            }
            is AndpubIntent.UpdateUnifiedArtifact -> {
                clearVivoProductionConfirmation()
                reduce { it.copy(unifiedArtifact = intent.draft) }
            }

            is AndpubIntent.UpdateChannelArtifact -> {
                clearVivoProductionConfirmation()
                reduce {
                    it.copy(channelArtifacts = it.channelArtifacts + (intent.channelId to intent.draft))
                }
            }

            is AndpubIntent.ApplyInspectionToUnified -> {
                clearVivoProductionConfirmation()
                reduce {
                    // Drop stale results if the user already selected another path.
                    if (it.unifiedArtifact.isStaleInspection(intent.path)) it
                    else {
                        it.copy(
                            unifiedArtifact = it.unifiedArtifact.withInspection(
                                intent.path,
                                intent.inspection,
                            ),
                        )
                    }
                }
            }

            is AndpubIntent.ApplySplitInspectionToUnified -> {
                clearVivoProductionConfirmation()
                reduce {
                    if (it.unifiedArtifact.isStaleSplitInspection(intent.slot, intent.path)) it
                    else {
                        it.copy(
                            unifiedArtifact = it.unifiedArtifact.withSplitInspection(
                                intent.slot,
                                intent.path,
                                intent.inspection,
                            ),
                        )
                    }
                }
            }

            is AndpubIntent.ApplyInspectionToChannel -> {
                clearVivoProductionConfirmation()
                reduce {
                    val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                    if (current.isStaleInspection(intent.path)) it
                    else {
                        it.copy(
                            channelArtifacts = it.channelArtifacts + (
                                intent.channelId to current.withInspection(
                                    intent.path,
                                    intent.inspection,
                                )
                            ),
                        )
                    }
                }
            }

            is AndpubIntent.ApplySplitInspectionToChannel -> {
                clearVivoProductionConfirmation()
                reduce {
                    val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                    if (current.isStaleSplitInspection(intent.slot, intent.path)) it
                    else {
                        it.copy(
                            channelArtifacts = it.channelArtifacts + (
                                intent.channelId to current.withSplitInspection(
                                    intent.slot,
                                    intent.path,
                                    intent.inspection,
                                )
                            ),
                        )
                    }
                }
            }

            is AndpubIntent.ApplyArtifactErrorToUnified -> {
                clearVivoProductionConfirmation()
                reduce {
                    if (it.unifiedArtifact.isStaleInspection(intent.path)) it
                    else {
                        it.copy(
                            unifiedArtifact = it.unifiedArtifact.copy(
                                value = intent.path,
                                message = intent.error.message ?: "产物解析失败",
                            ),
                        )
                    }
                }
            }

            is AndpubIntent.ApplySplitArtifactErrorToUnified -> {
                clearVivoProductionConfirmation()
                reduce {
                    if (it.unifiedArtifact.isStaleSplitInspection(intent.slot, intent.path)) it
                    else {
                        it.copy(
                            unifiedArtifact = it.unifiedArtifact.withSplitError(
                                intent.slot,
                                intent.path,
                                intent.error,
                            ),
                        )
                    }
                }
            }

            is AndpubIntent.ApplyArtifactErrorToChannel -> {
                clearVivoProductionConfirmation()
                reduce {
                    val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                    if (current.isStaleInspection(intent.path)) it
                    else {
                        it.copy(
                            channelArtifacts = it.channelArtifacts + (
                                intent.channelId to current.copy(
                                    value = intent.path,
                                    message = intent.error.message ?: "产物解析失败",
                                )
                            ),
                        )
                    }
                }
            }

            is AndpubIntent.ApplySplitArtifactErrorToChannel -> {
                clearVivoProductionConfirmation()
                reduce {
                    val current = it.channelArtifacts[intent.channelId] ?: ArtifactDraft()
                    if (current.isStaleSplitInspection(intent.slot, intent.path)) it
                    else {
                        it.copy(
                            channelArtifacts = it.channelArtifacts + (
                                intent.channelId to current.withSplitError(
                                    intent.slot,
                                    intent.path,
                                    intent.error,
                                )
                            ),
                        )
                    }
                }
            }

            AndpubIntent.CreatePublishTasks -> createPublishTasks()
            AndpubIntent.RetryFailedPublishTasks -> retryFailedPublishTasks()
            is AndpubIntent.RefreshPublishTaskStatus -> refreshPublishStatus(intent.taskId)
            is AndpubIntent.DismissMessage -> {
                if (message.value?.id == intent.messageId) {
                    message.value = null
                }
            }
        }
    }

    private fun postMessage(text: String, isError: Boolean = false) {
        message.value = UiMessage(id = ++messageSeq, text = text, isError = isError)
    }

    /**
     * Stale when the draft already points at a *different* non-empty path
     * (user moved on). Matching path or empty path (commit still in flight) is accepted.
     */
    private fun ArtifactDraft.isStaleInspection(path: String): Boolean =
        value.isNotBlank() && value != path

    private fun ArtifactDraft.isStaleSplitInspection(slot: SplitApkSlot, path: String): Boolean {
        val current = when (slot) {
            SplitApkSlot.Arm32 -> split32.value
            SplitApkSlot.Arm64 -> split64.value
        }
        return current.isNotBlank() && current != path
    }

    private fun createApp(name: String, packageName: String) {
        val cleanName = name.trim()
        val cleanPackageName = packageName.trim()
        val snapshot = uiState.value.snapshot
        when {
            cleanName.isEmpty() || cleanPackageName.isEmpty() -> {
                postMessage("应用名和包名必填", isError = true)
            }

            !validatePackageName(cleanPackageName) -> {
                postMessage("包名格式不正确", isError = true)
            }

            snapshot.apps.any { it.packageName == cleanPackageName } -> {
                postMessage("该包名已经存在", isError = true)
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
                postMessage("应用名和包名必填", isError = true)
            }

            !validatePackageName(cleanPackageName) -> {
                postMessage("包名格式不正确", isError = true)
            }

            snapshot.apps.none { it.id == appId } -> {
                postMessage("应用不存在", isError = true)
            }

            snapshot.apps.any { it.id != appId && it.packageName == cleanPackageName } -> {
                postMessage("该包名已经存在", isError = true)
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
            postMessage("应用不存在", isError = true)
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
                publishChannelIds = it.publishChannelIds.filterNot { channelId -> channelId in deletedChannelIds },
                selectedAppId = apps.firstOrNull { app -> app.id != appId }?.id,
            )
        }
    }

    private fun exportSelectedAppSettings() {
        val state = uiState.value
        val app = state.selectedApp
        if (app == null) {
            postMessage("请先选择应用", isError = true)
            return
        }
        viewModelScope.launch {
            busyFlags.value = busyFlags.value.copy(exporting = true)
            try {
                runCatching {
                    saveTextFile(
                        defaultFileName = buildAppSettingsExport.defaultFileName(app),
                        content = buildAppSettingsExport(app, state.selectedChannels),
                    )
                }.fold(
                    onSuccess = { path ->
                        if (path == null) postMessage("已取消导出")
                        else postMessage("已导出应用设置：$path")
                    },
                    onFailure = {
                        postMessage("导出应用设置失败：${it.message ?: "未知错误"}", isError = true)
                    },
                )
            } finally {
                busyFlags.value = busyFlags.value.copy(exporting = false)
            }
        }
    }

    private fun importAppSettings() {
        viewModelScope.launch {
            busyFlags.value = busyFlags.value.copy(importing = true)
            try {
                runCatching { openTextFile("导入应用设置") }
                    .onFailure {
                        postMessage("打开文件失败：${it.message ?: "未知错误"}", isError = true)
                        return@launch
                    }
                    .onSuccess { json ->
                        if (json == null) {
                            postMessage("已取消导入")
                            return@launch
                        }
                        val snapshot = uiState.value.snapshot
                        importAppSettingsUseCase(json, snapshot)
                            .onSuccess { result ->
                                reduce {
                                    var s = it
                                    val existingApp = s.apps.firstOrNull { a -> a.id == result.app.id }
                                    if (existingApp == null) {
                                        s = s.copy(
                                            apps = s.apps + result.app,
                                            selectedAppId = result.app.id,
                                        )
                                    }
                                    s = s.copy(
                                        channels = s.channels + result.channels,
                                        channelArtifacts = s.channelArtifacts +
                                                result.channels.associate { ch -> ch.id to ArtifactDraft() },
                                    )
                                    s
                                }
                                val appLabel = result.app.name
                                val channelCount = result.channels.size
                                val warnings = result.warnings
                                postMessage(
                                    buildString {
                                        append("已导入应用「$appLabel」，新增 $channelCount 个渠道")
                                        if (warnings.isNotEmpty()) {
                                            append("；")
                                            append(warnings.joinToString("；"))
                                        }
                                    },
                                    isError = warnings.isNotEmpty(),
                                )
                            }
                            .onFailure {
                                postMessage("导入失败：${it.message ?: "未知错误"}", isError = true)
                            }
                    }
            } finally {
                busyFlags.value = busyFlags.value.copy(importing = false)
            }
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

    private fun togglePublishChannel(channelId: String, selected: Boolean) {
        reduce {
            val channelIds = if (selected) {
                it.publishChannelIds + channelId
            } else {
                it.publishChannelIds - channelId
            }
            it.copy(publishChannelIds = channelIds.distinct())
        }
    }

    private fun addOrUpdateChannel(intent: AndpubIntent.AddOrUpdateChannel) {
        val app = uiState.value.selectedApp ?: return
        val cleanName = intent.name.trim()
        validateChannelCredentials(intent.marketType, intent.credentials)?.let {
            postMessage(it, isError = true)
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
            postMessage("渠道不存在", isError = true)
            return
        }

        reduce("已删除 ${channel.marketType.displayName} 渠道") {
            it.copy(
                channels = it.channels.filterNot { channel -> channel.id == channelId },
                publishTasks = it.publishTasks.filterNot { task -> task.channelId == channelId },
                channelArtifacts = it.channelArtifacts - channelId,
                publishChannelIds = it.publishChannelIds - channelId,
            )
        }
    }

    private fun testChannelConfig(intent: AndpubIntent.TestChannelConfig) {
        val app = uiState.value.selectedApp
        if (app == null) {
            postMessage("请先选择应用", isError = true)
            return
        }
        validateChannelCredentials(intent.marketType, intent.credentials)?.let {
            postMessage(it, isError = true)
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
                    postMessage("${intent.marketType.displayName} 连接测试成功")
                },
                onFailure = {
                    val error = it.message ?: "测试连接失败"
                    channelTests.updateTest(intent.testKey, ChannelTestUiState(error = error))
                    postMessage("${intent.marketType.displayName} 连接测试失败：$error", isError = true)
                },
            )
        }
    }

    private fun syncChannel(channel: ChannelRecord) {
        val app = uiState.value.apps.firstOrNull { it.id == channel.appId } ?: return
        viewModelScope.launch {
            syncChannelInfo(app, channel)
            postMessage("${channel.marketType.displayName} 应用信息已刷新")
        }
    }

    private fun syncAllChannels(notify: Boolean) {
        val state = uiState.value
        val app = state.selectedApp
        if (app == null) {
            postMessage("请先选择应用", isError = true)
            return
        }
        if (state.selectedChannels.isEmpty()) {
            postMessage("请先添加渠道", isError = true)
            return
        }

        viewModelScope.launch {
            busyFlags.value = busyFlags.value.copy(syncingAll = true)
            try {
                postMessage("正在查询全部渠道...")
                val syncedChannels = state.selectedChannels.map { channel ->
                    syncChannelInfo(app, channel)
                }
                if (!notify) {
                    postMessage("已查询全部渠道")
                    return@launch
                }
                val webhookUrl = uiState.value.toolSettings.weComWebhookUrl.trim()
                if (webhookUrl.isEmpty()) {
                    postMessage("已查询全部渠道；未配置企业微信 WebHook，未发送通知")
                    return@launch
                }

                runCatching {
                    weComWebhookRemote.sendMarkdown(
                        webhookUrl = webhookUrl,
                        content = buildMarketVersionReport(app, syncedChannels),
                    )
                }.fold(
                    onSuccess = { postMessage("已查询全部渠道并发送企业微信通知") },
                    onFailure = {
                        postMessage(
                            "已查询全部渠道，企业微信通知失败：${it.message ?: "未知错误"}",
                            isError = true,
                        )
                    },
                )
            } finally {
                busyFlags.value = busyFlags.value.copy(syncingAll = false)
            }
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

    private fun createPublishTasks() {
        val state = uiState.value
        val app = state.selectedApp
        val blockers = PublishGuards.blockers(state)
        if (app == null || blockers.isNotEmpty()) {
            postMessage(blockers.firstOrNull() ?: "请先选择应用", isError = true)
            return
        }

        viewModelScope.launch {
            isCreatingPublishTasks.value = true
            postMessage("正在创建并执行发布任务...")
            val vivoOptions = state.snapshot.vivoPublishOptions.copy(
                productionConfirmed = state.vivoProductionConfirmed,
            )
            var pendingTasks = emptyList<PublishTaskRecord>()
            try {
                runCatching {
                    pendingTasks = createPublishTaskRecords.createPendingTasks(
                        snapshot = state.snapshot,
                        app = app,
                        channels = state.publishTargetChannels,
                    )
                    replacePublishTasksForApp(app.id, pendingTasks)

                    val tasks = createPublishTaskRecords(
                        snapshot = state.snapshot,
                        app = app,
                        channels = state.publishTargetChannels,
                        onTaskLog = ::appendPublishTaskLog,
                    )
                    replacePublishTasksForApp(app.id, tasks)
                    val channelById = state.publishTargetChannels.associateBy { it.id }
                    coroutineScope {
                        tasks.map { task ->
                            async {
                                val channel = channelById[task.channelId]
                                if (channel == null) {
                                    task.copy(
                                        status = PublishTaskStatus.Failed,
                                        logs = task.logs + PublishTaskLog(
                                            level = LogLevel.Error,
                                            message = "渠道不存在",
                                            stage = PublishTaskStage.Result,
                                        ),
                                    ).also { updatePublishTask(it) }
                                } else {
                                    val runningTask = task.asRunningPublishTask()
                                    if (runningTask !== task) {
                                        updatePublishTask(runningTask)
                                    }
                                    executePublishTasks(
                                        app = app,
                                        channel = channel,
                                        task = runningTask,
                                        vivoOptions = vivoOptions,
                                        onTaskLog = { log -> appendPublishTaskLog(runningTask.id, log) },
                                    ).also { updatePublishTask(it) }
                                }
                            }
                        }.awaitAll()
                    }
                }.onSuccess {
                    postMessage("已创建并执行 ${it.size} 个发布任务")
                }.onFailure {
                    val errorMessage = it.message ?: "未知错误"
                    if (pendingTasks.isNotEmpty()) {
                        replacePublishTasksForApp(
                            app.id,
                            pendingTasks.map { task ->
                                task.copy(
                                    status = PublishTaskStatus.Failed,
                                    logs = task.logs + PublishTaskLog(
                                        level = LogLevel.Error,
                                        message = "创建发布任务失败：$errorMessage",
                                        stage = PublishTaskStage.Result,
                                    ),
                                )
                            },
                        )
                    }
                    postMessage("创建发布任务失败：${it.message ?: "未知错误"}", isError = true)
                }
            } finally {
                vivoProductionConfirmed.value = false
                isCreatingPublishTasks.value = false
            }
        }
    }

    private fun retryFailedPublishTasks() {
        val state = uiState.value
        val app = state.selectedApp ?: return
        val failedTasks = state.publishTasks.filter {
            it.appId == app.id && it.status == PublishTaskStatus.Failed
        }
        if (failedTasks.isEmpty()) {
            postMessage("没有需要重试的失败任务", isError = true)
            return
        }

        val vivoOptions = state.snapshot.vivoPublishOptions.copy(
            productionConfirmed = state.vivoProductionConfirmed,
        )

        viewModelScope.launch {
            isCreatingPublishTasks.value = true
            postMessage("正在重试 ${failedTasks.size} 个失败任务...")
            try {
                coroutineScope {
                    val channelById = state.publishTargetChannels.associateBy { it.id }
                    failedTasks.map { task ->
                        async {
                            val channel = channelById[task.channelId]
                            if (channel == null) {
                                task.copy(
                                    status = PublishTaskStatus.Failed,
                                    logs = task.logs + PublishTaskLog(
                                        level = LogLevel.Error,
                                        message = "渠道不存在，无法重试",
                                        stage = PublishTaskStage.Result,
                                    ),
                                ).also { updatePublishTask(it) }
                            } else {
                                val resetTask = task.copy(
                                    status = PublishTaskStatus.Ready,
                                    logs = emptyList(),
                                    vendorTaskId = null,
                                    vendorUploadIds = emptyList(),
                                    publishEnvironment = null,
                                )
                                val runningTask = resetTask.asRunningPublishTask()
                                updatePublishTask(runningTask)
                                executePublishTasks(
                                    app = app,
                                    channel = channel,
                                    task = runningTask,
                                    vivoOptions = vivoOptions,
                                    onTaskLog = { log -> appendPublishTaskLog(runningTask.id, log) },
                                ).also { updatePublishTask(it) }
                            }
                        }
                    }.awaitAll()
                }
                postMessage("重试完成")
            } finally {
                isCreatingPublishTasks.value = false
            }
        }
    }

    private fun refreshPublishStatus(taskId: String) {
        val state = uiState.value
        val task = state.publishTasks.firstOrNull { it.id == taskId }
        val app = state.apps.firstOrNull { it.id == task?.appId }
        val channel = state.channels.firstOrNull { it.id == task?.channelId }
        if (task == null || app == null || channel == null) {
            postMessage("发布任务不存在", isError = true)
            return
        }

        viewModelScope.launch {
            postMessage("正在刷新发布状态...")
            val updated = refreshPublishTaskStatus(
                app = app,
                channel = channel,
                task = task,
                vivoOptions = state.snapshot.vivoPublishOptions.copy(
                    productionConfirmed = state.vivoProductionConfirmed,
                ),
            )
            updateState {
                it.copy(
                    publishTasks = it.publishTasks.map { item ->
                        if (item.id == taskId) updated else item
                    }
                )
            }
            postMessage("发布状态已刷新")
        }
    }

    private fun updateChannel(channel: ChannelRecord) {
        viewModelScope.launch {
            updateState { it.withUpdatedChannel(channel) }
        }
    }

    private fun clearVivoProductionConfirmation() {
        vivoProductionConfirmed.value = false
    }

    private fun reduce(
        nextMessage: String? = null,
        transform: (LocalStateSnapshot) -> LocalStateSnapshot,
    ) {
        viewModelScope.launch {
            runCatching { updateState(transform) }
                .onSuccess {
                    if (nextMessage != null) {
                        postMessage(nextMessage)
                    }
                }
                .onFailure {
                    postMessage("保存本地状态失败：${it.message ?: "未知错误"}", isError = true)
                }
        }
    }

    private suspend fun replacePublishTasksForApp(
        appId: String,
        tasks: List<PublishTaskRecord>,
    ) {
        updateState {
            it.copy(
                publishTasks = it.publishTasks.filterNot { task -> task.appId == appId } + tasks
            )
        }
    }

    private suspend fun updatePublishTask(task: PublishTaskRecord) {
        updateState {
            it.copy(
                publishTasks = it.publishTasks.map { item ->
                    if (item.id == task.id) task else item
                }
            )
        }
    }

    private fun appendPublishTaskLog(
        taskId: String,
        log: PublishTaskLog,
    ) {
        viewModelScope.launch {
            updateState {
                it.copy(
                    publishTasks = it.publishTasks.map { item ->
                        if (item.id == taskId) item.copy(logs = item.logs + log) else item
                    }
                )
            }
        }
    }

    private fun PublishTaskRecord.asRunningPublishTask(): PublishTaskRecord {
        if (status != PublishTaskStatus.Ready) return this
        return copy(
            status = PublishTaskStatus.Uploading,
            logs = logs + PublishTaskLog(
                level = LogLevel.Info,
                message = "开始执行 ${marketType.displayName} 发布",
                stage = PublishTaskStage.Upload,
            ),
        )
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
            publishChannelIds = publishChannelIds.filter { channelId ->
                channels.any { it.id == channelId }
            },
        )

    private fun LocalStateSnapshot.withoutPersistedVivoProductionConfirmation(): LocalStateSnapshot =
        copy(vivoPublishOptions = vivoPublishOptions.withoutProductionConfirmation())

    private fun VivoPublishOptions.withoutProductionConfirmation(): VivoPublishOptions =
        copy(productionConfirmed = false)

    private fun LocalStateSnapshot.newId(prefix: String): String =
        "$prefix-${nextAvailableId()}"

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
        appendLine("**应用市场提审通知**")
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
                appendLine("- 线上版本：${info.displayOnlineVersion()}")
                appendLine("- 正在审核版本：${info.displayReviewingVersion(channel.marketType)}")
                appendLine("- 审核状态：${info.displayAuditStatus(channel.marketType)}")
            }
            appendLine()
        }
    }.trimEnd()

private fun ChannelRecord.reportTitle(): String =
    name.takeIf { it.isNotBlank() } ?: marketType.displayName
