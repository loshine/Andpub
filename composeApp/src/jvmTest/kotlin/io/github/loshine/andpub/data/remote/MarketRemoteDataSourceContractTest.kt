package io.github.loshine.andpub.data.remote

import io.github.loshine.andpub.data.remote.honor.HonorRemoteDataSource
import io.github.loshine.andpub.data.remote.huawei.HuaweiAuthContext
import io.github.loshine.andpub.data.remote.huawei.HuaweiRemoteDataSource
import io.github.loshine.andpub.data.remote.oppo.OppoRemoteDataSource
import io.github.loshine.andpub.data.remote.tencent.TencentRemoteDataSource
import io.github.loshine.andpub.data.remote.vivo.VivoRemoteDataSource
import io.github.loshine.andpub.data.remote.wecom.WeComWebhookRemoteDataSource
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiRemoteDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import java.security.KeyPairGenerator
import java.util.Base64

class MarketRemoteDataSourceContractTest : StringSpec({
    "Huawei API Client token uses documented oauth2 token endpoint" {
        val mock = CapturingHttpClient {
            """{"access_token":"token","expires_in":3600,"token_type":"Bearer"}"""
        }

        val result = HuaweiRemoteDataSource(mock.client).obtainToken(
            clientId = "cid",
            clientSecret = "secret",
        )

        result.accessToken shouldBe "token"
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/api/oauth2/v1/token"
        request.bodyText() shouldContain "client_credentials"
        request.bodyText() shouldContain "client_secret"
    }

    "Huawei API Client token surfaces ret business errors" {
        val mock = CapturingHttpClient {
            """{"ret":{"code":205524993,"msg":"client token auth failed"}}"""
        }

        shouldThrow<IllegalArgumentException> {
            HuaweiRemoteDataSource(mock.client).obtainToken(
                clientId = "cid",
                clientSecret = "secret",
            )
        }.message shouldContain "华为Token code=205524993: client token auth failed"
    }

    "Huawei API Client token surfaces oauth error fields" {
        val mock = CapturingHttpClient {
            """{"error":"invalid_client","error_description":"bad credentials"}"""
        }

        shouldThrow<IllegalStateException> {
            HuaweiRemoteDataSource(mock.client).obtainToken(
                clientId = "cid",
                clientSecret = "secret",
            )
        }.message shouldContain "华为 Token 接口未返回 access_token，invalid_client: bad credentials"
    }

    "Huawei API Client token surfaces top level code errors" {
        val mock = CapturingHttpClient {
            """{"code":203890688}"""
        }

        shouldThrow<IllegalArgumentException> {
            HuaweiRemoteDataSource(mock.client).obtainToken(
                clientId = "cid",
                clientSecret = "secret",
            )
        }.message shouldContain "华为Token code=203890688: client id or secret error"
    }

    "Huawei URL submit uses documented app-submit-with-file endpoint and Bearer headers" {
        val mock = CapturingHttpClient {
            """{"ret":{"code":0,"msg":"ok"}}"""
        }

        val result = HuaweiRemoteDataSource(mock.client).submitAppWithFile(
            auth = HuaweiAuthContext.ApiClient(clientId = "cid", accessToken = "token"),
            appId = "10001",
            body = """{"downloadUrl":"https://cdn.example.com/app.apk","requestId":"req-1"}""",
        )

        result.ret?.code shouldBe 0
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/api/publish/v2/app-submit-with-file"
        request.url.parameters["appId"] shouldBe "10001"
        request.headers["client_id"] shouldBe "cid"
        request.headers["Authorization"] shouldBe "Bearer token"
        request.bodyText() shouldContain "downloadUrl"
    }

    "Huawei app id list parses documented object ret responses" {
        val mock = CapturingHttpClient {
            """{"ret":{"code":0,"msg":"success"},"appids":[{"key":"猫耳FM","value":"10227369"}]}"""
        }

        val result = HuaweiRemoteDataSource(mock.client).getAppIdList(
            auth = HuaweiAuthContext.ApiClient(clientId = "cid", accessToken = "token"),
            packageName = "cn.missevan",
        )

        result.appIds.single().appId shouldBe "10227369"
        val request = mock.requests.single()
        request.url.encodedPath shouldBe "/api/publish/v2/appid-list"
        request.headers["client_id"] shouldBe "cid"
        request.headers["Authorization"] shouldBe "Bearer token"
    }

    "Huawei Service Account API calls use JWT Bearer header without API client headers" {
        val mock = CapturingHttpClient {
            """{"ret":{"code":0,"msg":"ok"}}"""
        }

        HuaweiRemoteDataSource(mock.client).submitAppWithFile(
            auth = HuaweiAuthContext.ServiceAccount(jwt = "header.payload.signature"),
            appId = "10001",
            body = """{"downloadUrl":"https://cdn.example.com/app.apk","requestId":"req-1"}""",
        )

        val request = mock.requests.single()
        request.headers["Authorization"] shouldBe "Bearer header.payload.signature"
        request.headers["client_id"] shouldBe null
        request.headers["teamId"] shouldBe null
        request.headers["oauth2Token"] shouldBe null
    }

    "Huawei OAuth Client API calls use teamId and oauth2Token headers" {
        val mock = CapturingHttpClient {
            """{"ret":{"code":0,"msg":"ok"}}"""
        }

        HuaweiRemoteDataSource(mock.client).submitAppWithFile(
            auth = HuaweiAuthContext.OAuthClient(teamId = "team-1", oauth2Token = "oauth-token"),
            appId = "10001",
            body = """{"downloadUrl":"https://cdn.example.com/app.apk","requestId":"req-1"}""",
        )

        val request = mock.requests.single()
        request.headers["teamId"] shouldBe "team-1"
        request.headers["oauth2Token"] shouldBe "oauth-token"
        request.headers["Authorization"] shouldBe null
        request.headers["client_id"] shouldBe null
    }

    "Honor URL upload uses documented upload-by-url endpoint and Bearer headers" {
        val mock = CapturingHttpClient {
            """{"code":0,"msg":"ok"}"""
        }

        val result = HonorRemoteDataSource(mock.client).uploadByUrl(
            token = "token",
            appId = "20002",
            body = """{"type":1,"fileUrl":"https://cdn.example.com/app.apk"}""",
        )

        result.code shouldBe 0
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/openapi/v1/publish/upload-by-url"
        request.url.parameters["appId"] shouldBe "20002"
        request.headers["Authorization"] shouldBe "Bearer token"
        request.bodyText() shouldContain "fileUrl"
    }

    "Honor current release parses documented data object" {
        val mock = CapturingHttpClient {
            """
            {
              "code": 0,
              "msg": "Success",
              "data": {
                "releaseId": "2052682500580466688",
                "auditMessage": "<p>审核通过</p>",
                "appId": 104426366,
                "auditResult": 1,
                "versionName": "6.6.3",
                "versionCode": "6060340"
              }
            }
            """.trimIndent()
        }

        val result = HonorRemoteDataSource(mock.client).getCurrentRelease(
            token = "token",
            appId = "104426366",
        )

        result?.versionName shouldBe "6.6.3"
        result?.versionCode shouldBe 6060340
        result?.auditResult shouldBe 1
        val request = mock.requests.single()
        request.url.encodedPath shouldBe "/openapi/v1/publish/get-app-current-release"
        request.url.parameters["appId"] shouldBe "104426366"
    }

    "WeCom webhook sends markdown robot messages" {
        val mock = CapturingHttpClient {
            """{"errcode":0,"errmsg":"ok"}"""
        }

        val result = WeComWebhookRemoteDataSource(mock.client).sendMarkdown(
            webhookUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=robot-key",
            content = "**应用市场版本巡检**",
        )

        result.errorCode shouldBe 0
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/cgi-bin/webhook/send"
        val body = request.bodyText()
        body shouldContain "\"msgtype\":\"markdown\""
        body shouldContain "应用市场版本巡检"
    }

    "Xiaomi query signs RequestData and posts to the legacy dev query endpoint" {
        val mock = CapturingHttpClient {
            """
            {
              "result": 0,
              "packageInfo": {
                "appName": "Demo",
                "packageName": "com.example.app",
                "versionName": "1.0.0",
                "versionCode": 100
              },
              "create": false,
              "updateVersion": true,
              "updateInfo": true
            }
            """.trimIndent()
        }

        val result = XiaomiRemoteDataSource(mock.client).queryApp(
            userName = "developer@example.com",
            password = "password",
            publicKey = generatedRsaPublicKeyPem(),
            packageName = "com.example.app",
        )

        result.packageInfo?.packageName shouldBe "com.example.app"
        result.updateVersion shouldBe true
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/devupload/dev/query"
        val body = request.bodyText()
        body shouldContain "RequestData="
        body shouldContain "SIG="
    }

    "OPPO publish app signs form params and treats errno zero as accepted async task" {
        val mock = CapturingHttpClient {
            """{"errno":0,"data":{"success":true,"message":"accepted","task_id":"task-1"}}"""
        }

        val result = OppoRemoteDataSource(mock.client).publishApp(
            accessToken = "token",
            clientSecret = "secret",
            params = mapOf("pkg_name" to "com.example.app", "version_code" to "100"),
        )

        result.success shouldBe true
        result.taskId shouldBe "task-1"
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/resource/v1/app/upd"
        val body = request.bodyText()
        body shouldContain "access_token=token"
        body shouldContain "pkg_name=com.example.app"
        body shouldContain "api_sign="
    }

    "vivo download update posts router method and maps code/subCode zero to task id" {
        val mock = CapturingHttpClient {
            """{"code":"0","subCode":"0","msg":"ok","data":{"task_id":"vivo-task-1"}}"""
        }

        val result = VivoRemoteDataSource(mock.client).updateApp(
            accessKey = "access",
            accessSecret = "secret",
            params = mapOf("packageName" to "com.example.app", "versionCode" to "100"),
        )

        result.taskId shouldBe "vivo-task-1"
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/router/rest"
        val body = request.bodyText()
        body shouldContain "method=app.update.app"
        body shouldContain "packageName=com.example.app"
        body shouldContain "sign="
    }

    "Tencent upload info signs form params and exposes COS presign data" {
        val mock = CapturingHttpClient {
            """{"ret":0,"msg":"ok","pre_sign_url":"https://cos.example.com/upload","serial_number":"serial-1"}"""
        }

        val result = TencentRemoteDataSource(mock.client).getFileUploadInfo(
            userId = "user",
            accessSecret = "secret",
            packageName = "com.example.app",
            appId = "30003",
            fileType = "apk",
            fileName = "app.apk",
        )

        result.preSignUrl shouldBe "https://cos.example.com/upload"
        result.serialNumber shouldBe "serial-1"
        val request = mock.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.encodedPath shouldBe "/open_file/developer_api/get_file_upload_info"
        val body = request.bodyText()
        body shouldContain "user_id=user"
        body shouldContain "pkg_name=com.example.app"
        body shouldContain "file_type=apk"
        body shouldContain "sign="
    }

    "remote decoder rejects empty and HTML responses with market context" {
        shouldThrow<IllegalArgumentException> {
            decodeResponse<Unit>("测试市场", "")
        }.message shouldContain "测试市场 返回了空响应"

        shouldThrow<IllegalArgumentException> {
            decodeResponse<Unit>("测试市场", "<html>error</html>")
        }.message shouldContain "测试市场 返回了非 JSON 响应"
    }

    "business error codes are not treated as success" {
        val mock = CapturingHttpClient {
            """{"errno":40001,"errmsg":"bad request"}"""
        }

        shouldThrow<IllegalArgumentException> {
            OppoRemoteDataSource(mock.client).getTaskState("token", "secret", "task-1")
        }.message shouldContain "OPPO任务状态 errno=40001: bad request"
    }
})

private fun generatedRsaPublicKeyPem(): String {
    val keyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(1024)
    }.generateKeyPair()
    val encoded = Base64.getMimeEncoder(64, "\n".encodeToByteArray())
        .encodeToString(keyPair.public.encoded)
    return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
}
