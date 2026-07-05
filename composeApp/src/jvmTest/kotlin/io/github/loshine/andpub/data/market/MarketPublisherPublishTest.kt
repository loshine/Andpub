package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.honor.HonorAppIdEntry
import io.github.loshine.andpub.data.remote.honor.HonorAppIdList
import io.github.loshine.andpub.data.remote.honor.HonorFileUploadPath
import io.github.loshine.andpub.data.remote.honor.HonorFileUploadPaths
import io.github.loshine.andpub.data.remote.honor.HonorOperationResult
import io.github.loshine.andpub.data.remote.honor.HonorRemoteDataSource
import io.github.loshine.andpub.data.remote.honor.HonorToken
import io.github.loshine.andpub.data.remote.huawei.HuaweiAppIdEntry
import io.github.loshine.andpub.data.remote.huawei.HuaweiAppIdList
import io.github.loshine.andpub.data.remote.huawei.HuaweiAuthContext
import io.github.loshine.andpub.data.remote.huawei.HuaweiJwtGenerator
import io.github.loshine.andpub.data.remote.huawei.HuaweiOperationResult
import io.github.loshine.andpub.data.remote.huawei.HuaweiRemoteDataSource
import io.github.loshine.andpub.data.remote.huawei.HuaweiRet
import io.github.loshine.andpub.data.remote.huawei.HuaweiToken
import io.github.loshine.andpub.data.remote.oppo.OppoRemoteDataSource
import io.github.loshine.andpub.data.remote.oppo.OppoTaskResult
import io.github.loshine.andpub.data.remote.oppo.OppoToken
import io.github.loshine.andpub.data.remote.oppo.OppoUploadConfig
import io.github.loshine.andpub.data.remote.oppo.OppoUploadResult
import io.github.loshine.andpub.data.remote.tencent.TencentFileUploadInfo
import io.github.loshine.andpub.data.remote.tencent.TencentOperationResult
import io.github.loshine.andpub.data.remote.tencent.TencentRemoteDataSource
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiOperationResult
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiPackageInfo
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiQueryAppResult
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiRemoteDataSource
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

class MarketPublisherPublishTest : StringSpec({
    val app = AppRecord("app-1", "Local App", "com.example.app")
    val fakeApk = "apk-content".encodeToByteArray()

    // ─── Honor publish ────────────────────────────────────────────────────────

    "Honor publisher uploads APK, binds file, submits audit — returns Submitted" {
        val remote = mockk<HonorRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HonorToken("tok", 3600, "Bearer")
        coEvery { remote.getAppId("tok", "com.example.app") } returns
            HonorAppIdList(listOf(HonorAppIdEntry(appId = "hon-app-id")))
        coEvery {
            remote.getFileUploadUrl("tok", "hon-app-id", any())
        } returns HonorFileUploadPaths(
            listOf(HonorFileUploadPath(fileName = "app.apk", uploadUrl = "https://honor-cdn.com/upload", objectId = "obj-1"))
        )
        coEvery {
            remote.uploadFile("tok", "hon-app-id", "obj-1", "app.apk", fakeApk)
        } returns HonorOperationResult(code = 0, message = null)
        coEvery {
            remote.updateFileInfo("tok", "hon-app-id", any())
        } returns HonorOperationResult(code = 0, message = null)
        coEvery {
            remote.submitAudit("tok", "hon-app-id", any())
        } returns HonorOperationResult(code = 0, message = null)

        val result = HonorMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(honorPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorUploadIds shouldContain "obj-1"
        coVerify { remote.uploadFile("tok", "hon-app-id", "obj-1", "app.apk", fakeApk) }
        coVerify { remote.updateFileInfo("tok", "hon-app-id", any()) }
        coVerify { remote.submitAudit("tok", "hon-app-id", any()) }
    }

    "Honor publisher uses channel marketAppId when configured, skips getAppId call" {
        val remote = mockk<HonorRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HonorToken("tok", 3600, "Bearer")
        coEvery { remote.getFileUploadUrl("tok", "configured-id", any()) } returns
            HonorFileUploadPaths(listOf(HonorFileUploadPath(fileName = "a.apk", uploadUrl = "u", objectId = "obj-2")))
        coEvery { remote.uploadFile("tok", "configured-id", "obj-2", "app.apk", fakeApk) } returns
            HonorOperationResult(0, null)
        coEvery { remote.updateFileInfo("tok", "configured-id", any()) } returns HonorOperationResult(0, null)
        coEvery { remote.submitAudit("tok", "configured-id", any()) } returns HonorOperationResult(0, null)

        val result = HonorMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(honorPublishRequest(app, marketAppId = "configured-id")).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        coVerify(exactly = 0) { remote.getAppId(any(), any()) }
    }

    "Honor publisher fails when APK file cannot be read" {
        val remote = mockk<HonorRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HonorToken("tok", 3600, "Bearer")
        coEvery { remote.getAppId("tok", "com.example.app") } returns
            HonorAppIdList(listOf(HonorAppIdEntry(appId = "hon-app-id")))
        coEvery { remote.getFileUploadUrl("tok", "hon-app-id", any()) } returns
            HonorFileUploadPaths(listOf(HonorFileUploadPath(fileName = "a.apk", uploadUrl = "u", objectId = "obj-1")))

        val result = HonorMarketPublisher(remote, readFile = { Result.failure(Exception("disk error")) })
            .publish(honorPublishRequest(app))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "disk error"
    }

    "Honor publisher rejects AAB artifacts" {
        val remote = mockk<HonorRemoteDataSource>(relaxed = true)

        val result = HonorMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(honorPublishRequest(app, packageType = PackageType.Aab))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "AAB"
    }

    "Honor publisher refreshPublishStatus calls getAuditResult and returns Submitted" {
        val remote = mockk<HonorRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HonorToken("tok", 3600, "Bearer")
        coEvery { remote.getAppId("tok", "com.example.app") } returns
            HonorAppIdList(listOf(HonorAppIdEntry(appId = "hon-app-id")))
        coEvery { remote.getAuditResult("tok", any()) } returns HonorOperationResult(code = 0, message = "审核通过")

        val result = HonorMarketPublisher(remote)
            .refreshPublishStatus(honorPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.logs.single().message shouldContain "审核通过"
    }

    // ─── Huawei URL-based publish ──────────────────────────────────────────────

    "Huawei publisher submits URL artifact via submitAppWithFile — returns Submitted" {
        val remote = mockk<HuaweiRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HuaweiToken("token", 3600, "Bearer")
        val auth = HuaweiAuthContext.ApiClient(clientId = "cid", accessToken = "token")
        coEvery { remote.getAppIdList(auth, "com.example.app") } returns
            HuaweiAppIdList(listOf(HuaweiAppIdEntry(appId = "hw-app-id")))
        coEvery { remote.submitAppWithFile(auth, "hw-app-id", any()) } returns
            HuaweiOperationResult(ret = HuaweiRet(code = 0, message = null))

        val result = HuaweiMarketPublisher(remote)
            .publish(huaweiUrlPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        coVerify { remote.submitAppWithFile(auth, "hw-app-id", any()) }
    }

    "Huawei publisher uses channel marketAppId and skips getAppIdList call" {
        val remote = mockk<HuaweiRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HuaweiToken("token", 3600, "Bearer")
        val auth = HuaweiAuthContext.ApiClient(clientId = "cid", accessToken = "token")
        coEvery { remote.submitAppWithFile(auth, "preset-id", any()) } returns
            HuaweiOperationResult(ret = HuaweiRet(code = 0, message = null))

        val result = HuaweiMarketPublisher(remote)
            .publish(huaweiUrlPublishRequest(app, marketAppId = "preset-id")).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        coVerify(exactly = 0) { remote.getAppIdList(any(), any()) }
    }

    "Huawei publisher rejects local file artifact and reports clear error" {
        val remote = mockk<HuaweiRemoteDataSource>(relaxed = true)

        val result = HuaweiMarketPublisher(remote)
            .publish(huaweiLocalFilePublishRequest(app))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "URL"
    }

    "Huawei publisher refreshes status by querying app info" {
        val remote = mockk<HuaweiRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HuaweiToken("token", 3600, "Bearer")
        val auth = HuaweiAuthContext.ApiClient(clientId = "cid", accessToken = "token")
        coEvery { remote.getAppIdList(auth, "com.example.app") } returns
            HuaweiAppIdList(listOf(HuaweiAppIdEntry(appId = "hw-app-id")))
        coEvery { remote.getAppInfo(auth, "hw-app-id") } returns
            io.github.loshine.andpub.data.remote.huawei.HuaweiAppInfoResult(
                appInfo = io.github.loshine.andpub.data.remote.huawei.HuaweiAppInfo(releaseState = 4),
                auditInfo = null,
                languages = emptyList(),
                phasedReleaseInfo = null,
            )

        val result = HuaweiMarketPublisher(remote)
            .refreshPublishStatus(huaweiUrlPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.logs.single().message shouldContain "审核中"
    }
    // ─── OPPO publish ─────────────────────────────────────────────────────────

    "OPPO publisher uploads unified APK and submits — returns Submitted with taskId" {
        val remote = mockk<OppoRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns OppoToken("token", 99999)
        coEvery { remote.getUploadUrl("token", "secret", "apk") } returns
            OppoUploadConfig(uploadUrl = "https://oppo-cdn.com/upload", sign = "sign-abc")
        coEvery {
            remote.uploadFile("https://oppo-cdn.com/upload", "sign-abc", "apk", "app.apk", any())
        } returns OppoUploadResult(url = "https://oppo-cdn.com/app.apk", md5 = "uploaded-md5")
        coEvery {
            remote.publishApp(
                accessToken = "token",
                clientSecret = "secret",
                params = mapOf(
                    "pkg_name" to "com.example.app",
                    "version_code" to "100",
                    "apk_url" to """[{"url":"https://oppo-cdn.com/app.apk","md5":"uploaded-md5","cpu_code":0}]""",
                ),
            )
        } returns OppoTaskResult(success = true, message = "ok", taskId = "oppo-task-1")

        val result = OppoMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(oppoPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorTaskId shouldBe "oppo-task-1"
        coVerify { remote.uploadFile("https://oppo-cdn.com/upload", "sign-abc", "apk", "app.apk", any()) }
        coVerify { remote.publishApp("token", "secret", any()) }
    }

    "OPPO publisher uploads split APKs and calls updateMultiPackageApp" {
        val remote = mockk<OppoRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns OppoToken("token", 99999)
        coEvery { remote.getUploadUrl("token", "secret", "apk") } returnsMany listOf(
            OppoUploadConfig("https://cdn.com/upload1", "sign-1"),
            OppoUploadConfig("https://cdn.com/upload2", "sign-2"),
        )
        coEvery {
            remote.uploadFile("https://cdn.com/upload1", "sign-1", "apk", "app-32.apk", any())
        } returns OppoUploadResult(url = "https://cdn.com/app-32.apk", md5 = "md5-32")
        coEvery {
            remote.uploadFile("https://cdn.com/upload2", "sign-2", "apk", "app-64.apk", any())
        } returns OppoUploadResult(url = "https://cdn.com/app-64.apk", md5 = "md5-64")
        coEvery {
            remote.updateMultiPackageApp("token", "secret", any())
        } returns OppoTaskResult(success = true, message = "ok", taskId = "split-task-1")

        val result = OppoMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(oppoSplitPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorTaskId shouldBe "split-task-1"
        result.vendorUploadIds.size shouldBe 2
        coVerify { remote.updateMultiPackageApp("token", "secret", any()) }
        coVerify(exactly = 0) { remote.publishApp(any(), any(), any()) }
    }

    "OPPO publisher fails when publishApp returns success=false" {
        val remote = mockk<OppoRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns OppoToken("token", 99999)
        coEvery { remote.getUploadUrl("token", "secret", "apk") } returns
            OppoUploadConfig("https://cdn.com/upload", "sign-abc")
        coEvery { remote.uploadFile(any(), any(), any(), any(), any()) } returns
            OppoUploadResult(url = "https://cdn.com/app.apk", md5 = "md5")
        coEvery { remote.publishApp("token", "secret", any()) } returns
            OppoTaskResult(success = false, message = "版本号重复", taskId = null)

        val result = OppoMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(oppoPublishRequest(app))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "版本号重复"
    }

    "OPPO publisher refreshes status using getTaskState" {
        val remote = mockk<OppoRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns OppoToken("token", 99999)
        coEvery { remote.getTaskState("token", "secret", "task-99") } returns
            OppoTaskResult(success = true, message = "已完成", taskId = "task-99")

        val result = OppoMarketPublisher(remote)
            .refreshPublishStatus(oppoPublishRequest(app, vendorTaskId = "task-99")).getOrThrow()

        result.status shouldBe PublishTaskStatus.Accepted
        coVerify { remote.getTaskState("token", "secret", "task-99") }
    }

    // ─── Xiaomi publish ───────────────────────────────────────────────────────

    "Xiaomi publisher queries status, uploads APK via pushAppWithFile — returns Submitted" {
        val remote = mockk<XiaomiRemoteDataSource>()
        coEvery {
            remote.queryApp("dev@example.com", "pass", "pub-key", "com.example.app")
        } returns XiaomiQueryAppResult(
            packageInfo = XiaomiPackageInfo("App", "com.example.app", "1.0", 10),
            create = false,
            updateVersion = true,
            updateInfo = false,
        )
        coEvery {
            remote.pushAppWithFile(any(), "pass", "pub-key", "app.apk", fakeApk, "md5abc")
        } returns XiaomiOperationResult(message = "操作成功")

        val result = XiaomiMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(xiaomiPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        coVerify { remote.pushAppWithFile(any(), "pass", "pub-key", "app.apk", fakeApk, "md5abc") }
    }

    "Xiaomi publisher fails when updateVersion is false" {
        val remote = mockk<XiaomiRemoteDataSource>()
        coEvery {
            remote.queryApp("dev@example.com", "pass", "pub-key", "com.example.app")
        } returns XiaomiQueryAppResult(
            packageInfo = XiaomiPackageInfo("App", "com.example.app", "1.0", 10),
            create = false,
            updateVersion = false,
            updateInfo = false,
        )

        val result = XiaomiMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(xiaomiPublishRequest(app))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "updateVersion=false"
        coVerify(exactly = 0) { remote.pushAppWithFile(any(), any(), any(), any(), any(), any()) }
    }

    "Xiaomi publisher rejects split APK artifacts" {
        val remote = mockk<XiaomiRemoteDataSource>(relaxed = true)

        val result = XiaomiMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(xiaomiPublishRequest(app, packageType = PackageType.SplitApk))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "统一 APK"
    }

    // ─── Tencent publish ──────────────────────────────────────────────────────

    "Tencent publisher gets presigned URL, uploads to COS, calls updateApp — returns Submitted" {
        val remote = mockk<TencentRemoteDataSource>()
        coEvery {
            remote.getFileUploadInfo("user", "secret", "com.example.app", "tencent-id", "apk", "app.apk")
        } returns TencentFileUploadInfo(preSignUrl = "https://cos.qq.com/presigned", serialNumber = "serial-1")
        coEvery { remote.uploadToPresignedUrl("https://cos.qq.com/presigned", fakeApk) } just runs
        coEvery {
            remote.updateApp("user", "secret", "com.example.app", "tencent-id", any())
        } returns TencentOperationResult(message = "ok")

        val result = TencentMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(tencentPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorUploadIds shouldContain "serial-1"
        coVerify { remote.uploadToPresignedUrl("https://cos.qq.com/presigned", fakeApk) }
        coVerify { remote.updateApp("user", "secret", "com.example.app", "tencent-id", any()) }
    }

    "Tencent publisher uploads split APKs and passes both serial numbers to updateApp" {
        val remote = mockk<TencentRemoteDataSource>()
        coEvery {
            remote.getFileUploadInfo("user", "secret", "com.example.app", "tencent-id", "apk", "app-32.apk")
        } returns TencentFileUploadInfo(preSignUrl = "https://cos.qq.com/32", serialNumber = "serial-32")
        coEvery {
            remote.getFileUploadInfo("user", "secret", "com.example.app", "tencent-id", "apk", "app-64.apk")
        } returns TencentFileUploadInfo(preSignUrl = "https://cos.qq.com/64", serialNumber = "serial-64")
        coEvery { remote.uploadToPresignedUrl(any(), any()) } just runs
        coEvery { remote.updateApp("user", "secret", "com.example.app", "tencent-id", any()) } returns
            TencentOperationResult(message = "ok")

        val result = TencentMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(tencentSplitPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorUploadIds shouldContain "serial-32"
        result.vendorUploadIds shouldContain "serial-64"
        coVerify(exactly = 2) { remote.uploadToPresignedUrl(any(), any()) }
    }

    "Tencent publisher fails without marketAppId" {
        val remote = mockk<TencentRemoteDataSource>(relaxed = true)

        val result = TencentMarketPublisher(remote, readFile = { Result.success(fakeApk) })
            .publish(tencentPublishRequest(app, marketAppId = null))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "app_id"
        coVerify(exactly = 0) { remote.getFileUploadInfo(any(), any(), any(), any(), any(), any()) }
    }

    "Tencent publisher refreshes status: audit status 3 maps to Accepted" {
        val remote = mockk<TencentRemoteDataSource>()
        coEvery {
            remote.queryAppUpdateStatus("user", "secret", "com.example.app", "tencent-id")
        } returns io.github.loshine.andpub.data.remote.tencent.TencentUpdateStatus(auditStatus = 3, message = "通过")

        val result = TencentMarketPublisher(remote)
            .refreshPublishStatus(tencentPublishRequest(app)).getOrThrow()

        result.status shouldBe PublishTaskStatus.Accepted
    }

})

// ─── Test helpers ──────────────────────────────────────────────────────────────

private val defaultApp = AppRecord("app-1", "Local App", "com.example.app")

private fun publishChannel(
    marketType: MarketType,
    marketAppId: String? = null,
    credentials: Map<String, String> = emptyMap(),
): ChannelRecord =
    ChannelRecord(
        id = "channel-${marketType.name}",
        appId = "app-1",
        marketType = marketType,
        marketAppId = marketAppId,
        credentials = credentials,
        extraFields = emptyMap(),
    )

private fun publishTask(
    marketType: MarketType,
    packageType: PackageType = PackageType.Apk,
    sourceType: ArtifactSourceType = ArtifactSourceType.LocalFile,
    artifactValue: String = "/tmp/app.apk",
    sha256: String = "sha256abc",
    md5: String = "md5abc",
    versionCode: Long? = 100L,
    versionName: String? = "1.0.0",
): PublishTaskRecord =
    PublishTaskRecord(
        id = "task-1",
        appId = "app-1",
        channelId = "channel-${marketType.name}",
        marketType = marketType,
        publishMode = PublishMode.UnifiedArtifact,
        artifact = ArtifactDraft(
            sourceType = sourceType,
            packageType = packageType,
            value = artifactValue,
            md5 = md5,
            sha256 = sha256,
            packageName = "com.example.app",
            versionCode = versionCode,
            versionName = versionName,
        ),
        status = PublishTaskStatus.Ready,
        logs = emptyList(),
    )

private fun splitPublishTask(marketType: MarketType): PublishTaskRecord =
    PublishTaskRecord(
        id = "task-split",
        appId = "app-1",
        channelId = "channel-${marketType.name}",
        marketType = marketType,
        publishMode = PublishMode.PerChannelArtifact,
        artifact = ArtifactDraft(
            packageType = PackageType.SplitApk,
            versionCode = 100L,
            versionName = "1.0.0",
            packageName = "com.example.app",
            split32 = ArtifactPart(value = "/tmp/app-32.apk", md5 = "md5-32", sha256 = "sha-32", packageName = "com.example.app"),
            split64 = ArtifactPart(value = "/tmp/app-64.apk", md5 = "md5-64", sha256 = "sha-64", packageName = "com.example.app"),
        ),
        status = PublishTaskStatus.Ready,
        logs = emptyList(),
    )

private fun honorPublishRequest(
    app: AppRecord,
    marketAppId: String? = null,
    packageType: PackageType = PackageType.Apk,
): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Honor,
            marketAppId = marketAppId,
            credentials = mapOf("clientId" to "cid", "clientSecret" to "secret"),
        ),
        task = publishTask(MarketType.Honor, packageType = packageType),
    )

private fun huaweiUrlPublishRequest(
    app: AppRecord,
    marketAppId: String? = null,
): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Huawei,
            marketAppId = marketAppId,
            credentials = mapOf("clientId" to "cid", "clientSecret" to "secret"),
        ),
        task = publishTask(
            MarketType.Huawei,
            sourceType = ArtifactSourceType.Url,
            artifactValue = "https://cdn.example.com/app-release.apk",
        ),
    )

private fun huaweiLocalFilePublishRequest(app: AppRecord): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Huawei,
            credentials = mapOf("clientId" to "cid", "clientSecret" to "secret"),
        ),
        task = publishTask(MarketType.Huawei, sourceType = ArtifactSourceType.LocalFile),
    )

private fun oppoPublishRequest(
    app: AppRecord,
    vendorTaskId: String? = null,
): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Oppo,
            credentials = mapOf("clientId" to "cid", "clientSecret" to "secret"),
        ),
        task = publishTask(MarketType.Oppo).let {
            if (vendorTaskId != null) it.copy(vendorTaskId = vendorTaskId, status = PublishTaskStatus.Submitted)
            else it
        },
    )

private fun oppoSplitPublishRequest(app: AppRecord): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Oppo,
            credentials = mapOf("clientId" to "cid", "clientSecret" to "secret"),
        ),
        task = splitPublishTask(MarketType.Oppo),
    )

private fun xiaomiPublishRequest(
    app: AppRecord,
    packageType: PackageType = PackageType.Apk,
): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Xiaomi,
            credentials = mapOf(
                "userName" to "dev@example.com",
                "password" to "pass",
                "publicKey" to "pub-key",
            ),
        ),
        task = publishTask(MarketType.Xiaomi, packageType = packageType),
    )

private fun tencentPublishRequest(
    app: AppRecord,
    marketAppId: String? = "tencent-id",
): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Tencent,
            marketAppId = marketAppId,
            credentials = mapOf("userId" to "user", "accessSecret" to "secret"),
        ),
        task = publishTask(MarketType.Tencent),
    )

private fun tencentSplitPublishRequest(app: AppRecord): MarketPublishRequest =
    MarketPublishRequest(
        app = app,
        channel = publishChannel(
            MarketType.Tencent,
            marketAppId = "tencent-id",
            credentials = mapOf("userId" to "user", "accessSecret" to "secret"),
        ),
        task = splitPublishTask(MarketType.Tencent),
    )
