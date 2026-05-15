package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.network.httpClient
import io.github.loshine.andpub.platform.hmacSha256Hex
import io.github.loshine.andpub.platform.md5Hex
import io.github.loshine.andpub.platform.rsaPublicEncryptHex
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApiMarketPublisher(
    override val marketType: MarketType,
    private val client: HttpClient,
) : MarketPublisher {
    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            when (marketType) {
                MarketType.Huawei -> fetchHuawei(app, channel)
                MarketType.Honor -> fetchHonor(app, channel)
                MarketType.Xiaomi -> fetchXiaomi(app, channel)
                MarketType.Oppo -> fetchOppo(app, channel)
                MarketType.Vivo -> fetchVivo(app, channel)
                MarketType.Tencent -> fetchTencent(app, channel)
            }
        }

    private suspend fun fetchHuawei(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val clientId = channel.requiredCredential("clientId")
        val accessToken = channel.requiredCredential("accessToken")
        val appId = channel.marketAppId ?: run {
            val text = client.get("https://connect-api.cloud.huawei.com/api/publish/v2/appid-list") {
                header("client_id", clientId)
                header("Authorization", "Bearer $accessToken")
                url { parameters.append("packageName", app.packageName) }
            }.bodyAsText()
            val root = json.parseToJsonElement(text).jsonObject
            root["appids"]?.firstObject()?.string("value")
                ?: error("华为未返回 appId：$text")
        }

        val text = client.get("https://connect-api.cloud.huawei.com/api/publish/v2/app-info") {
            header("client_id", clientId)
            header("Authorization", "Bearer $accessToken")
            url { parameters.append("appId", appId) }
        }.bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        return MarketAppInfo(
            marketAppId = appId,
            packageName = root.deepString("packageName") ?: app.packageName,
            appName = root.deepString("appName") ?: app.name,
            onlineVersion = root.deepString("versionName"),
            auditStatus = root.deepString("auditStatus") ?: root.deepString("appStatus"),
            releaseStatus = root.deepString("releaseStatus"),
            updatedAtText = "华为 API",
        )
    }

    private suspend fun fetchHonor(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val token = fetchHonorToken(channel)
        val appId = channel.marketAppId ?: run {
            val text = client.get("https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish/get-app-id") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $token")
                url { parameters.append("pkgName", app.packageName) }
            }.bodyAsText()
            val root = json.parseToJsonElement(text).jsonObject
            require(root.int("code") == 0) {
                "荣耀 code=${root.int("code")}: ${root.string("msg") ?: text}"
            }
            root["data"]?.firstObject()?.string("appId")
                ?: error("荣耀未返回 APPID：$text")
        }

        val text = client.get("https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish/get-app-detail") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            url { parameters.append("appId", appId) }
        }.bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        require(root.int("code") == 0) {
            "荣耀 code=${root.int("code")}: ${root.string("msg") ?: text}"
        }
        val data = root["data"]?.jsonObject
        return MarketAppInfo(
            marketAppId = appId,
            packageName = data?.deepString("packageName") ?: app.packageName,
            appName = data?.deepString("appName") ?: app.name,
            onlineVersion = data?.deepString("versionName"),
            auditStatus = data?.deepString("auditStatus"),
            releaseStatus = data?.deepString("releaseStatus"),
            updatedAtText = "荣耀 API",
        )
    }

    private suspend fun fetchHonorToken(channel: ChannelRecord): String {
        val response = client.submitForm(
            url = "https://iam.developer.honor.com/auth/token",
            formParameters = parameters {
                append("grant_type", "client_credentials")
                append("client_id", channel.requiredCredential("clientId"))
                append("client_secret", channel.requiredCredential("clientSecret"))
            },
        ).bodyAsText()
        val root = json.parseToJsonElement(response).jsonObject
        return root.string("access_token") ?: error("荣耀 token 获取失败：$response")
    }

    private suspend fun fetchXiaomi(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val requestData = """{"packageName":"${app.packageName}","userName":"${channel.requiredCredential("userName")}"}"""
        val sigPayload = """{"password":"${channel.requiredCredential("password")}","sig":[{"name":"RequestData","hash":"${md5Hex(requestData)}"}]}"""
        val sig = rsaPublicEncryptHex(channel.requiredCredential("publicKey"), sigPayload)
        val text = client.submitForm(
            url = "https://api.developer.xiaomi.com/devupload/dev/query",
            formParameters = parameters {
                append("RequestData", requestData)
                append("SIG", sig)
            },
        ).bodyAsText()
        val root = parseJsonObject("小米", text)
        require(root.int("result") == 0) {
            "小米 result=${root.int("result")}: ${root.string("message") ?: text}"
        }
        val info = root["packageInfo"]?.jsonObject
        return MarketAppInfo(
            marketAppId = channel.marketAppId ?: app.packageName,
            packageName = info?.string("packageName") ?: app.packageName,
            appName = info?.string("appName") ?: app.name,
            onlineVersion = info?.string("versionName"),
            auditStatus = "updateVersion=${root.boolean("updateVersion")}, updateInfo=${root.boolean("updateInfo")}",
            releaseStatus = root.string("message"),
            updatedAtText = "小米 API",
        )
    }

    private suspend fun fetchOppo(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val tokenText = client.get("https://oop-openapi-cn.heytapmobi.com/developer/v1/token") {
            url {
                parameters.append("client_id", channel.requiredCredential("clientId"))
                parameters.append("client_secret", channel.requiredCredential("clientSecret"))
            }
        }.bodyAsText()
        val tokenRoot = json.parseToJsonElement(tokenText).jsonObject
        require(tokenRoot.int("errno") == 0) {
            "OPPO errno=${tokenRoot.int("errno")}: ${tokenRoot.string("errmsg") ?: tokenText}"
        }
        val accessToken = tokenRoot["data"]?.jsonObject?.string("access_token")
            ?: error("OPPO token 获取失败：$tokenText")

        val timestamp = currentSeconds()
        val params = mapOf(
            "access_token" to accessToken,
            "timestamp" to timestamp,
            "pkg_name" to app.packageName,
        )
        val apiSign = hmacSha256Hex(channel.requiredCredential("clientSecret"), params.signPlain())
        val text = client.get("https://oop-openapi-cn.heytapmobi.com/resource/v1/app/info") {
            url {
                params.forEach { (key, value) -> parameters.append(key, value) }
                parameters.append("api_sign", apiSign)
            }
        }.bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        require(root.int("errno") == 0) {
            "OPPO errno=${root.int("errno")}: ${root.string("errmsg") ?: text}"
        }
        val data = root["data"]?.jsonObject
        return MarketAppInfo(
            marketAppId = data?.string("app_id") ?: channel.marketAppId ?: app.packageName,
            packageName = data?.string("pkg_name") ?: app.packageName,
            appName = data?.string("app_name") ?: app.name,
            onlineVersion = data?.string("version_name"),
            auditStatus = data?.string("audit_status_name") ?: data?.string("audit_status"),
            releaseStatus = data?.string("release_status"),
            updatedAtText = "OPPO API",
        )
    }

    private suspend fun fetchVivo(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val params = linkedMapOf(
            "method" to "app.query.task.status",
            "access_key" to channel.requiredCredential("accessKey"),
            "timestamp" to currentMillis(),
            "format" to "json",
            "v" to "1.0",
            "sign_method" to "HMAC-SHA256",
            "target_app_key" to "developer",
            "packageName" to app.packageName,
            "packetType" to "0",
        )
        params["sign"] = hmacSha256Hex(channel.requiredCredential("accessSecret"), params.signPlain())
        val text = client.submitForm(
            url = "https://developer-api.vivo.com.cn/router/rest",
            formParameters = Parameters.build {
                params.forEach { (key, value) -> append(key, value) }
            },
        ).bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        val data = root["data"]?.jsonObject
        val code = root.deepString("code")
        if (code != null && code != "0" && data == null) {
            error("vivo code=$code: ${root.deepString("msg") ?: text}")
        }
        return MarketAppInfo(
            marketAppId = channel.marketAppId ?: app.packageName,
            packageName = data?.string("packageName") ?: app.packageName,
            appName = app.name,
            onlineVersion = null,
            auditStatus = data?.string("status") ?: root.deepString("code"),
            releaseStatus = data?.string("errorReason") ?: root.deepString("msg"),
            updatedAtText = "vivo API",
        )
    }

    private suspend fun fetchTencent(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val appId = channel.marketAppId ?: error("腾讯查询应用详情需要填写市场侧应用 ID")
        val params = linkedMapOf(
            "user_id" to channel.requiredCredential("userId"),
            "timestamp" to currentSeconds(),
            "pkg_name" to app.packageName,
            "app_id" to appId,
        )
        params["sign"] = hmacSha256Hex(channel.requiredCredential("accessSecret"), params.signPlain())
        val text = client.submitForm(
            url = "https://p.open.qq.com/open_file/developer_api/query_app_detail",
            formParameters = Parameters.build {
                params.forEach { (key, value) -> append(key, value) }
            },
        ).bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        require(root.int("ret") == 0) {
            "腾讯 ret=${root.int("ret")}: ${root.string("msg") ?: text}"
        }
        return MarketAppInfo(
            marketAppId = appId,
            packageName = root.string("pkg_name") ?: app.packageName,
            appName = root.string("app_name") ?: app.name,
            onlineVersion = null,
            auditStatus = root.string("feature"),
            releaseStatus = root.string("msg"),
            updatedAtText = "腾讯 API",
        )
    }

    private fun ChannelRecord.requiredCredential(key: String): String =
        credentials[key]?.takeIf { it.isNotBlank() }
            ?: error("${marketType.displayName} 缺少 $key")

    private fun Map<String, String>.signPlain(): String =
        filterValues { it.isNotEmpty() }
            .entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }

    private fun currentSeconds(): String =
        (kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000).toString()

    private fun currentMillis(): String =
        kotlin.time.Clock.System.now().toEpochMilliseconds().toString()

    companion object {
        fun createAll(client: HttpClient = httpClient()): List<MarketPublisher> =
            MarketType.entries.map { ApiMarketPublisher(it, client) }

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private fun parseJsonObject(
    marketName: String,
    text: String,
): JsonObject {
    val trimmed = text.trimStart()
    require(!trimmed.startsWith("<")) {
        "$marketName 返回了非 JSON 响应：${text.take(300)}"
    }
    return responseJson.parseToJsonElement(text).jsonObject
}

private val responseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

private fun JsonElement.firstObject(): JsonObject? =
    when (this) {
        is kotlinx.serialization.json.JsonArray -> firstOrNull()?.jsonObject
        is JsonObject -> this
        else -> null
    }

private fun JsonObject.deepString(key: String): String? {
    string(key)?.let { return it }
    values.forEach { value ->
        when (value) {
            is JsonObject -> value.deepString(key)?.let { return it }
            is kotlinx.serialization.json.JsonArray -> value.forEach { item ->
                (item as? JsonObject)?.deepString(key)?.let { return it }
            }
            else -> Unit
        }
    }
    return null
}
