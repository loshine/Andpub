package io.github.loshine.andpub.presentation

import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.MarketCapability
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.domain.model.vivoEnvironment

/**
 * Lightweight pre-publish checks aligned with CreatePublishTasksUseCase validation.
 * Publishing stays blocked while ArtifactEditor persists a channel-compatible package type.
 */
object PublishGuards {
    fun blockers(state: AndpubUiState): List<String> {
        val blockers = mutableListOf<String>()
        if (state.selectedApp == null) {
            blockers += "请先选择应用"
            return blockers
        }
        if (state.publishTargetChannels.isEmpty()) {
            blockers += "请先勾选要发布的渠道"
            return blockers
        }
        if (state.isCreatingPublishTasks) {
            blockers += "发布进行中，请稍候"
            return blockers
        }

        when (state.publishMode) {
            PublishMode.UnifiedArtifact -> {
                val capabilities = state.publishTargetChannels.map {
                    MarketDefinitions.schemaOf(it.marketType).capability
                }
                val allowed = capabilities.allowedPackageTypes()
                blockers += packageTypeBlockers(
                    draft = state.unifiedArtifactDraft,
                    allowed = allowed,
                    label = "统一产物",
                )
            }
            PublishMode.PerChannelArtifact -> {
                state.publishTargetChannels.forEach { channel ->
                    val title = channel.name.takeIf { it.isNotBlank() } ?: channel.marketType.displayName
                    val capability = MarketDefinitions.schemaOf(channel.marketType).capability
                    val allowed = listOf(capability).allowedPackageTypes()
                    blockers += packageTypeBlockers(
                        draft = state.artifactDrafts[channel.id] ?: ArtifactDraft(),
                        allowed = allowed,
                        label = title,
                    )
                }
            }
        }

        val needsVivoProductionConfirm = state.publishTargetChannels.any { channel ->
            channel.marketType == MarketType.Vivo &&
                channel.vivoEnvironment() == VivoApiEnvironment.Production
        }
        if (needsVivoProductionConfirm && !state.vivoProductionConfirmed) {
            blockers += "请确认向 vivo 正式环境提交更新"
        }
        return blockers.distinct()
    }

    fun canStartPublish(state: AndpubUiState): Boolean = blockers(state).isEmpty()

    private fun packageTypeBlockers(
        draft: ArtifactDraft,
        allowed: Set<PackageType>,
        label: String,
    ): List<String> = when {
        allowed.isEmpty() -> listOf("$label：没有可用的包类型")
        draft.packageType !in allowed -> listOf("$label：包类型正在适配所选渠道，请稍候")
        else -> artifactBlockers(draft, label)
    }

    private fun artifactBlockers(draft: ArtifactDraft, label: String): List<String> {
        return if (draft.packageType == PackageType.SplitApk) {
            buildList {
                if (draft.split32.value.isBlank()) add("$label：缺少 32 位 APK")
                if (draft.split64.value.isBlank()) add("$label：缺少 64 位 APK")
            }
        } else {
            if (draft.value.isBlank()) listOf("$label：缺少产物文件或 URL") else emptyList()
        }
    }

    private fun List<MarketCapability>.allowedPackageTypes(): Set<PackageType> {
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
        }
    }
}
