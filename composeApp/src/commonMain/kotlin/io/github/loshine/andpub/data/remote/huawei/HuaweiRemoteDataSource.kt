package io.github.loshine.andpub.data.remote.huawei

import io.github.loshine.andpub.data.remote.decodeResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import org.koin.core.annotation.Single

@Single
class HuaweiRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun obtainToken(
        clientId: String,
        clientSecret: String,
    ): HuaweiToken {
        val response = client.post("$AUTH_BASE/api/oauth2/v1/token") {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "grant_type" to "client_credentials",
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                )
            )
        }.decodeResponse<HuaweiTokenResponse>("华为Token")
        return HuaweiToken(
            accessToken = response.accessToken ?: error("华为 Token 接口未返回 access_token"),
            expiresIn = response.expiresIn,
            tokenType = response.tokenType,
        )
    }

    suspend fun getAppIdList(
        clientId: String,
        accessToken: String,
        packageName: String,
        packageTypes: String? = null,
        pcVersionName: String? = null,
    ): HuaweiAppIdList {
        val response = client.get("$PUBLISH_BASE/appid-list") {
            huaweiHeaders(clientId, accessToken)
            url {
                parameters.append("packageName", packageName)
                packageTypes?.let { parameters.append("packageTypes", it) }
                pcVersionName?.let { parameters.append("pcVersionName", it) }
            }
        }.decodeResponse<HuaweiResponse>("华为应用ID列表")
        response.requireSuccess("华为应用ID列表")
        return HuaweiAppIdList(response.appids)
    }

    suspend fun getAppInfo(
        clientId: String,
        accessToken: String,
        appId: String,
        lang: String? = null,
        releaseType: Int? = null,
    ): HuaweiAppInfoResult {
        val response = client.get("$PUBLISH_BASE/app-info") {
            huaweiHeaders(clientId, accessToken)
            url {
                parameters.append("appId", appId)
                lang?.let { parameters.append("lang", it) }
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
        }.decodeResponse<HuaweiResponse>("华为应用信息")
        response.requireSuccess("华为应用信息")
        return HuaweiAppInfoResult(
            appInfo = response.appInfo,
            auditInfo = response.auditInfo,
            languages = response.languages,
            phasedReleaseInfo = response.phasedReleaseInfo,
        )
    }

    suspend fun updateAppInfo(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/app-info") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新应用信息")

    suspend fun updateLanguageInfo(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/app-language-info") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新语言信息")

    suspend fun deleteLanguageInfo(
        clientId: String,
        accessToken: String,
        appId: String,
        lang: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.delete("$PUBLISH_BASE/app-language-info") {
            huaweiHeaders(clientId, accessToken)
            url {
                parameters.append("appId", appId)
                parameters.append("lang", lang)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
        }.parseHuaweiOperationResult("华为删除语言信息")

    suspend fun updateFileInfo(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/app-file-info") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新文件信息")

    suspend fun submitApp(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.post("$PUBLISH_BASE/app-submit") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为提交发布")

    suspend fun submitAppWithFile(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.post("$PUBLISH_BASE/app-submit-with-file") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为URL提交发布")

    suspend fun submitPackageByUrl(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.post("$PUBLISH_BASE/app-package-file/by-url") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为URL提交软件包")

    suspend fun getPackageList(
        clientId: String,
        accessToken: String,
        appId: String,
        fromRecCount: Int = 1,
        maxReqCount: Int = 10,
    ): HuaweiOperationResult =
        client.get("$PUBLISH_BASE/package-list") {
            huaweiHeaders(clientId, accessToken)
            url {
                parameters.append("appId", appId)
                parameters.append("fromRecCount", fromRecCount.toString())
                parameters.append("maxReqCount", maxReqCount.toString())
            }
        }.parseHuaweiOperationResult("华为软件包列表")

    suspend fun getPackageCompileStatus(
        clientId: String,
        accessToken: String,
        appId: String,
        pkgIds: String,
    ): HuaweiOperationResult =
        client.get("$PUBLISH_BASE/package/compile/status") {
            huaweiHeaders(clientId, accessToken)
            url {
                parameters.append("appId", appId)
                parameters.append("pkgIds", pkgIds)
            }
        }.parseHuaweiOperationResult("华为AAB编译状态")

    suspend fun updatePhasedRelease(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/phased-release") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新分阶段发布")

    suspend fun updateOnShelfTime(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/on-shelf-time") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新上架时间")

    suspend fun updateGmsProperties(
        clientId: String,
        accessToken: String,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/properties/gms") {
            huaweiHeaders(clientId, accessToken)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为设置GMS依赖")

    private fun HttpRequestBuilder.huaweiHeaders(
        clientId: String,
        accessToken: String,
    ) {
        header("client_id", clientId)
        header("Authorization", "Bearer $accessToken")
        header(HttpHeaders.Accept, "application/json")
    }

    private companion object {
        const val AUTH_BASE = "https://connect-api.cloud.huawei.com"
        const val PUBLISH_BASE = "$AUTH_BASE/api/publish/v2"
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.parseHuaweiOperationResult(marketName: String): HuaweiOperationResult {
    val response = decodeResponse<HuaweiResponse>(marketName)
    return HuaweiOperationResult(response.requireSuccess(marketName))
}

private fun HuaweiResponse.requireSuccess(marketName: String): HuaweiRet? {
    val ret = ret?.takeIf { it.isNotBlank() }?.let {
        decodeResponse<HuaweiRet>("$marketName ret", it)
    }
    val code = ret?.code ?: return ret
    require(code == 0) {
        "$marketName code=$code: ${ret.message.orEmpty()}"
    }
    return ret
}
