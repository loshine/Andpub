package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.platform.ArtifactDownloadTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class CreatePublishTasksUseCaseTest : StringSpec({
    val app = AppRecord("app-1", "猫耳", "cn.missevan")

    "creates visible pending tasks with ids matching prepared tasks" {
        val useCase = CreatePublishTasksUseCase(
            inspectArtifact = { path, _, _ -> Result.success(testInspection(path)) },
        )
        val channel = channel(MarketType.Vivo)
        val oldTask = PublishTaskRecord(
            id = "task-9",
            appId = app.id,
            channelId = channel.id,
            marketType = channel.marketType,
            publishMode = PublishMode.UnifiedArtifact,
            artifact = ArtifactDraft(),
            status = PublishTaskStatus.Failed,
            logs = emptyList(),
        )
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            publishTasks = listOf(oldTask),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.LocalFile,
                value = "/tmp/app.apk",
                packageName = "cn.missevan",
            ),
        )

        val pending = useCase.createPendingTasks(snapshot, app, listOf(channel)).single()
        val prepared = useCase(snapshot, app, listOf(channel)).single()

        pending.id shouldBe "task-10"
        pending.id shouldBe prepared.id
        pending.status shouldBe PublishTaskStatus.Validating
        pending.logs.single().message shouldBe "vivo 应用市场 正在准备并检查产物"
    }

    "downloads url artifact for markets without direct url upload" {
        var downloadedUrl: String? = null
        var downloadTarget: ArtifactDownloadTarget? = null
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { url, target ->
                downloadedUrl = url
                downloadTarget = target
                Result.success("/tmp/downloaded.apk")
            },
            inspectArtifact = { path, _, _ ->
                Result.success(testInspection(path))
            },
        )
        val channel = channel(MarketType.Xiaomi)
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.Url,
                value = "https://cdn.example.com/app.apk",
            ),
        )

        val task = useCase(snapshot, app, listOf(channel)).single()

        downloadedUrl shouldBe "https://cdn.example.com/app.apk"
        downloadTarget?.marketFolder shouldBe "xiaomi"
        downloadTarget?.artifactFolder shouldBe "apk"
        downloadTarget?.variantFolder shouldBe "universal"
        task.status shouldBe PublishTaskStatus.Ready
        task.artifact.sourceType shouldBe ArtifactSourceType.LocalFile
        task.artifact.value shouldBe "/tmp/downloaded.apk"
        task.artifact.downloadedPath shouldBe "/tmp/downloaded.apk"
        task.artifact.packageName shouldBe "cn.missevan"
        task.artifact.versionName shouldBe "1.2.3"
        task.logs.map { it.message } shouldContain
            "小米应用市场 不支持 URL 直传，已下载并检查后按本地文件上传"
    }

    "downloads and inspects url artifact for markets with direct url upload" {
        var downloadCalled = false
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { _, _ ->
                downloadCalled = true
                Result.success("/tmp/downloaded.apk")
            },
            inspectArtifact = { path, _, _ -> Result.success(testInspection(path)) },
        )
        val channel = channel(MarketType.Huawei)
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.Url,
                value = "https://cdn.example.com/app.apk",
            ),
        )

        val task = useCase(snapshot, app, listOf(channel)).single()

        downloadCalled shouldBe true
        task.status shouldBe PublishTaskStatus.Ready
        task.artifact.sourceType shouldBe ArtifactSourceType.Url
        task.artifact.value shouldBe "https://cdn.example.com/app.apk"
        task.artifact.downloadedPath shouldBe "/tmp/downloaded.apk"
        task.artifact.packageName shouldBe "cn.missevan"
        task.artifact.versionName shouldBe "1.2.3"
        task.logs.map { it.message } shouldContain "华为 AppGallery URL 产物已下载并检查，后续将直接提交 URL"
        task.logs.map { it.message } shouldContain "华为 AppGallery 支持 URL 拉包，将直接提交 URL"
    }

    "downloads bundle url artifacts into bundle cache target" {
        var downloadTarget: ArtifactDownloadTarget? = null
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { _, target ->
                downloadTarget = target
                Result.success("/tmp/downloaded.aab")
            },
            inspectArtifact = { path, _, _ -> Result.success(testInspection(path)) },
        )
        val channel = channel(MarketType.Huawei)
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.Url,
                packageType = PackageType.Aab,
                value = "https://cdn.example.com/app.aab",
            ),
        )

        val task = useCase(snapshot, app, listOf(channel)).single()

        task.status shouldBe PublishTaskStatus.Ready
        downloadTarget?.marketFolder shouldBe "huawei"
        downloadTarget?.artifactFolder shouldBe "bundle"
        downloadTarget?.variantFolder shouldBe null
    }

    "downloads url artifacts for selected channels concurrently" {
        val secondDownloadStarted = CompletableDeferred<Unit>()
        val downloadedUrls = mutableListOf<String>()
        val xiaomi = channel(MarketType.Xiaomi)
        val tencent = channel(MarketType.Tencent)
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { url, _ ->
                downloadedUrls += url
                if (url.endsWith("app-1.apk")) {
                    secondDownloadStarted.await()
                } else {
                    secondDownloadStarted.complete(Unit)
                }
                Result.success("/tmp/${url.substringAfterLast("/")}")
            },
            inspectArtifact = { path, _, _ ->
                Result.success(testInspection(path))
            },
        )
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(xiaomi, tencent),
            publishMode = PublishMode.PerChannelArtifact,
            channelArtifacts = mapOf(
                xiaomi.id to ArtifactDraft(
                    sourceType = ArtifactSourceType.Url,
                    value = "https://cdn.example.com/app-1.apk",
                ),
                tencent.id to ArtifactDraft(
                    sourceType = ArtifactSourceType.Url,
                    value = "https://cdn.example.com/app-2.apk",
                ),
            ),
        )

        val tasks = withTimeout(1_000) {
            useCase(snapshot, app, listOf(xiaomi, tencent))
        }

        downloadedUrls shouldBe listOf(
            "https://cdn.example.com/app-1.apk",
            "https://cdn.example.com/app-2.apk",
        )
        tasks.map { it.id } shouldBe listOf("task-2", "task-3")
        tasks.map { it.channelId } shouldBe listOf(xiaomi.id, tencent.id)
    }

    "fails task when fallback download fails" {
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { _, _ -> Result.failure(IllegalStateException("network down")) },
            inspectArtifact = { path, _, _ -> Result.success(testInspection(path)) },
        )
        val channel = channel(MarketType.Tencent)
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.Url,
                value = "https://cdn.example.com/app.apk",
            ),
        )

        val task = useCase(snapshot, app, listOf(channel)).single()

        task.status shouldBe PublishTaskStatus.Failed
        task.artifact.sourceType shouldBe ArtifactSourceType.Url
        task.logs.map { it.message } shouldContain
            "腾讯应用开放平台 URL 产物下载检查失败：network down"
    }

    "downloads split url artifacts for markets without direct url upload" {
        val downloadedUrls = mutableListOf<String>()
        val downloadTargets = mutableListOf<ArtifactDownloadTarget>()
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { url, target ->
                downloadedUrls += url
                downloadTargets += target
                Result.success("/tmp/${url.substringAfterLast("/")}")
            },
            inspectArtifact = { path, _, _ ->
                Result.success(testInspection(path))
            },
        )
        val channel = channel(MarketType.Tencent)
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.Url,
                packageType = PackageType.SplitApk,
                split32 = ArtifactPart(value = "https://cdn.example.com/app-32.apk"),
                split64 = ArtifactPart(value = "https://cdn.example.com/app-64.apk"),
            ),
        )

        val task = useCase(snapshot, app, listOf(channel)).single()

        downloadedUrls shouldBe listOf(
            "https://cdn.example.com/app-32.apk",
            "https://cdn.example.com/app-64.apk",
        )
        downloadTargets.map { it.marketFolder } shouldBe listOf("tencent", "tencent")
        downloadTargets.map { it.artifactFolder } shouldBe listOf("apk", "apk")
        downloadTargets.map { it.variantFolder } shouldBe listOf("32", "64")
        task.status shouldBe PublishTaskStatus.Ready
        task.artifact.sourceType shouldBe ArtifactSourceType.LocalFile
        task.artifact.split32.value shouldBe "/tmp/app-32.apk"
        task.artifact.split64.value shouldBe "/tmp/app-64.apk"
        task.artifact.split32.downloadedPath shouldBe "/tmp/app-32.apk"
        task.artifact.split64.downloadedPath shouldBe "/tmp/app-64.apk"
        task.logs.map { it.message } shouldContain
            "腾讯应用开放平台 不支持 URL 直传，32 位 APK 已下载并检查后按本地文件上传"
        task.logs.map { it.message } shouldContain
            "腾讯应用开放平台 不支持 URL 直传，64 位 APK 已下载并检查后按本地文件上传"
    }

    "downloads split url artifacts for vivo and converts to local upload files" {
        val downloadedUrls = mutableListOf<String>()
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { url, _ ->
                downloadedUrls += url
                Result.success("/tmp/${url.substringAfterLast("/")}")
            },
            inspectArtifact = { path, _, _ ->
                Result.success(testInspection(path))
            },
        )
        val channel = channel(MarketType.Vivo)
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.Url,
                packageType = PackageType.SplitApk,
                split32 = ArtifactPart(value = "https://cdn.example.com/app-32.apk"),
                split64 = ArtifactPart(value = "https://cdn.example.com/app-64.apk"),
            ),
        )

        val task = useCase(snapshot, app, listOf(channel)).single()

        downloadedUrls shouldBe listOf(
            "https://cdn.example.com/app-32.apk",
            "https://cdn.example.com/app-64.apk",
        )
        task.status shouldBe PublishTaskStatus.Ready
        task.artifact.sourceType shouldBe ArtifactSourceType.LocalFile
        task.artifact.split32.value shouldBe "/tmp/app-32.apk"
        task.artifact.split64.value shouldBe "/tmp/app-64.apk"
        task.artifact.split32.downloadedPath shouldBe "/tmp/app-32.apk"
        task.artifact.split64.downloadedPath shouldBe "/tmp/app-64.apk"
        task.artifact.split32.packageName shouldBe "cn.missevan"
        task.artifact.split64.versionName shouldBe "1.2.3"
        task.logs.map { it.message } shouldContain
            "vivo 应用市场 不支持 URL 直传，32 位 APK 已下载并检查后按本地文件上传"
        task.logs.map { it.message } shouldContain
            "vivo 应用市场 不支持 URL 直传，64 位 APK 已下载并检查后按本地文件上传"
    }

    "fails url task when downloaded artifact has no manifest package" {
        val useCase = CreatePublishTasksUseCase(
            downloadUrlArtifact = { _, _ -> Result.success("/tmp/downloaded.apk") },
            inspectArtifact = { path, _, _ ->
                Result.success(noManifestInspection(path))
            },
        )
        val channel = channel(MarketType.Vivo)
        val snapshot = LocalStateSnapshot(
            apps = listOf(app),
            channels = listOf(channel),
            unifiedArtifact = ArtifactDraft(
                sourceType = ArtifactSourceType.Url,
                value = "https://cdn.example.com/app.apk",
            ),
        )

        val task = useCase(snapshot, app, listOf(channel)).single()

        task.status shouldBe PublishTaskStatus.Failed
        task.logs.map { it.message } shouldContain
            "vivo 应用市场 URL 产物已下载但检查失败：URL 产物 检查失败：未找到 aapt2"
    }
})

private fun channel(marketType: MarketType): ChannelRecord =
    ChannelRecord(
        id = "channel-${marketType.name}",
        appId = "app-1",
        marketType = marketType,
        marketAppId = "market-id",
        credentials = emptyMap(),
        extraFields = emptyMap(),
    )

private fun testInspection(path: String): ArtifactInspection =
    ArtifactInspection(
        fileName = path.substringAfterLast("/"),
        fileSizeBytes = 1024,
        md5 = "md5",
        sha1 = "sha1",
        sha256 = "sha256",
        packageName = "cn.missevan",
        versionName = "1.2.3",
        versionCode = 123,
        abiList = emptyList(),
    )

private fun noManifestInspection(path: String): ArtifactInspection =
    ArtifactInspection(
        fileName = path.substringAfterLast("/"),
        fileSizeBytes = 1024,
        md5 = "md5",
        sha1 = "sha1",
        sha256 = "sha256",
        packageName = null,
        versionName = null,
        versionCode = null,
        abiList = emptyList(),
        warnings = listOf("未找到 aapt2"),
    )
