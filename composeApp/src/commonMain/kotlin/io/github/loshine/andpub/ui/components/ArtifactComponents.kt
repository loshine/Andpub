package io.github.loshine.andpub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketCapability
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.SplitApkSlot
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.domain.model.VivoCompatibleDevice
import io.github.loshine.andpub.domain.model.VivoOnlineType
import io.github.loshine.andpub.domain.model.vivoEnvironment
import io.github.loshine.andpub.domain.model.withVivoEnvironment
import io.github.loshine.andpub.platform.inspectLocalArtifact
import io.github.loshine.andpub.platform.pickArtifactFilePath
import io.github.loshine.andpub.presentation.AndpubIntent
import io.github.loshine.andpub.presentation.AndpubUiState
import kotlinx.coroutines.launch

// ─── Artifact section (top-level) ─────────────────────────────────────────────

@Composable
fun ArtifactSection(
    state: AndpubUiState,
    targetChannels: List<ChannelRecord>,
    onIntent: (AndpubIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PublishMode.entries.forEach { mode ->
                StableFilterChip(
                    selected = state.publishMode == mode,
                    onClick = { onIntent(AndpubIntent.UpdatePublishMode(mode)) },
                    label = { Text(mode.displayName) },
                )
            }
        }

        if (state.publishMode == PublishMode.UnifiedArtifact) {
            val selectedCapabilities = targetChannels
                .map { MarketDefinitions.schemaOf(it.marketType).capability }
            val urlFallbackMarkets = targetChannels
                .filter { !MarketDefinitions.schemaOf(it.marketType).capability.supportsUserUrl }
                .joinToString("、") { it.marketType.displayName }
            ArtifactEditor(
                title = "统一产物",
                draft = state.unifiedArtifactDraft,
                toolSettings = state.toolSettings,
                allowedPackageTypes = selectedCapabilities.allowedPackageTypes(),
                allowUrl = true,
                urlFallbackMessage = urlFallbackMarkets.takeIf { it.isNotBlank() }?.let {
                    "$it 不支持 URL 直传，创建发布任务时会先下载到本地再按文件上传。"
                },
                onDraftChange = { onIntent(AndpubIntent.UpdateUnifiedArtifact(it)) },
                onPickFile = { path, result ->
                    result.fold(
                        onSuccess = { onIntent(AndpubIntent.ApplyInspectionToUnified(path, it)) },
                        onFailure = { onIntent(AndpubIntent.ApplyArtifactErrorToUnified(path, it)) },
                    )
                },
                onPickSplitFile = { slot, path, result ->
                    result.fold(
                        onSuccess = { onIntent(AndpubIntent.ApplySplitInspectionToUnified(slot, path, it)) },
                        onFailure = { onIntent(AndpubIntent.ApplySplitArtifactErrorToUnified(slot, path, it)) },
                    )
                },
            )
        } else {
            if (targetChannels.isEmpty()) {
                Text("先勾选要发布的渠道，再为每个渠道配置独立产物。")
            }
            targetChannels.forEach { channel ->
                val capability = MarketDefinitions.schemaOf(channel.marketType).capability
                ArtifactEditor(
                    title = channel.displayTitle(),
                    draft = state.artifactDrafts[channel.id] ?: ArtifactDraft(),
                    toolSettings = state.toolSettings,
                    allowedPackageTypes = listOf(capability).allowedPackageTypes(),
                    allowUrl = true,
                    urlFallbackMessage = if (capability.supportsUserUrl) null
                    else "${channel.marketType.displayName} 不支持 URL 直传，创建发布任务时会先下载到本地再按文件上传。",
                    onDraftChange = { onIntent(AndpubIntent.UpdateChannelArtifact(channel.id, it)) },
                    onPickFile = { path, result ->
                        result.fold(
                            onSuccess = { onIntent(AndpubIntent.ApplyInspectionToChannel(channel.id, path, it)) },
                            onFailure = { onIntent(AndpubIntent.ApplyArtifactErrorToChannel(channel.id, path, it)) },
                        )
                    },
                    onPickSplitFile = { slot, path, result ->
                        result.fold(
                            onSuccess = { onIntent(AndpubIntent.ApplySplitInspectionToChannel(channel.id, slot, path, it)) },
                            onFailure = { onIntent(AndpubIntent.ApplySplitArtifactErrorToChannel(channel.id, slot, path, it)) },
                        )
                    },
                )
            }
        }
    }
}

// ─── Artifact editor card ──────────────────────────────────────────────────────

@Composable
fun ArtifactEditor(
    title: String,
    draft: ArtifactDraft,
    toolSettings: ToolSettings,
    allowedPackageTypes: Set<PackageType>,
    allowUrl: Boolean,
    urlFallbackMessage: String?,
    onDraftChange: (ArtifactDraft) -> Unit,
    onPickFile: (String, Result<ArtifactInspection>) -> Unit,
    onPickSplitFile: (SplitApkSlot, String, Result<ArtifactInspection>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selectedPackageType = draft.packageType.takeIf { it in allowedPackageTypes }
        ?: allowedPackageTypes.first()
    val effectiveDraft = draft.copy(
        sourceType = if (draft.sourceType == ArtifactSourceType.Url && !allowUrl) {
            ArtifactSourceType.LocalFile
        } else {
            draft.sourceType
        },
        packageType = selectedPackageType,
    )

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtifactSourceType.entries
                    .filter { it == ArtifactSourceType.LocalFile || allowUrl }
                    .forEach { type ->
                        StableFilterChip(
                            selected = effectiveDraft.sourceType == type,
                            onClick = {
                                onDraftChange(effectiveDraft.copy(sourceType = type).withoutInspection())
                            },
                            label = { Text(type.displayName) },
                        )
                    }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackageType.entries.filter { it in allowedPackageTypes }.forEach { type ->
                    StableFilterChip(
                        selected = effectiveDraft.packageType == type,
                        onClick = {
                            onDraftChange(effectiveDraft.copy(packageType = type))
                        },
                        label = { Text(type.displayName) },
                    )
                }
            }

            urlFallbackMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            if (effectiveDraft.packageType == PackageType.SplitApk) {
                SplitArtifactFileRow(
                    slot = SplitApkSlot.Arm32,
                    part = effectiveDraft.split32,
                    sourceType = effectiveDraft.sourceType,
                    toolSettings = toolSettings,
                    onPartChange = { onDraftChange(effectiveDraft.copy(split32 = it)) },
                    onPickFile = onPickSplitFile,
                )
                SplitArtifactFileRow(
                    slot = SplitApkSlot.Arm64,
                    part = effectiveDraft.split64,
                    sourceType = effectiveDraft.sourceType,
                    toolSettings = toolSettings,
                    onPartChange = { onDraftChange(effectiveDraft.copy(split64 = it)) },
                    onPickFile = onPickSplitFile,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = effectiveDraft.value,
                        onValueChange = { onDraftChange(effectiveDraft.withEditedValue(it)) },
                        label = {
                            Text(
                                if (effectiveDraft.sourceType == ArtifactSourceType.Url) "URL"
                                else "本地文件路径"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (effectiveDraft.sourceType == ArtifactSourceType.LocalFile) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val path = pickArtifactFilePath() ?: return@launch
                                    onPickFile(
                                        path,
                                        inspectLocalArtifact(
                                            path = path,
                                            androidSdkPath = toolSettings.androidSdkPath,
                                            bundletoolPath = toolSettings.bundletoolPath,
                                        ),
                                    )
                                }
                            },
                        ) { Text("选择") }
                    }
                }

                if (effectiveDraft.sourceType == ArtifactSourceType.Url) {
                    UrlHashFields(effectiveDraft, onDraftChange)
                }
                ArtifactInspectionSummary(effectiveDraft)
            }
        }
    }
}

@Composable
private fun SplitArtifactFileRow(
    slot: SplitApkSlot,
    part: ArtifactPart,
    sourceType: ArtifactSourceType,
    toolSettings: ToolSettings,
    onPartChange: (ArtifactPart) -> Unit,
    onPickFile: (SplitApkSlot, String, Result<ArtifactInspection>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = part.value,
                onValueChange = { onPartChange(part.withEditedValue(it)) },
                label = {
                    Text(
                        if (sourceType == ArtifactSourceType.Url) "${slot.displayName} URL"
                        else "${slot.displayName} 文件路径"
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (sourceType == ArtifactSourceType.LocalFile) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val path = pickArtifactFilePath() ?: return@launch
                            onPickFile(
                                slot,
                                path,
                                inspectLocalArtifact(
                                    path = path,
                                    androidSdkPath = toolSettings.androidSdkPath,
                                    bundletoolPath = toolSettings.bundletoolPath,
                                ),
                            )
                        }
                    },
                ) { Text("选择") }
            }
        }
        ArtifactPartSummary(slot.displayName, part)
    }
}

@Composable
private fun UrlHashFields(draft: ArtifactDraft, onDraftChange: (ArtifactDraft) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.md5,
            onValueChange = { onDraftChange(draft.copy(md5 = it)) },
            label = { Text("MD5") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.sha256,
            onValueChange = { onDraftChange(draft.copy(sha256 = it)) },
            label = { Text("SHA-256") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ArtifactInspectionSummary(draft: ArtifactDraft) {
    if (draft.packageType == PackageType.SplitApk) {
        ArtifactPartSummary(SplitApkSlot.Arm32.displayName, draft.split32)
        ArtifactPartSummary(SplitApkSlot.Arm64.displayName, draft.split64)
        return
    }
    if (draft.value.isBlank() && draft.message == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (draft.downloadedPath.isNotBlank()) Text("下载到本地：${draft.downloadedPath}", style = MaterialTheme.typography.bodySmall)
        draft.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (draft.md5.isNotBlank()) Text("MD5：${draft.md5}", style = MaterialTheme.typography.bodySmall)
        if (draft.sha1.isNotBlank()) Text("SHA-1：${draft.sha1}", style = MaterialTheme.typography.bodySmall)
        if (draft.sha256.isNotBlank()) Text("SHA-256：${draft.sha256}", style = MaterialTheme.typography.bodySmall)
        draft.packageName?.let { Text("包名：$it", style = MaterialTheme.typography.bodySmall) }
        draft.versionName?.let { Text("版本名：$it", style = MaterialTheme.typography.bodySmall) }
        draft.versionCode?.let { Text("版本号：$it", style = MaterialTheme.typography.bodySmall) }
        if (draft.abiList.isNotEmpty()) Text("ABI：${draft.abiList.joinToString()}", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun ArtifactPartSummary(label: String, part: ArtifactPart) {
    if (part.value.isBlank() && part.message == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (part.value.isNotBlank()) Text(part.value, style = MaterialTheme.typography.bodySmall)
        if (part.downloadedPath.isNotBlank()) Text("下载到本地：${part.downloadedPath}", style = MaterialTheme.typography.bodySmall)
        part.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (part.md5.isNotBlank()) Text("MD5：${part.md5}", style = MaterialTheme.typography.bodySmall)
        part.packageName?.let { Text("包名：$it", style = MaterialTheme.typography.bodySmall) }
        part.versionName?.let { Text("版本名：$it", style = MaterialTheme.typography.bodySmall) }
        part.versionCode?.let { Text("版本号：$it", style = MaterialTheme.typography.bodySmall) }
        if (part.abiList.isNotEmpty()) Text("ABI：${part.abiList.joinToString()}", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(4.dp))
}

// ─── Vivo publish options ──────────────────────────────────────────────────────

@Composable
fun VivoPublishOptionSection(
    state: AndpubUiState,
    channel: ChannelRecord,
    onIntent: (AndpubIntent) -> Unit,
) {
    val options = state.snapshot.vivoPublishOptions
    val isProduction = channel.vivoEnvironment() == VivoApiEnvironment.Production
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("vivo 发布选项", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VivoApiEnvironment.entries.forEach { item ->
                StableFilterChip(
                    selected = channel.vivoEnvironment() == item,
                    onClick = {
                        onIntent(
                            AndpubIntent.AddOrUpdateChannel(
                                channelId = channel.id,
                                name = channel.name,
                                marketType = channel.marketType,
                                marketAppId = channel.marketAppId,
                                credentials = channel.credentials,
                                extraFields = channel.extraFields.withVivoEnvironment(item),
                            )
                        )
                    },
                    label = { Text(item.displayName) },
                )
            }
        }
        Text(
            "vivo 会先确认目标环境中的应用已存在，再上传文件并提交更新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VivoOnlineType.entries.forEach { item ->
                StableFilterChip(
                    selected = options.onlineType == item,
                    onClick = { onIntent(AndpubIntent.UpdateVivoPublishOptions(options.copy(onlineType = item))) },
                    label = { Text(item.displayName) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VivoCompatibleDevice.entries.forEach { item ->
                StableFilterChip(
                    selected = options.compatibleDevice == item,
                    onClick = { onIntent(AndpubIntent.UpdateVivoPublishOptions(options.copy(compatibleDevice = item))) },
                    label = { Text(item.displayName) },
                )
            }
        }
        if (isProduction) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.vivoProductionConfirmed,
                    onCheckedChange = { onIntent(AndpubIntent.UpdateVivoProductionConfirmed(it)) },
                )
                Text("确认向 vivo 正式环境提交更新", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ─── Shared helpers ────────────────────────────────────────────────────────────

fun List<MarketCapability>.allowedPackageTypes(): Set<PackageType> {
    if (isEmpty()) return PackageType.entries.toSet()
    val candidates = PackageType.entries.toSet()
    return candidates.filterTo(mutableSetOf()) { packageType ->
        all { capability ->
            when (packageType) {
                PackageType.Apk -> capability.supportsUnifiedApk
                PackageType.SplitApk -> capability.supportsSplitApk
                PackageType.Aab -> capability.supportsAab
            }
        }
    }.ifEmpty { setOf(PackageType.Apk) }
}

fun ArtifactDraft.withEditedValue(value: String): ArtifactDraft =
    copy(value = value).withoutInspection()

fun ArtifactDraft.withoutInspection(): ArtifactDraft = copy(
    downloadedPath = "",
    md5 = "",
    sha1 = "",
    sha256 = "",
    packageName = null,
    versionName = null,
    versionCode = null,
    abiList = emptyList(),
    message = null,
)

fun ArtifactPart.withEditedValue(value: String): ArtifactPart = copy(
    value = value,
    downloadedPath = "",
    md5 = "",
    sha1 = "",
    sha256 = "",
    packageName = null,
    versionName = null,
    versionCode = null,
    abiList = emptyList(),
    message = null,
)
