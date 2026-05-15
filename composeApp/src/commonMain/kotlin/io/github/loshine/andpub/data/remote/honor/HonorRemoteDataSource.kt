package io.github.loshine.andpub.data.remote.honor

import io.github.loshine.andpub.data.remote.decodeResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.parameters
import org.koin.core.annotation.Single

@Single
class HonorRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun obtainToken(
        clientId: String,
        clientSecret: String,
    ): HonorToken {
        val text = client.submitForm(
            url = "$AUTH_BASE/auth/token",
            formParameters = parameters {
                append("grant_type", "client_credentials")
                append("client_id", clientId)
                append("client_secret", clientSecret)
            },
        ).bodyAsText()
        val response = decodeResponse<HonorTokenResponse>("荣耀Token", text)
        return HonorToken(
            accessToken = response.accessToken ?: error("荣耀 token 获取失败"),
            expiresIn = response.expiresIn,
            tokenType = response.tokenType,
        )
    }

    suspend fun getAppId(
        token: String,
        packageName: String,
    ): HonorAppIdList {
        val response = client.get("$PUBLISH_BASE/get-app-id") {
            honorHeaders(token)
            url { parameters.append("pkgName", packageName) }
        }.decodeResponse<HonorResponse<List<HonorAppIdEntry>>>("荣耀APPID")
        response.requireSuccess("荣耀APPID")
        return HonorAppIdList(response.data.orEmpty())
    }

    suspend fun getAppDetail(
        token: String,
        appId: String,
    ): HonorAppDetail {
        val response = client.get("$PUBLISH_BASE/get-app-detail") {
            honorHeaders(token)
            url { parameters.append("appId", appId) }
        }.decodeResponse<HonorResponse<HonorAppDetailData>>("荣耀应用详情")
        response.requireSuccess("荣耀应用详情")
        return response.data?.toHonorAppDetail() ?: error("荣耀应用详情未返回 data")
    }

    suspend fun getCurrentRelease(
        token: String,
        appId: String,
    ): HonorCurrentRelease? {
        val response = client.get("$PUBLISH_BASE/get-app-current-release") {
            honorHeaders(token)
            url { parameters.append("appId", appId) }
        }.decodeResponse<HonorResponse<List<HonorCurrentReleaseData>>>("荣耀最新版本状态")
        response.requireSuccess("荣耀最新版本状态")
        return response.data.orEmpty().firstOrNull()?.toHonorCurrentRelease()
    }

    suspend fun getFileUploadUrl(
        token: String,
        appId: String,
        body: String,
    ): HonorFileUploadPaths {
        val response = client.post("$PUBLISH_BASE/get-file-upload-url") {
            honorHeaders(token)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.decodeResponse<HonorResponse<List<HonorFileUploadPath>>>("荣耀获取上传路径")
        response.requireSuccess("荣耀获取上传路径")
        return HonorFileUploadPaths(response.data.orEmpty())
    }

    suspend fun uploadFile(
        token: String,
        appId: String,
        objectId: String,
        formParameters: Parameters,
    ): HonorOperationResult {
        val text = client.submitForm(
            url = "$PUBLISH_BASE/file-upload",
            formParameters = formParameters,
        ) {
            honorHeaders(token)
            url {
                parameters.append("appId", appId)
                parameters.append("objectId", objectId)
            }
        }.bodyAsText()
        return decodeResponse<HonorResponse<Unit>>("荣耀上传文件", text).toHonorOperationResult("荣耀上传文件")
    }

    suspend fun uploadByUrl(
        token: String,
        appId: String,
        body: String,
    ): HonorOperationResult =
        client.post("$PUBLISH_BASE/upload-by-url") {
            honorHeaders(token)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHonorOperationResult("荣耀URL上传")

    suspend fun updateFileInfo(
        token: String,
        appId: String,
        body: String,
    ): HonorOperationResult =
        client.post("$PUBLISH_BASE/update-file-info") {
            honorHeaders(token)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHonorOperationResult("荣耀更新文件信息")

    suspend fun updateAppInfo(
        token: String,
        appId: String,
        body: String,
    ): HonorOperationResult =
        client.post("$PUBLISH_BASE/update-app-info") {
            honorHeaders(token)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHonorOperationResult("荣耀更新应用信息")

    suspend fun updateLanguageInfo(
        token: String,
        appId: String,
        body: String,
    ): HonorOperationResult =
        client.post("$PUBLISH_BASE/update-language-info") {
            honorHeaders(token)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHonorOperationResult("荣耀更新语言信息")

    suspend fun submitAudit(
        token: String,
        appId: String,
        body: String,
    ): HonorOperationResult =
        client.post("$PUBLISH_BASE/submit-audit") {
            honorHeaders(token)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHonorOperationResult("荣耀提交审核")

    suspend fun getAuditResult(
        token: String,
        body: String,
    ): HonorOperationResult =
        client.post("$PUBLISH_BASE/get-audit-result") {
            honorHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.parseHonorOperationResult("荣耀审核状态")

    private fun HttpRequestBuilder.honorHeaders(token: String) {
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $token")
    }

    private companion object {
        const val AUTH_BASE = "https://iam.developer.honor.com"
        const val PUBLISH_BASE = "https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish"
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.parseHonorOperationResult(marketName: String): HonorOperationResult =
    decodeResponse<HonorResponse<Unit>>(marketName).toHonorOperationResult(marketName)

private fun HonorResponse<*>.toHonorOperationResult(marketName: String): HonorOperationResult {
    requireSuccess(marketName)
    return HonorOperationResult(code = code, message = message)
}

private fun HonorResponse<*>.requireSuccess(marketName: String) {
    val code = code ?: return
    require(code == 0) {
        "$marketName code=$code: ${message.orEmpty()}"
    }
}

private fun HonorAppDetailData.toHonorAppDetail(): HonorAppDetail {
    val release = releaseInfo ?: currentRelease
    return HonorAppDetail(
        appId = appId,
        packageName = packageName,
        appName = appName,
        versionName = release?.versionName ?: versionName,
        versionCode = release?.versionCode ?: versionCode,
    )
}

private fun HonorCurrentReleaseData.toHonorCurrentRelease(): HonorCurrentRelease =
    HonorCurrentRelease(
        versionName = versionName,
        versionCode = versionCode,
        auditStatus = auditStatus,
        auditResult = auditResult,
        releaseStatus = releaseStatus,
    )
