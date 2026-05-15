package io.github.loshine.andpub.data.remote.oppo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OppoToken(
    val accessToken: String,
    val expireIn: Long?,
)

@Serializable
data class OppoUploadConfig(
    val uploadUrl: String?,
    val sign: String?,
)

@Serializable
data class OppoUploadResult(
    val url: String? = null,
    val uriPath: String? = null,
    val md5: String? = null,
    val fileExtension: String? = null,
    val fileSize: Long? = null,
    val id: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

data class OppoTaskResult(
    val success: Boolean?,
    val message: String?,
    val taskId: String?,
)

data class OppoAppInfoResult(
    val appInfo: OppoAppInfo?,
)

@Serializable
data class OppoAppInfo(
    val appId: String? = null,
    val packageName: String? = null,
    val appName: String? = null,
    val versionName: String? = null,
    val auditStatus: String? = null,
    val auditStatusName: String? = null,
    val releaseStatus: String? = null,
)

@Serializable
internal data class OppoResponse<T>(
    val errno: Int? = null,
    val errmsg: String? = null,
    val data: T? = null,
)

@Serializable
internal data class OppoTokenData(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("expire_in")
    val expireIn: Long? = null,
)

@Serializable
internal data class OppoUploadConfigData(
    @SerialName("upload_url")
    val uploadUrl: String? = null,
    val sign: String? = null,
)

@Serializable
internal data class OppoUploadResultData(
    val url: String? = null,
    @SerialName("uri_path")
    val uriPath: String? = null,
    val md5: String? = null,
    @SerialName("file_extension")
    val fileExtension: String? = null,
    @SerialName("file_size")
    val fileSize: Long? = null,
    val id: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class OppoTaskData(
    val success: Boolean? = null,
    val message: String? = null,
    @SerialName("task_id")
    val taskId: String? = null,
)

@Serializable
internal data class OppoAppInfoData(
    @SerialName("app_id")
    val appId: String? = null,
    @SerialName("pkg_name")
    val packageName: String? = null,
    @SerialName("app_name")
    val appName: String? = null,
    @SerialName("version_name")
    val versionName: String? = null,
    @SerialName("audit_status")
    val auditStatus: String? = null,
    @SerialName("audit_status_name")
    val auditStatusName: String? = null,
    @SerialName("release_status")
    val releaseStatus: String? = null,
)
