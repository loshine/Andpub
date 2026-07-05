package io.github.loshine.andpub.data.remote.xiaomi

import io.github.loshine.andpub.data.remote.decodeResponse
import io.github.loshine.andpub.data.remote.signPlain
import io.github.loshine.andpub.platform.md5Hex
import io.github.loshine.andpub.platform.rsaPublicEncryptHex
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.parameters
import org.koin.core.annotation.Single

@Single
class XiaomiRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun queryApp(
        userName: String,
        password: String,
        publicKey: String,
        packageName: String,
    ): XiaomiQueryAppResult {
        val requestData = """{"packageName":"$packageName","userName":"$userName"}"""
        val response = signedPost("小米查询应用", "$BASE/dev/query", requestData, password, publicKey)
        return XiaomiQueryAppResult(
            packageInfo = response.packageInfo,
            create = response.create,
            updateVersion = response.updateVersion,
            updateInfo = response.updateInfo,
            message = response.message,
        )
    }

    suspend fun queryCategory(
        userName: String,
        password: String,
        publicKey: String,
    ): XiaomiOperationResult {
        val requestData = """{"userName":"$userName"}"""
        val response = signedPost("小米查询分类", "$BASE/dev/category", requestData, password, publicKey)
        return XiaomiOperationResult(message = response.message)
    }

    suspend fun pushApp(
        requestData: String,
        password: String,
        publicKey: String,
        extraFormFields: Parameters = Parameters.Empty,
    ): XiaomiOperationResult =
        XiaomiOperationResult(
            message = signedPost("小米推送应用", "$BASE/dev/push", requestData, password, publicKey, extraFormFields).message,
        )

    suspend fun pushChannelApk(
        requestData: String,
        password: String,
        publicKey: String,
        extraFormFields: Parameters = Parameters.Empty,
    ): XiaomiOperationResult =
        XiaomiOperationResult(
            message = signedPost("小米推送渠道包", "$BASE/dev/pushChannelApk", requestData, password, publicKey, extraFormFields).message,
        )

    suspend fun pushAppWithFile(
        requestData: String,
        password: String,
        publicKey: String,
        apkFileName: String,
        apkBytes: ByteArray,
        apkMd5: String,
    ): XiaomiOperationResult {
        val requestDataMd5 = md5Hex(requestData)
        val sigPayload = """{"password":"$password","sig":[{"name":"RequestData","hash":"$requestDataMd5"},{"name":"apk","hash":"$apkMd5"}]}"""
        val sig = rsaPublicEncryptHex(publicKey, sigPayload)
        val text = client.post("$BASE/dev/push") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("RequestData", requestData)
                        append("SIG", sig)
                        append(
                            "apk",
                            apkBytes,
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$apkFileName\"")
                                append(HttpHeaders.ContentType, "application/vnd.android.package-archive")
                            },
                        )
                    },
                )
            )
        }.bodyAsText()
        val response = decodeResponse<XiaomiResponse>("小米推送APK", text)
        response.requireSuccess("小米推送APK")
        return XiaomiOperationResult(message = response.message)
    }

    private suspend fun signedPost(
        marketName: String,
        url: String,
        requestData: String,
        password: String,
        publicKey: String,
        extraFormFields: Parameters = Parameters.Empty,
    ): XiaomiResponse {
        val sigPayload = """{"password":"$password","sig":[{"name":"RequestData","hash":"${md5Hex(requestData)}"}]}"""
        val sig = rsaPublicEncryptHex(publicKey, sigPayload)
        val text = client.submitForm(
            url = url,
            formParameters = parameters {
                append("RequestData", requestData)
                append("SIG", sig)
                extraFormFields.names().forEach { key ->
                    extraFormFields.getAll(key).orEmpty().forEach { value -> append(key, value) }
                }
            },
        ).bodyAsText()
        val response = decodeResponse<XiaomiResponse>(marketName, text)
        response.requireSuccess(marketName)
        return response
    }

    private companion object {
        const val BASE = "https://api.developer.xiaomi.com/devupload"
    }
}

private fun XiaomiResponse.requireSuccess(marketName: String) {
    val result = result ?: return
    require(result == 0) {
        "$marketName result=$result: ${message.orEmpty()}"
    }
}
