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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
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
        val accessToken = fetchHuaweiAccessToken(channel)
        val configuredAppId = channel.marketAppId
        val appId = configuredAppId ?: fetchHuaweiAppId(clientId, accessToken, app.packageName)
        val appInfoRoot = runCatching {
            fetchHuaweiAppInfo(clientId, accessToken, appId)
        }.recoverCatching { error ->
            val resolvedAppId = configuredAppId?.let { fetchHuaweiAppId(clientId, accessToken, app.packageName) }
            if (resolvedAppId != null && resolvedAppId != configuredAppId) {
                fetchHuaweiAppInfo(clientId, accessToken, resolvedAppId)
            } else {
                throw error
            }
        }.getOrThrow()
        requireHuaweiSuccess(appInfoRoot)
        val appInfo = appInfoRoot.objectValue("appInfo")
        val auditInfo = appInfoRoot.objectValue("auditInfo")
        val phasedReleaseInfo = appInfoRoot.objectValue("phasedReleaseInfo")
        val releaseState = appInfo?.deepInt("releaseState")
        return MarketAppInfo(
            marketAppId = appId,
            packageName = app.packageName,
            appName = appInfoRoot.deepString("appName") ?: appInfoRoot["languages"]?.firstObject()?.string("appName") ?: app.name,
            onlineVersion = appInfo?.firstDeepString("onShelfVersionNumber", "versionNumber")
                ?: "华为接口未返回线上版本",
            auditStatus = huaweiAuditStatusText(releaseState, auditInfo)
                ?: "华为接口未返回审核状态",
            releaseStatus = huaweiReleaseStatusText(releaseState, phasedReleaseInfo?.string("state")),
            updatedAtText = "华为 API",
        )
    }

    private suspend fun fetchHuaweiAppId(
        clientId: String,
        accessToken: String,
        packageName: String,
    ): String {
        val root = client.get("https://connect-api.cloud.huawei.com/api/publish/v2/appid-list") {
            header("client_id", clientId)
            header("Authorization", "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            url { parameters.append("packageName", packageName) }
        }.parseJsonObject("华为应用ID列表")
        requireHuaweiSuccess(root)
        return root["appids"]?.firstObject()?.firstDeepString("value", "appId", "id")
            ?: error("华为未返回 appId")
    }

    private suspend fun fetchHuaweiAppInfo(
        clientId: String,
        accessToken: String,
        appId: String,
    ): JsonObject =
        client.get("https://connect-api.cloud.huawei.com/api/publish/v2/app-info") {
            header("client_id", clientId)
            header("Authorization", "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            url { parameters.append("appId", appId) }
        }.parseJsonObject("华为应用信息")

    private suspend fun fetchHuaweiAccessToken(channel: ChannelRecord): String {
        val body = JsonObject(
            mapOf(
                "grant_type" to JsonPrimitive("client_credentials"),
                "client_id" to JsonPrimitive(channel.requiredCredential("clientId")),
                "client_secret" to JsonPrimitive(channel.requiredCredential("clientSecret")),
            )
        ).toString()
        val text = client.post("https://connect-api.cloud.huawei.com/api/oauth2/v1/token") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.parseJsonObject("华为Token")
        requireHuaweiSuccess(text)
        return text.string("access_token") ?: text.string("accessToken")
            ?: error("华为 Token 接口未返回 access_token")
    }

    private fun requireHuaweiSuccess(root: JsonObject) {
        val ret = root.objectValue("ret") ?: root.string("ret")?.let { responseJson.parseToJsonElement(it).jsonObject } ?: return
        val code = ret.int("code") ?: return
        require(code == 0) {
            "华为 code=$code: ${ret.string("msg").orEmpty()}"
        }
    }

    private fun huaweiAuditStatusText(
        releaseState: Int?,
        auditInfo: JsonObject?,
    ): String? {
        val mainStatus = releaseState?.let { huaweiReleaseStateText(it) }
        val auditOpinion = auditInfo?.string("auditOpinion")?.takeIf { it.isNotBlank() }
        val auditResults = listOfNotNull(
            auditInfo?.int("copyRightAuditResult")?.let { "版权审核${huaweiPassStatusText(it)}" },
            auditInfo?.int("copyRightCodeAuditResult")?.let { "版号审核${huaweiPassStatusText(it)}" },
            auditInfo?.int("recordAuditResult")?.let { "备案审核${huaweiPassStatusText(it)}" },
        )
        return listOfNotNull(mainStatus, auditOpinion)
            .plus(auditResults)
            .joinToString("，")
            .takeIf { it.isNotBlank() }
    }

    private fun huaweiReleaseStateText(releaseState: Int): String =
        when (releaseState) {
            0 -> "审核通过"
            1 -> "上架审核不通过"
            2 -> "已下架，无审核中版本"
            3 -> "待上架，预约上架"
            4 -> "审核中"
            5 -> "升级审核中"
            6 -> "下架审核中"
            7 -> "草稿，未提交审核"
            8 -> "升级审核不通过"
            9 -> "下架审核不通过"
            10 -> "开发者已下架，无审核中版本"
            11 -> "已撤销上架"
            12 -> "预审中"
            13 -> "预审不通过"
            else -> "未知审核状态($releaseState)"
        }

    private fun huaweiPassStatusText(status: Int): String =
        when (status) {
            0 -> "通过"
            1 -> "不通过"
            else -> "未知($status)"
        }

    private fun huaweiReleaseStatusText(
        releaseState: Int?,
        phasedReleaseState: String?,
    ): String {
        val releaseText = when (releaseState) {
            0 -> "已上架"
            1 -> "上架审核不通过"
            2 -> "已下架（含强制下架）"
            3 -> "待上架，预约上架"
            4 -> "审核中"
            5 -> "升级审核中"
            6 -> "申请下架"
            7 -> "草稿"
            8 -> "升级审核不通过"
            9 -> "下架审核不通过"
            10 -> "应用被开发者下架"
            11 -> "撤销上架"
            12 -> "预审中"
            13 -> "预审不通过"
            null -> "华为接口未返回上架状态"
            else -> "未知上架状态($releaseState)"
        }
        val phasedText = phasedReleaseState?.let { huaweiPhasedReleaseStatusText(it) }
        return listOfNotNull(releaseText, phasedText).joinToString("，")
    }

    private fun huaweiPhasedReleaseStatusText(state: String): String =
        when (state) {
            "SUSPEND" -> "分阶段发布暂停"
            "RELEASE" -> "分阶段发布中"
            "CANCEL" -> "分阶段发布取消"
            "DRAFT" -> "分阶段发布草稿"
            else -> "未知分阶段发布状态($state)"
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
        val currentRelease = fetchHonorCurrentRelease(token, appId)
        val releaseInfo = data?.firstDeepObjectContaining("versionName", "versionCode")
        return MarketAppInfo(
            marketAppId = appId,
            packageName = data?.deepString("packageName") ?: app.packageName,
            appName = data?.deepString("appName") ?: app.name,
            onlineVersion = currentRelease?.firstDeepString("versionName", "version")
                ?: releaseInfo?.deepString("versionName"),
            auditStatus = currentRelease?.deepInt("auditResult")?.let { honorAuditStatusText(it) }
                ?: currentRelease?.firstDeepString("auditStatus", "reviewStatus", "status")
                ?: data?.deepString("auditStatus"),
            releaseStatus = currentRelease?.firstDeepString("releaseStatus", "publishStatus", "releaseState")
                ?: releaseInfo?.deepString("versionName")?.let { "已上架" },
            updatedAtText = "荣耀 API",
        )
    }

    private suspend fun fetchHonorCurrentRelease(
        token: String,
        appId: String,
    ): JsonObject? {
        val text = client.get("https://appmarket-openapi-drcn.cloud.honor.com/openapi/v1/publish/get-app-current-release") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            url { parameters.append("appId", appId) }
        }.bodyAsText()
        val root = parseJsonObject("荣耀最新版本状态", text)
        require(root.int("code") == 0) {
            "荣耀 code=${root.int("code")}: ${root.string("msg") ?: text}"
        }
        return root["data"]?.firstObject()
    }

    private fun honorAuditStatusText(auditResult: Int): String =
        when (auditResult) {
            0 -> "审核中"
            1 -> "审核通过"
            2 -> "审核不通过"
            3 -> "其他非审核状态"
            4 -> "编辑中，未提交审核"
            else -> "未知审核状态($auditResult)"
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
        val create = root.boolean("create")
        val updateVersion = root.boolean("updateVersion")
        val updateInfo = root.boolean("updateInfo")
        return MarketAppInfo(
            marketAppId = channel.marketAppId ?: app.packageName,
            packageName = info?.string("packageName") ?: app.packageName,
            appName = info?.string("appName") ?: app.name,
            onlineVersion = info?.string("versionName"),
            auditStatus = xiaomiUpdateStatusText(updateVersion, updateInfo),
            releaseStatus = xiaomiReleaseStatusText(info, create),
            updatedAtText = "小米 API",
        )
    }

    private fun xiaomiUpdateStatusText(
        updateVersion: Boolean?,
        updateInfo: Boolean?,
    ): String =
        listOfNotNull(
            updateVersion?.let { if (it) "允许更新版本" else "不允许更新版本" },
            updateInfo?.let { if (it) "允许更新资料" else "不允许更新资料" },
        ).joinToString("，").ifBlank { "未返回更新状态" }

    private fun xiaomiReleaseStatusText(
        packageInfo: JsonObject?,
        create: Boolean?,
    ): String =
        when {
            packageInfo != null -> "已存在"
            create == true -> "未创建，可新增"
            create == false -> "未创建，不可新增"
            else -> "未返回上架状态"
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
        val auditStatus = data?.string("audit_status")
        val releaseStatus = data?.string("release_status")?.takeIf { it.isNotBlank() }
        return MarketAppInfo(
            marketAppId = data?.string("app_id") ?: channel.marketAppId ?: app.packageName,
            packageName = data?.string("pkg_name") ?: app.packageName,
            appName = data?.string("app_name") ?: app.name,
            onlineVersion = data?.string("version_name"),
            auditStatus = data?.string("audit_status_name") ?: auditStatus?.let { oppoStatusText(it) },
            releaseStatus = releaseStatus?.let { oppoStatusText(it) }
                ?: auditStatus?.let { oppoStatusText(it) },
            updatedAtText = "OPPO API",
        )
    }

    private fun oppoStatusText(status: String): String =
        when (status) {
            "0" -> "未发布"
            "1" -> "审核中"
            "2" -> "审核通过"
            "3" -> "测试不通过"
            "4" -> "运营审核中"
            "5" -> "运营打回"
            "6" -> "运营通过"
            "7" -> "定时发布"
            "00" -> "资质审核中"
            "11" -> "资质审核通过"
            "-11" -> "资质审核不通过"
            "-22" -> "报备提交成功"
            "22" -> "已冻结"
            "111" -> "上线"
            "222" -> "下线"
            "444" -> "审核不通过"
            else -> status
        }

    private suspend fun fetchVivo(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val data = fetchVivoAppDetail(app.packageName, channel)
        val status = data.int("status")
        val saleStatus = data.int("saleStatus") ?: data.int("onlineStatus")
        val onlineType = data.int("onlineType")
        return MarketAppInfo(
            marketAppId = channel.marketAppId ?: app.packageName,
            packageName = data.string("packageName") ?: app.packageName,
            appName = data.string("appName") ?: app.name,
            onlineVersion = data.string("versionName"),
            auditStatus = vivoAuditStatusText(status, saleStatus) ?: data.string("status"),
            releaseStatus = vivoReleaseStatusText(saleStatus, onlineType)
                ?: data.string("saleStatus")
                ?: data.string("onlineStatus"),
            updatedAtText = "vivo API",
        )
    }

    private suspend fun fetchVivoAppDetail(
        packageName: String,
        channel: ChannelRecord,
    ): JsonObject {
        val params = linkedMapOf(
            "method" to "app.query.details",
            "access_key" to channel.requiredCredential("accessKey"),
            "timestamp" to currentMillis(),
            "format" to "json",
            "v" to "1.0",
            "sign_method" to "HMAC-SHA256",
            "target_app_key" to "developer",
            "packageName" to packageName,
        )
        params["sign"] = hmacSha256Hex(channel.requiredCredential("accessSecret"), params.signPlain())
        val text = client.submitForm(
            url = "https://developer-api.vivo.com.cn/router/rest",
            formParameters = Parameters.build {
                params.forEach { (key, value) -> append(key, value) }
            },
        ).bodyAsText()
        val root = parseJsonObject("vivo应用详情", text)
        if (root.string("code") == "10018") {
            error(
                "vivo 当前接入信息没有 app.query.details 访问权限。10018 表示禁止访问；" +
                    "如果后台 access_key/access_secret 正确，请在 vivo API 管理确认是否开通应用详情查询能力。"
            )
        }
        require(root.isVivoSuccess()) {
            "vivo code=${root.string("code")}: ${root.string("subMsg") ?: root.string("msg") ?: text}"
        }
        return root["data"]?.jsonObject ?: error("vivo 未返回应用详情：$text")
    }

    private fun vivoAuditStatusText(
        status: Int?,
        saleStatus: Int?,
    ): String? =
        when {
            saleStatus == 1 -> "审核通过"
            saleStatus == 0 -> "审核通过，待上架"
            status != null -> vivoAppStatusText(status)
            else -> null
        }

    private fun vivoAppStatusText(status: Int): String =
        when (status) {
            0 -> "草稿"
            1 -> "审核中"
            2 -> "审核通过"
            3 -> "审核失败"
            4 -> "测试中"
            else -> "未知审核状态($status)"
        }

    private fun vivoReleaseStatusText(
        saleStatus: Int?,
        onlineType: Int?,
    ): String? {
        val saleText = when (saleStatus) {
            0 -> "待上架"
            1 -> "已上架"
            2 -> "已下架"
            null -> return null
            else -> "未知上架状态($saleStatus)"
        }
        val typeText = onlineType?.let { vivoOnlineTypeText(it) }
        return listOfNotNull(saleText, typeText).joinToString("，")
    }

    private fun vivoOnlineTypeText(onlineType: Int): String =
        when (onlineType) {
            1 -> "实时上架"
            2 -> "定时上架"
            else -> "未知上架类型($onlineType)"
        }

    private suspend fun fetchTencent(app: AppRecord, channel: ChannelRecord): MarketAppInfo {
        val appId = channel.marketAppId ?: error("腾讯查询应用详情需要填写市场侧应用 ID")
        val root = fetchTencentAppDetail(app.packageName, appId, channel)
        val updateStatus = fetchTencentUpdateStatus(app.packageName, appId, channel)
        val auditStatus = updateStatus?.int("audit_status")
        val onlineVersion = root.firstDeepString("version_name", "versionName", "apk_version", "apkVersion", "version")
            ?: fetchTencentStoreVersion(app.packageName)
        return MarketAppInfo(
            marketAppId = appId,
            packageName = root.string("pkg_name") ?: app.packageName,
            appName = root.string("app_name") ?: app.name,
            onlineVersion = onlineVersion ?: "腾讯接口和应用宝网页均未返回线上版本",
            auditStatus = auditStatus?.let { tencentAuditStatusText(it) },
            releaseStatus = tencentReleaseStatusText(auditStatus),
            updatedAtText = "腾讯 API",
        )
    }

    private suspend fun fetchTencentAppDetail(
        packageName: String,
        appId: String,
        channel: ChannelRecord,
    ): JsonObject {
        val params = linkedMapOf(
            "user_id" to channel.requiredCredential("userId"),
            "timestamp" to currentSeconds(),
            "pkg_name" to packageName,
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
        return root
    }

    private suspend fun fetchTencentStoreVersion(packageName: String): String? =
        runCatching {
            val html = client.get("https://sj.qq.com/appdetail/$packageName").bodyAsText()
            parseTencentStoreVersion(html)
        }.getOrNull()

    private suspend fun fetchTencentUpdateStatus(
        packageName: String,
        appId: String,
        channel: ChannelRecord,
    ): JsonObject? {
        val params = linkedMapOf(
            "user_id" to channel.requiredCredential("userId"),
            "timestamp" to currentSeconds(),
            "pkg_name" to packageName,
            "app_id" to appId,
        )
        params["sign"] = hmacSha256Hex(channel.requiredCredential("accessSecret"), params.signPlain())
        val text = client.submitForm(
            url = "https://p.open.qq.com/open_file/developer_api/query_app_update_status",
            formParameters = Parameters.build {
                params.forEach { (key, value) -> append(key, value) }
            },
        ).bodyAsText()
        val root = parseJsonObject("腾讯审核状态", text)
        if (root.int("ret") == 5000002) return null
        require(root.int("ret") == 0) {
            "腾讯 ret=${root.int("ret")}: ${root.string("msg") ?: text}"
        }
        return root
    }

    private fun tencentAuditStatusText(status: Int): String =
        when (status) {
            1 -> "审核中"
            2 -> "审核驳回"
            3 -> "审核通过"
            8 -> "开发者主动撤销"
            else -> "未知审核状态($status)"
        }

    private fun tencentReleaseStatusText(auditStatus: Int?): String =
        when (auditStatus) {
            1 -> "已上架，更新审核中"
            2 -> "已上架，更新审核驳回"
            3 -> "已上架，更新审核通过"
            8 -> "已上架，更新已撤销"
            else -> "已上架"
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
    require(trimmed.isNotBlank()) {
        "$marketName 返回了空响应"
    }
    require(!trimmed.startsWith("<")) {
        "$marketName 返回了非 JSON 响应：${text.take(300)}"
    }
    return responseJson.parseToJsonElement(text).jsonObject
}

private suspend fun HttpResponse.parseJsonObject(marketName: String): JsonObject {
    val text = bodyAsText()
    val trimmed = text.trimStart()
    require(trimmed.isNotBlank()) {
        "$marketName 返回了空响应，HTTP ${status.value} ${status.description}"
    }
    require(!trimmed.startsWith("<")) {
        "$marketName 返回了非 JSON 响应，HTTP ${status.value} ${status.description}：${text.take(300)}"
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

private fun JsonObject.objectValue(key: String): JsonObject? =
    this[key] as? JsonObject

private fun JsonObject.isVivoSuccess(): Boolean {
    val code = string("code") ?: return false
    val subCode = string("subCode")
    return code == "0" && (subCode == null || subCode == "0")
}

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

private fun JsonObject.deepInt(key: String): Int? {
    this[key]?.jsonPrimitive?.intOrNull?.let { return it }
    values.forEach { value ->
        when (value) {
            is JsonObject -> value.deepInt(key)?.let { return it }
            is kotlinx.serialization.json.JsonArray -> value.forEach { item ->
                (item as? JsonObject)?.deepInt(key)?.let { return it }
            }
            else -> Unit
        }
    }
    return null
}

private fun JsonObject.firstDeepString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { deepString(it) }

private fun JsonObject.firstDeepObjectContaining(vararg keys: String): JsonObject? {
    if (keys.any { containsKey(it) }) return this
    values.forEach { value ->
        when (value) {
            is JsonObject -> value.firstDeepObjectContaining(*keys)?.let { return it }
            is kotlinx.serialization.json.JsonArray -> value.forEach { item ->
                (item as? JsonObject)?.firstDeepObjectContaining(*keys)?.let { return it }
            }
            else -> Unit
        }
    }
    return null
}

private fun parseTencentStoreVersion(html: String): String? {
    val text = html
        .replace(Regex("(?is)<script.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?</style>"), " ")
        .replace(Regex("<[^>]+>"), "\n")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
    val versionPattern = Regex("""[0-9]+(?:[._-][0-9A-Za-z]+)+""")
    Regex("""版本号\s*[:：]?\s*(${versionPattern.pattern})""")
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.let { return it }

    val lines = text.lines()
    lines.forEachIndexed { index, line ->
        if (line == "版本号") {
            lines.drop(index + 1)
                .firstOrNull { versionPattern.matches(it) }
                ?.let { return it }
        }
    }
    return Regex("""\bV\s*(${versionPattern.pattern})\b""")
        .find(text)
        ?.groupValues
        ?.get(1)
}
