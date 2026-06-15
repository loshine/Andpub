package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.honor.HonorAppDetail
import io.github.loshine.andpub.data.remote.honor.HonorAppIdEntry
import io.github.loshine.andpub.data.remote.honor.HonorAppIdList
import io.github.loshine.andpub.data.remote.honor.HonorCurrentRelease
import io.github.loshine.andpub.data.remote.honor.HonorRemoteDataSource
import io.github.loshine.andpub.data.remote.honor.HonorToken
import io.github.loshine.andpub.data.remote.huawei.HuaweiAppIdEntry
import io.github.loshine.andpub.data.remote.huawei.HuaweiAppIdList
import io.github.loshine.andpub.data.remote.huawei.HuaweiAppInfo
import io.github.loshine.andpub.data.remote.huawei.HuaweiAppInfoResult
import io.github.loshine.andpub.data.remote.huawei.HuaweiAuditInfo
import io.github.loshine.andpub.data.remote.huawei.HuaweiLanguageInfo
import io.github.loshine.andpub.data.remote.huawei.HuaweiPhasedReleaseInfo
import io.github.loshine.andpub.data.remote.huawei.HuaweiAuthContext
import io.github.loshine.andpub.data.remote.huawei.HuaweiJwtGenerator
import io.github.loshine.andpub.data.remote.huawei.HuaweiRemoteDataSource
import io.github.loshine.andpub.data.remote.huawei.HuaweiToken
import io.github.loshine.andpub.domain.market.HuaweiCredentialKeys
import io.github.loshine.andpub.data.remote.oppo.OppoAppInfo
import io.github.loshine.andpub.data.remote.oppo.OppoAppInfoResult
import io.github.loshine.andpub.data.remote.oppo.OppoRemoteDataSource
import io.github.loshine.andpub.data.remote.oppo.OppoToken
import io.github.loshine.andpub.data.remote.tencent.TencentAppDetail
import io.github.loshine.andpub.data.remote.tencent.TencentRemoteDataSource
import io.github.loshine.andpub.data.remote.tencent.TencentUpdateStatus
import io.github.loshine.andpub.data.remote.vivo.VivoAppDetail
import io.github.loshine.andpub.data.remote.vivo.VivoOperationResult
import io.github.loshine.andpub.data.remote.vivo.VivoRemoteDataSource
import io.github.loshine.andpub.data.remote.vivo.VivoSplitUpdateRequest
import io.github.loshine.andpub.data.remote.vivo.VivoTaskStatus
import io.github.loshine.andpub.data.remote.vivo.VivoUnifiedUpdateRequest
import io.github.loshine.andpub.data.remote.vivo.VivoUploadResult
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiPackageInfo
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiQueryAppResult
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiRemoteDataSource
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.domain.model.VivoCompatibleDevice
import io.github.loshine.andpub.domain.model.VivoOnlineType
import io.github.loshine.andpub.domain.model.VivoPublishOptions
import io.github.loshine.andpub.domain.model.withVivoEnvironment
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class MarketPublisherTest : StringSpec({
    val app = AppRecord("app-1", "Local App", "com.example.app")

    "Huawei publisher supports Service Account when selected" {
        val remote = mockk<HuaweiRemoteDataSource>()
        val jwtGenerator = mockk<HuaweiJwtGenerator>()
        val auth = HuaweiAuthContext.ServiceAccount("jwt-token")
        every { jwtGenerator.createServiceAccountJwt(any()) } returns "jwt-token"
        coEvery { remote.getAppIdList(auth, "com.example.app") } returns HuaweiAppIdList(
            listOf(HuaweiAppIdEntry(appId = "huawei-app-id")),
        )
        coEvery { remote.getAppInfo(auth, "huawei-app-id") } returns HuaweiAppInfoResult(
            appInfo = HuaweiAppInfo(releaseState = 4, versionNumber = "1.2.3", onShelfVersionNumber = "1.2.2"),
            auditInfo = HuaweiAuditInfo(auditOpinion = "等待人工审核", copyRightAuditResult = 0),
            languages = listOf(HuaweiLanguageInfo(appName = "Huawei App")),
            phasedReleaseInfo = HuaweiPhasedReleaseInfo("RELEASE"),
        )

        val info = HuaweiMarketPublisher(remote, jwtGenerator).fetchAppInfo(
            app,
            channel(
                MarketType.Huawei,
                credentials = mapOf(
                    HuaweiCredentialKeys.AuthMode to "serviceAccount",
                    "serviceAccountJson" to """
                        {
                          "key_id": "kid",
                          "private_key": "private-key",
                          "sub_account": "sub",
                          "token_uri": "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
                        }
                    """.trimIndent(),
                ),
            ),
        ).getOrThrow()

        info.marketAppId shouldBe "huawei-app-id"
        coVerify(exactly = 0) { remote.obtainToken(any(), any()) }
        coVerify { remote.getAppIdList(auth, "com.example.app") }
        coVerify { remote.getAppInfo(auth, "huawei-app-id") }
    }

    "Huawei publisher defaults to API Client auth and keeps legacy channels compatible" {
        val remote = mockk<HuaweiRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HuaweiToken("token", 3600, "Bearer")
        val auth = HuaweiAuthContext.ApiClient(clientId = "cid", accessToken = "token")
        coEvery { remote.getAppIdList(auth, "com.example.app") } returns HuaweiAppIdList(
            listOf(HuaweiAppIdEntry(appId = "huawei-app-id")),
        )
        coEvery { remote.getAppInfo(auth, "huawei-app-id") } returns HuaweiAppInfoResult(
            appInfo = HuaweiAppInfo(releaseState = 4, versionNumber = "1.2.3", onShelfVersionNumber = "1.2.2"),
            auditInfo = HuaweiAuditInfo(auditOpinion = "等待人工审核", copyRightAuditResult = 0),
            languages = listOf(HuaweiLanguageInfo(appName = "Huawei App")),
            phasedReleaseInfo = HuaweiPhasedReleaseInfo("RELEASE"),
        )

        val info = HuaweiMarketPublisher(remote).fetchAppInfo(
            app,
            channel(MarketType.Huawei, credentials = mapOf("clientId" to "cid", "clientSecret" to "secret")),
        ).getOrThrow()

        info.marketAppId shouldBe "huawei-app-id"
        info.appName shouldBe "Huawei App"
        info.onlineVersion shouldBe "1.2.2"
        info.auditStatus shouldBe "审核中，等待人工审核，版权审核通过"
        info.releaseStatus shouldBe "审核中，分阶段发布中"
        coVerify { remote.getAppIdList(auth, "com.example.app") }
    }

    "Huawei publisher supports OAuth Client auth" {
        val remote = mockk<HuaweiRemoteDataSource>()
        val auth = HuaweiAuthContext.OAuthClient(teamId = "team-1", oauth2Token = "oauth-token")
        coEvery { remote.getAppIdList(auth, "com.example.app") } returns HuaweiAppIdList(
            listOf(HuaweiAppIdEntry(appId = "huawei-app-id")),
        )
        coEvery { remote.getAppInfo(auth, "huawei-app-id") } returns HuaweiAppInfoResult(
            appInfo = HuaweiAppInfo(releaseState = 0, versionNumber = "1.2.3", onShelfVersionNumber = "1.2.3"),
            auditInfo = null,
            languages = listOf(HuaweiLanguageInfo(appName = "Huawei App")),
            phasedReleaseInfo = null,
        )

        val info = HuaweiMarketPublisher(remote).fetchAppInfo(
            app,
            channel(
                MarketType.Huawei,
                credentials = mapOf(
                    HuaweiCredentialKeys.AuthMode to "oauthClient",
                    "teamId" to "team-1",
                    "oauth2Token" to "oauth-token",
                ),
            ),
        ).getOrThrow()

        info.marketAppId shouldBe "huawei-app-id"
        coVerify(exactly = 0) { remote.obtainToken(any(), any()) }
        coVerify { remote.getAppInfo(auth, "huawei-app-id") }
    }

    "Honor publisher follows docs: token, APPID, current release audit result" {
        val remote = mockk<HonorRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns HonorToken("token", 3600, "Bearer")
        coEvery { remote.getAppId("token", "com.example.app") } returns HonorAppIdList(
            listOf(HonorAppIdEntry(appId = "10001", packageName = "com.example.app")),
        )
        coEvery { remote.getAppDetail("token", "10001") } returns HonorAppDetail(
            appId = "10001",
            packageName = "com.example.app",
            appName = "Honor App",
            versionName = "1.0.0",
            versionCode = 10,
        )
        coEvery { remote.getCurrentRelease("token", "10001") } returns HonorCurrentRelease(
            versionName = "1.1.0",
            versionCode = 11,
            auditStatus = "reviewing",
            auditResult = 1,
            releaseStatus = "publishing",
        )

        val info = HonorMarketPublisher(remote).fetchAppInfo(
            app,
            channel(MarketType.Honor, credentials = mapOf("clientId" to "cid", "clientSecret" to "secret")),
        ).getOrThrow()

        info.marketAppId shouldBe "10001"
        info.onlineVersion shouldBe "1.1.0"
        info.auditStatus shouldBe "审核通过"
        info.releaseStatus shouldBe "publishing"
    }

    "Xiaomi publisher follows docs: query package status and exposes update capability" {
        val remote = mockk<XiaomiRemoteDataSource>()
        coEvery {
            remote.queryApp("developer@example.com", "password", "public-key", "com.example.app")
        } returns XiaomiQueryAppResult(
            packageInfo = XiaomiPackageInfo(
                appName = "Xiaomi App",
                packageName = "com.example.app",
                versionName = "2.0.0",
                versionCode = 20,
            ),
            create = false,
            updateVersion = true,
            updateInfo = false,
        )

        val info = XiaomiMarketPublisher(remote).fetchAppInfo(
            app,
            channel(
                MarketType.Xiaomi,
                credentials = mapOf(
                    "userName" to "developer@example.com",
                    "password" to "password",
                    "publicKey" to "public-key",
                ),
            ),
        ).getOrThrow()

        info.marketAppId shouldBe "com.example.app"
        info.appName shouldBe "Xiaomi App"
        info.auditStatus shouldBe "小米暂不支持获取审核状态"
        info.releaseStatus shouldBe "已存在"
    }

    "OPPO publisher follows docs: token, signed app info, audit and release status names" {
        val remote = mockk<OppoRemoteDataSource>()
        coEvery { remote.obtainToken("cid", "secret") } returns OppoToken("token", 3600)
        coEvery { remote.getAppInfo("token", "secret", "com.example.app") } returns OppoAppInfoResult(
            OppoAppInfo(
                appId = "oppo-app-id",
                packageName = "com.example.app",
                appName = "OPPO App",
                versionName = "3.0.0",
                auditStatus = "1",
                auditStatusName = "审核中",
                releaseStatus = "111",
            )
        )

        val info = OppoMarketPublisher(remote).fetchAppInfo(
            app,
            channel(MarketType.Oppo, credentials = mapOf("clientId" to "cid", "clientSecret" to "secret")),
        ).getOrThrow()

        info.marketAppId shouldBe "oppo-app-id"
        info.auditStatus shouldBe "审核中"
        info.releaseStatus shouldBe "上线"
    }

    "vivo publisher follows docs: app query detail maps sale and online status" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery { remote.queryAppDetails("access", "secret", "com.example.app", VivoApiEnvironment.Production) } returns VivoAppDetail(
            packageName = "com.example.app",
            appName = "vivo App",
            versionName = "4.0.0",
            status = 3,
            saleStatus = 1,
            onlineStatus = null,
            onlineType = 1,
        )

        val info = VivoMarketPublisher(remote).fetchAppInfo(
            app,
            channel(MarketType.Vivo, credentials = mapOf("accessKey" to "access", "accessSecret" to "secret")),
        ).getOrThrow()

        info.auditStatus shouldBe "审核通过"
        info.releaseStatus shouldBe "已上架，实时上架"
    }

    "vivo publisher uploads and submits unified APK update" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery { remote.queryAppDetails("access", "secret", "com.example.app", VivoApiEnvironment.Sandbox) } returns
            VivoAppDetail(packageName = "com.example.app")
        coEvery {
            remote.uploadApk(
                accessKey = "access",
                accessSecret = "secret",
                packageName = "com.example.app",
                fileName = "app.apk",
                fileBytes = any(),
                fileMd5 = "md5",
                environment = VivoApiEnvironment.Sandbox,
            )
        } returns VivoUploadResult(packageName = "com.example.app", serialNumber = "serial-1", md5 = "md5")
        coEvery {
            remote.syncUpdateApp(
                accessKey = "access",
                accessSecret = "secret",
                request = VivoUnifiedUpdateRequest(
                    packageName = "com.example.app",
                    versionCode = 100,
                    apk = "serial-1",
                    fileMd5 = "md5",
                    onlineType = "1",
                    compatibleDevice = "1",
                ),
                environment = VivoApiEnvironment.Sandbox,
            )
        } returns VivoOperationResult(message = "ok", taskId = "task-1")

        val result = VivoMarketPublisher(remote, readFile = { Result.success("apk".encodeToByteArray()) })
            .publish(vivoPublishRequest())
            .getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorTaskId shouldBe "task-1"
        result.vendorUploadIds shouldBe listOf("serial-1")
        coVerify { remote.syncUpdateApp(any(), any(), any(), VivoApiEnvironment.Sandbox) }
    }

    "vivo publisher skips sandbox app detail precheck when sandbox does not support query details" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery {
            remote.queryAppDetails("access", "secret", "com.example.app", VivoApiEnvironment.Sandbox)
        } throws IllegalStateException("vivo应用详情 code=10010: 此功能不存在，请联系vivo开放平台")
        coEvery {
            remote.uploadApk(
                accessKey = "access",
                accessSecret = "secret",
                packageName = "com.example.app",
                fileName = "app.apk",
                fileBytes = any(),
                fileMd5 = "md5",
                environment = VivoApiEnvironment.Sandbox,
            )
        } returns VivoUploadResult(packageName = "com.example.app", serialNumber = "serial-1", md5 = "md5")
        coEvery {
            remote.syncUpdateApp(any(), any(), any(), VivoApiEnvironment.Sandbox)
        } returns VivoOperationResult(message = "ok", taskId = "task-1")

        val result = VivoMarketPublisher(remote, readFile = { Result.success("apk".encodeToByteArray()) })
            .publish(vivoPublishRequest())
            .getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.logs.map { it.message } shouldContain "vivo 测试环境不支持 app.query.details，已跳过应用存在性预校验"
        coVerify { remote.uploadApk(any(), any(), any(), any(), any(), any(), any(), VivoApiEnvironment.Sandbox) }
        coVerify { remote.syncUpdateApp(any(), any(), any(), VivoApiEnvironment.Sandbox) }
    }

    "vivo publisher returns failure when unified APK upload fails" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery { remote.queryAppDetails(any(), any(), any(), any()) } returns VivoAppDetail(packageName = "com.example.app")
        coEvery {
            remote.uploadApk(any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("upload rejected")

        val result = VivoMarketPublisher(remote, readFile = { Result.success("apk".encodeToByteArray()) })
            .publish(vivoPublishRequest())

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "upload rejected"
    }

    "vivo publisher uploads split APKs and submits split update" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery { remote.queryAppDetails(any(), any(), any(), VivoApiEnvironment.Sandbox) } returns
            VivoAppDetail(packageName = "com.example.app")
        coEvery { remote.uploadApk32(any(), any(), any(), any(), any(), any(), any(), VivoApiEnvironment.Sandbox) } returns
            VivoUploadResult(serialNumber = "serial-32")
        coEvery { remote.uploadApk64(any(), any(), any(), any(), any(), any(), any(), VivoApiEnvironment.Sandbox) } returns
            VivoUploadResult(serialNumber = "serial-64")
        coEvery {
            remote.syncUpdateSubpackageApp(
                "access",
                "secret",
                VivoSplitUpdateRequest("com.example.app", "serial-32", "serial-64", "1", "1"),
                VivoApiEnvironment.Sandbox,
            )
        } returns VivoOperationResult(message = "ok", taskId = "split-task")

        val result = VivoMarketPublisher(remote, readFile = { Result.success("apk".encodeToByteArray()) })
            .publish(vivoPublishRequest(task = splitPublishTask()))
            .getOrThrow()

        result.status shouldBe PublishTaskStatus.Submitted
        result.vendorUploadIds shouldBe listOf("serial-32", "serial-64")
    }

    "vivo publisher refreshes task status to accepted when vendor reports success" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery {
            remote.queryTaskStatus(
                accessKey = "access",
                accessSecret = "secret",
                packageName = "com.example.app",
                packetType = 0,
                environment = VivoApiEnvironment.Sandbox,
            )
        } returns VivoTaskStatus(packageName = "com.example.app", taskStatus = 3)

        val result = VivoMarketPublisher(remote)
            .refreshPublishStatus(vivoPublishRequest(task = unifiedPublishTask().copy(status = PublishTaskStatus.Submitted)))
            .getOrThrow()

        result.status shouldBe PublishTaskStatus.Accepted
        result.logs.single().message shouldContain "处理成功"
    }

    "vivo publisher does not submit split update when one split upload fails" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery { remote.queryAppDetails(any(), any(), any(), any()) } returns VivoAppDetail(packageName = "com.example.app")
        coEvery { remote.uploadApk32(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            VivoUploadResult(serialNumber = "serial-32")
        coEvery { remote.uploadApk64(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("64 failed")

        val result = VivoMarketPublisher(remote, readFile = { Result.success("apk".encodeToByteArray()) })
            .publish(vivoPublishRequest(task = splitPublishTask()))

        result.isFailure shouldBe true
        coVerify(exactly = 0) { remote.syncUpdateSubpackageApp(any(), any(), any(), any()) }
    }

    "vivo publisher fails before upload when submit options are missing" {
        val remote = mockk<VivoRemoteDataSource>(relaxed = true)

        val result = VivoMarketPublisher(remote, readFile = { Result.success("apk".encodeToByteArray()) })
            .publish(vivoPublishRequest(options = VivoPublishOptions()))

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "onlineType"
        coVerify(exactly = 0) { remote.queryAppDetails(any(), any(), any(), any()) }
    }

    "vivo publisher fails before upload when existing app cannot be proven" {
        val remote = mockk<VivoRemoteDataSource>()
        coEvery { remote.queryAppDetails(any(), any(), any(), any()) } throws IllegalStateException("not found")

        val result = VivoMarketPublisher(remote, readFile = { Result.success("apk".encodeToByteArray()) })
            .publish(vivoPublishRequest())

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "首次创建不支持"
        coVerify(exactly = 0) { remote.uploadApk(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    "Tencent publisher follows docs: app detail, update audit status, store version fallback" {
        val remote = mockk<TencentRemoteDataSource>()
        coEvery { remote.queryAppDetail("user", "secret", "com.example.app", "tencent-app-id") } returns TencentAppDetail(
            packageName = "com.example.app",
            appName = "Tencent App",
            versionName = null,
        )
        coEvery { remote.queryAppUpdateStatus("user", "secret", "com.example.app", "tencent-app-id") } returns TencentUpdateStatus(
            auditStatus = 2,
            message = "rejected",
        )
        coEvery { remote.getStorePage("com.example.app") } returns "<html><body>版本号：5.0.1</body></html>"

        val info = TencentMarketPublisher(remote).fetchAppInfo(
            app,
            channel(
                MarketType.Tencent,
                marketAppId = "tencent-app-id",
                credentials = mapOf("userId" to "user", "accessSecret" to "secret"),
            ),
        ).getOrThrow()

        info.onlineVersion shouldBe "5.0.1"
        info.auditStatus shouldBe "审核驳回"
        info.releaseStatus shouldBe "已上架，更新审核驳回"
    }

    "publisher returns failure when a required credential is missing" {
        val result = XiaomiMarketPublisher(mockk()).fetchAppInfo(
            app,
            channel(MarketType.Xiaomi, credentials = mapOf("userName" to "developer@example.com")),
        )

        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "缺少 password"
    }
})

private fun channel(
    marketType: MarketType,
    marketAppId: String? = null,
    credentials: Map<String, String>,
    extraFields: Map<String, String> = emptyMap(),
): ChannelRecord =
    ChannelRecord(
        id = "channel-${marketType.name}",
        appId = "app-1",
        marketType = marketType,
        marketAppId = marketAppId,
        credentials = credentials,
        extraFields = extraFields,
    )

private fun vivoPublishRequest(
    task: PublishTaskRecord = unifiedPublishTask(),
    options: VivoPublishOptions = VivoPublishOptions(
        onlineType = VivoOnlineType.Realtime,
        compatibleDevice = VivoCompatibleDevice.Phone,
        productionConfirmed = true,
    ),
): MarketPublishRequest =
    MarketPublishRequest(
        app = AppRecord("app-1", "Local App", "com.example.app"),
        channel = channel(
            MarketType.Vivo,
            credentials = mapOf("accessKey" to "access", "accessSecret" to "secret"),
            extraFields = emptyMap<String, String>().withVivoEnvironment(VivoApiEnvironment.Sandbox),
        ),
        task = task,
        vivoOptions = options,
    )

private fun unifiedPublishTask(): PublishTaskRecord =
    PublishTaskRecord(
        id = "task-1",
        appId = "app-1",
        channelId = "channel-Vivo",
        marketType = MarketType.Vivo,
        publishMode = PublishMode.UnifiedArtifact,
        artifact = ArtifactDraft(
            packageType = PackageType.Apk,
            value = "/tmp/app.apk",
            md5 = "md5",
            packageName = "com.example.app",
            versionCode = 100,
        ),
        status = PublishTaskStatus.Ready,
        logs = emptyList(),
    )

private fun splitPublishTask(): PublishTaskRecord =
    unifiedPublishTask().copy(
        publishMode = PublishMode.PerChannelArtifact,
        artifact = ArtifactDraft(
            packageType = PackageType.SplitApk,
            split32 = ArtifactPart(value = "/tmp/app-32.apk", md5 = "md5-32", packageName = "com.example.app"),
            split64 = ArtifactPart(value = "/tmp/app-64.apk", md5 = "md5-64", packageName = "com.example.app"),
        ),
    )
