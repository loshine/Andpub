package io.github.loshine.andpub.presentation

import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PublishGuardsTest {
    @Test
    fun blockers_whenNoChannelsSelected() {
        val state = baseState()
        val blockers = PublishGuards.blockers(state)
        assertContains(blockers.first(), "勾选")
    }

    @Test
    fun blockers_whenUnifiedArtifactMissing() {
        val app = AppRecord(id = "app-1", name = "Demo", packageName = "com.demo.app")
        val channel = sampleChannel(app.id)
        val state = AndpubUiState(
            snapshot = LocalStateSnapshot(
                apps = listOf(app),
                channels = listOf(channel),
                selectedAppId = app.id,
                publishMode = PublishMode.UnifiedArtifact,
                publishChannelIds = listOf(channel.id),
                unifiedArtifact = ArtifactDraft(),
            ),
        )
        val blockers = PublishGuards.blockers(state)
        assertTrue(blockers.any { it.contains("缺少产物") })
    }

    @Test
    fun canStart_whenArtifactPresent() {
        val app = AppRecord(id = "app-1", name = "Demo", packageName = "com.demo.app")
        val channel = sampleChannel(app.id)
        val state = AndpubUiState(
            snapshot = LocalStateSnapshot(
                apps = listOf(app),
                channels = listOf(channel),
                selectedAppId = app.id,
                publishMode = PublishMode.UnifiedArtifact,
                publishChannelIds = listOf(channel.id),
                unifiedArtifact = ArtifactDraft(
                    packageType = PackageType.Apk,
                    value = "/tmp/app.apk",
                ),
            ),
        )
        assertTrue(PublishGuards.canStartPublish(state))
    }

    @Test
    fun blockers_untilCompatiblePackageTypeIsPersisted() {
        val app = AppRecord(id = "app-1", name = "Demo", packageName = "com.demo.app")
        val channel = sampleChannel(app.id) // Huawei
        val state = AndpubUiState(
            snapshot = LocalStateSnapshot(
                apps = listOf(app),
                channels = listOf(channel),
                selectedAppId = app.id,
                publishMode = PublishMode.UnifiedArtifact,
                publishChannelIds = listOf(channel.id),
                unifiedArtifact = ArtifactDraft(
                    packageType = PackageType.SplitApk,
                    value = "/tmp/app.apk",
                ),
            ),
        )
        val blockers = PublishGuards.blockers(state)
        assertTrue(blockers.any { it.contains("包类型正在适配") })
    }

    private fun baseState(): AndpubUiState {
        val app = AppRecord(id = "app-1", name = "Demo", packageName = "com.demo.app")
        return AndpubUiState(
            snapshot = LocalStateSnapshot(
                apps = listOf(app),
                selectedAppId = app.id,
            ),
        )
    }

    private fun sampleChannel(appId: String) = ChannelRecord(
        id = "ch-1",
        appId = appId,
        marketType = MarketType.Huawei,
        marketAppId = null,
        credentials = emptyMap(),
        extraFields = emptyMap(),
    )
}
