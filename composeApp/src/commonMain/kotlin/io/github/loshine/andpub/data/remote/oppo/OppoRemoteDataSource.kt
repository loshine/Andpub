package io.github.loshine.andpub.data.remote.oppo

import io.github.loshine.andpub.data.remote.currentSeconds
import io.github.loshine.andpub.data.remote.decodeResponse
import io.github.loshine.andpub.data.remote.signPlain
import io.github.loshine.andpub.platform.hmacSha256Hex
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.parameters
import org.koin.core.annotation.Single

@Single
class OppoRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun obtainToken(
        clientId: String,
        clientSecret: String,
    ): OppoToken {
        val response = client.get("$BASE/developer/v1/token") {
            url {
                parameters.append("client_id", clientId)
                parameters.append("client_secret", clientSecret)
            }
        }.decodeResponse<OppoResponse<OppoTokenData>>("OPPO Token")
        response.requireSuccess("OPPO Token")
        val data = response.data ?: error("OPPO token 获取失败")
        return OppoToken(
            accessToken = data.accessToken ?: error("OPPO token 获取失败"),
            expireIn = data.expireIn,
        )
    }

    suspend fun getUploadUrl(
        accessToken: String,
        clientSecret: String,
        type: String,
    ): OppoUploadConfig {
        val response = signedGet<OppoUploadConfigData>("OPPO获取上传配置", "$BASE/resource/v1/upload/get-upload-url", clientSecret) {
            put("access_token", accessToken)
            put("timestamp", currentSeconds())
            put("type", type)
        }
        val data = response.data
        return OppoUploadConfig(
            uploadUrl = data?.uploadUrl,
            sign = data?.sign,
        )
    }

    suspend fun uploadFile(
        uploadUrl: String,
        formParameters: Parameters,
    ): OppoUploadResult {
        val text = client.submitForm(
            url = uploadUrl,
            formParameters = formParameters,
        ).bodyAsText()
        val response = decodeResponse<OppoResponse<OppoUploadResultData>>("OPPO上传文件", text)
        response.requireSuccess("OPPO上传文件")
        return response.data?.toOppoUploadResult() ?: error("OPPO上传文件未返回 data")
    }

    suspend fun publishApp(
        accessToken: String,
        clientSecret: String,
        params: Map<String, String>,
    ): OppoTaskResult =
        signedForm("OPPO发布版本", "$BASE/resource/v1/app/upd", accessToken, clientSecret, params).toOppoTaskResult()

    suspend fun updateAppMaterial(
        accessToken: String,
        clientSecret: String,
        params: Map<String, String>,
    ): OppoTaskResult =
        signedForm("OPPO更新资料", "$BASE/resource/v1/app/updm", accessToken, clientSecret, params).toOppoTaskResult()

    suspend fun updateMultiPackageApp(
        accessToken: String,
        clientSecret: String,
        params: Map<String, String>,
    ): OppoTaskResult =
        signedForm("OPPO分包更新", "$BASE/resource/v1/app/multi-updm", accessToken, clientSecret, params).toOppoTaskResult()

    suspend fun getAppInfo(
        accessToken: String,
        clientSecret: String,
        packageName: String,
    ): OppoAppInfoResult {
        val response = signedGet<OppoAppInfoData>("OPPO应用详情", "$BASE/resource/v1/app/info", clientSecret) {
            put("access_token", accessToken)
            put("timestamp", currentSeconds())
            put("pkg_name", packageName)
        }
        return OppoAppInfoResult(response.data?.toOppoAppInfo())
    }

    suspend fun getMultiAppInfo(
        accessToken: String,
        clientSecret: String,
        packageNames: String,
    ): OppoAppInfoResult =
        signedGet<OppoAppInfoData>("OPPO批量应用详情", "$BASE/resource/v1/app/multi-info", clientSecret) {
            put("access_token", accessToken)
            put("timestamp", currentSeconds())
            put("pkg_names", packageNames)
        }.let { OppoAppInfoResult(it.data?.toOppoAppInfo()) }

    suspend fun getTaskState(
        accessToken: String,
        clientSecret: String,
        taskId: String,
    ): OppoTaskResult =
        signedForm("OPPO任务状态", "$BASE/resource/v1/app/task-state", accessToken, clientSecret, mapOf("task_id" to taskId))
            .toOppoTaskResult()

    private suspend fun signedForm(
        marketName: String,
        url: String,
        accessToken: String,
        clientSecret: String,
        params: Map<String, String>,
    ): OppoResponse<OppoTaskData> {
        val signedParams = linkedMapOf(
            "access_token" to accessToken,
            "timestamp" to currentSeconds(),
        )
        signedParams.putAll(params)
        signedParams["api_sign"] = hmacSha256Hex(clientSecret, signedParams.signPlain())
        val text = client.submitForm(
            url = url,
            formParameters = parameters {
                signedParams.forEach { (key, value) -> append(key, value) }
            },
        ).bodyAsText()
        val response = decodeResponse<OppoResponse<OppoTaskData>>(marketName, text)
        response.requireSuccess(marketName)
        return response
    }

    private suspend inline fun <reified T> signedGet(
        marketName: String,
        url: String,
        clientSecret: String,
        buildParams: MutableMap<String, String>.() -> Unit,
    ): OppoResponse<T> {
        val params = linkedMapOf<String, String>().apply(buildParams)
        params["api_sign"] = hmacSha256Hex(clientSecret, params.signPlain())
        val response = client.get(url) {
            url {
                params.forEach { (key, value) -> parameters.append(key, value) }
            }
        }.decodeResponse<OppoResponse<T>>(marketName)
        response.requireSuccess(marketName)
        return response
    }

    private companion object {
        const val BASE = "https://oop-openapi-cn.heytapmobi.com"
    }
}

private fun OppoUploadResultData.toOppoUploadResult(): OppoUploadResult =
    OppoUploadResult(
        url = url,
        uriPath = uriPath,
        md5 = md5,
        fileExtension = fileExtension,
        fileSize = fileSize,
        id = id,
        width = width,
        height = height,
    )

private fun OppoResponse<*>.requireSuccess(marketName: String) {
    val errno = errno ?: return
    require(errno == 0) {
        "$marketName errno=$errno: ${errmsg.orEmpty()}"
    }
}

private fun OppoResponse<OppoTaskData>.toOppoTaskResult(): OppoTaskResult =
    OppoTaskResult(
        success = data?.success,
        message = data?.message ?: errmsg,
        taskId = data?.taskId,
    )

private fun OppoAppInfoData.toOppoAppInfo(): OppoAppInfo =
    OppoAppInfo(
        appId = appId,
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        auditStatus = auditStatus,
        auditStatusName = auditStatusName,
        releaseStatus = releaseStatus,
    )
