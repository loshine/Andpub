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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.annotation.Single

@Single
class HuaweiRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun obtainToken(
        clientId: String,
        clientSecret: String,
    ): HuaweiToken {
        val response = client.post("$AUTH_BASE/oauth2/v1/token") {
            contentType(ContentType.Application.Json)
            setBody(
                JsonObject(
                    mapOf(
                        "grant_type" to JsonPrimitive("client_credentials"),
                        "client_id" to JsonPrimitive(clientId),
                        "client_secret" to JsonPrimitive(clientSecret),
                    )
                ).toString()
            )
        }.decodeResponse<HuaweiTokenResponse>("华为Token")
        response.ret.requireSuccess("华为Token")
        response.requireSuccess()
        return HuaweiToken(
            accessToken = response.accessToken ?: error(response.missingAccessTokenMessage()),
            expiresIn = response.expiresIn,
            tokenType = response.tokenType,
        )
    }

    suspend fun getAppIdList(
        auth: HuaweiAuthContext,
        packageName: String,
        packageTypes: String? = null,
        pcVersionName: String? = null,
    ): HuaweiAppIdList {
        val response = client.get("$PUBLISH_BASE/appid-list") {
            huaweiHeaders(auth)
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
        auth: HuaweiAuthContext,
        appId: String,
        lang: String? = null,
        releaseType: Int? = null,
    ): HuaweiAppInfoResult {
        val response = client.get("$PUBLISH_BASE/app-info") {
            huaweiHeaders(auth)
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
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/app-info") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新应用信息")

    suspend fun updateLanguageInfo(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/app-language-info") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新语言信息")

    suspend fun deleteLanguageInfo(
        auth: HuaweiAuthContext,
        appId: String,
        lang: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.delete("$PUBLISH_BASE/app-language-info") {
            huaweiHeaders(auth)
            url {
                parameters.append("appId", appId)
                parameters.append("lang", lang)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
        }.parseHuaweiOperationResult("华为删除语言信息")

    suspend fun updateFileInfo(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/app-file-info") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新文件信息")

    suspend fun submitApp(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.post("$PUBLISH_BASE/app-submit") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为提交发布")

    suspend fun submitAppWithFile(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.post("$PUBLISH_BASE/app-submit-with-file") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为URL提交发布")

    suspend fun submitPackageByUrl(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.post("$PUBLISH_BASE/app-package-file/by-url") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为URL提交软件包")

    suspend fun getPackageList(
        auth: HuaweiAuthContext,
        appId: String,
        fromRecCount: Int = 1,
        maxReqCount: Int = 10,
    ): HuaweiOperationResult =
        client.get("$PUBLISH_BASE/package-list") {
            huaweiHeaders(auth)
            url {
                parameters.append("appId", appId)
                parameters.append("fromRecCount", fromRecCount.toString())
                parameters.append("maxReqCount", maxReqCount.toString())
            }
        }.parseHuaweiOperationResult("华为软件包列表")

    suspend fun getPackageCompileStatus(
        auth: HuaweiAuthContext,
        appId: String,
        pkgIds: String,
    ): HuaweiOperationResult =
        client.get("$PUBLISH_BASE/package/compile/status") {
            huaweiHeaders(auth)
            url {
                parameters.append("appId", appId)
                parameters.append("pkgIds", pkgIds)
            }
        }.parseHuaweiOperationResult("华为AAB编译状态")

    suspend fun updatePhasedRelease(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
        releaseType: Int? = null,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/phased-release") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("appId", appId)
                releaseType?.let { parameters.append("releaseType", it.toString()) }
            }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新分阶段发布")

    suspend fun updateOnShelfTime(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/on-shelf-time") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为更新上架时间")

    suspend fun updateGmsProperties(
        auth: HuaweiAuthContext,
        appId: String,
        body: String,
    ): HuaweiOperationResult =
        client.put("$PUBLISH_BASE/properties/gms") {
            huaweiHeaders(auth)
            contentType(ContentType.Application.Json)
            url { parameters.append("appId", appId) }
            setBody(body)
        }.parseHuaweiOperationResult("华为设置GMS依赖")

    private fun HttpRequestBuilder.huaweiHeaders(auth: HuaweiAuthContext) {
        when (auth) {
            is HuaweiAuthContext.ApiClient -> {
                header("client_id", auth.clientId)
                header("Authorization", "Bearer ${auth.accessToken}")
            }
            is HuaweiAuthContext.OAuthClient -> {
                header("teamId", auth.teamId)
                header("oauth2Token", auth.oauth2Token)
            }
            is HuaweiAuthContext.ServiceAccount -> {
                header("Authorization", "Bearer ${auth.jwt}")
            }
        }
        header(HttpHeaders.Accept, "application/json")
    }

    private companion object {
        const val AUTH_BASE = "https://connect-api.cloud.huawei.com/api"
        const val PUBLISH_BASE = "$AUTH_BASE/publish/v2"
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.parseHuaweiOperationResult(marketName: String): HuaweiOperationResult {
    val response = decodeResponse<HuaweiResponse>(marketName)
    return HuaweiOperationResult(response.requireSuccess(marketName))
}

private fun HuaweiResponse.requireSuccess(marketName: String): HuaweiRet? {
    ret.requireSuccess(marketName)
    return ret
}

private fun HuaweiRet?.requireSuccess(marketName: String) {
    val code = this?.code ?: return
    require(code == 0) {
        "$marketName code=$code: ${message.orEmpty()}"
    }
}

private fun HuaweiTokenResponse.missingAccessTokenMessage(): String {
    val ret = ret ?: topLevelRet()
    val detail = when {
        ret?.code != null -> "code=${ret.code}: ${ret.message.orEmpty()}"
        error != null -> listOfNotNull(error, errorDescription).joinToString(": ")
        else -> null
    }
    return listOfNotNull("华为 Token 接口未返回 access_token", detail).joinToString("，")
}

private fun HuaweiTokenResponse.requireSuccess() {
    val code = code ?: return
    require(code == 0) {
        "华为Token code=$code: ${huaweiErrorMessage(code, message)}"
    }
}

private fun HuaweiTokenResponse.topLevelRet(): HuaweiRet? =
    code?.let { HuaweiRet(code = it, message = huaweiErrorMessage(it, message)) }

private fun huaweiErrorMessage(code: Int, message: String?): String =
    when {
        !message.isNullOrBlank() -> message
        code == 203890688 -> "client id or secret error"
        else -> ""
    }
