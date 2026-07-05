package io.github.loshine.andpub.data.remote.tencent

import io.github.loshine.andpub.data.remote.currentSeconds
import io.github.loshine.andpub.data.remote.decodeResponse
import io.github.loshine.andpub.data.remote.signPlain
import io.github.loshine.andpub.platform.hmacSha256Hex
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import org.koin.core.annotation.Single

@Single
class TencentRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun queryAppDetail(
        userId: String,
        accessSecret: String,
        packageName: String,
        appId: String,
    ): TencentAppDetail {
        val response = signedPost(
            marketName = "腾讯应用详情",
            path = "query_app_detail",
            accessSecret = accessSecret,
            params = baseParams(userId, packageName, appId),
        )
        return TencentAppDetail(
            packageName = response.packageName,
            appName = response.appName,
            versionName = response.versionName,
        )
    }

    suspend fun getFileUploadInfo(
        userId: String,
        accessSecret: String,
        packageName: String,
        appId: String,
        fileType: String,
        fileName: String? = null,
    ): TencentFileUploadInfo {
        val params = baseParams(userId, packageName, appId).apply {
            put("file_type", fileType)
            fileName?.let { put("file_name", it) }
        }
        val response = signedPost(
            marketName = "腾讯获取上传信息",
            path = "get_file_upload_info",
            accessSecret = accessSecret,
            params = params,
        )
        return TencentFileUploadInfo(
            preSignUrl = response.preSignUrl,
            serialNumber = response.serialNumber,
        )
    }

    suspend fun updateApp(
        userId: String,
        accessSecret: String,
        packageName: String,
        appId: String,
        params: Map<String, String>,
    ): TencentOperationResult =
        TencentOperationResult(
            message = signedPost(
                marketName = "腾讯更新应用",
                path = "update_app",
                accessSecret = accessSecret,
                params = baseParams(userId, packageName, appId).apply { putAll(params) },
            ).message,
        )

    suspend fun queryAppUpdateStatus(
        userId: String,
        accessSecret: String,
        packageName: String,
        appId: String,
    ): TencentUpdateStatus? {
        val response = signedPost(
            marketName = "腾讯审核状态",
            path = "query_app_update_status",
            accessSecret = accessSecret,
            params = baseParams(userId, packageName, appId),
            allowNotFound = true,
        )
        if (response.ret == 5000002) return null
        return TencentUpdateStatus(
            auditStatus = response.auditStatus,
            message = response.message,
        )
    }

    suspend fun uploadToPresignedUrl(
        presignedUrl: String,
        fileBytes: ByteArray,
    ) {
        val response = client.put(presignedUrl) {
            setBody(fileBytes)
        }
        val status = response.status.value
        require(status in 200..299) {
            "腾讯 COS 上传失败，HTTP $status：${response.bodyAsText().take(200)}"
        }
    }

    suspend fun getStorePage(packageName: String): String =
        client.get("https://sj.qq.com/appdetail/$packageName").bodyAsText()

    private suspend fun signedPost(
        marketName: String,
        path: String,
        accessSecret: String,
        params: LinkedHashMap<String, String>,
        allowNotFound: Boolean = false,
    ): TencentResponse {
        params["sign"] = hmacSha256Hex(accessSecret, params.signPlain())
        val text = client.submitForm(
            url = "$BASE/$path",
            formParameters = Parameters.build {
                params.forEach { (key, value) -> append(key, value) }
            },
        ).bodyAsText()
        val response = decodeResponse<TencentResponse>(marketName, text)
        response.requireSuccess(marketName, allowNotFound)
        return response
    }

    private fun baseParams(
        userId: String,
        packageName: String,
        appId: String,
    ): LinkedHashMap<String, String> =
        linkedMapOf(
            "user_id" to userId,
            "timestamp" to currentSeconds(),
            "pkg_name" to packageName,
            "app_id" to appId,
        )

    private companion object {
        const val BASE = "https://p.open.qq.com/open_file/developer_api"
    }
}

private fun TencentResponse.requireSuccess(
    marketName: String,
    allowNotFound: Boolean,
) {
    val value = ret ?: return
    if (allowNotFound && value == 5000002) return
    require(value == 0) {
        "$marketName ret=$value: ${message.orEmpty()}"
    }
}
