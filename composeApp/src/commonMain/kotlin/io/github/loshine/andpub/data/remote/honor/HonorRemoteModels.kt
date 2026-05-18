package io.github.loshine.andpub.data.remote.honor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HonorToken(
    val accessToken: String,
    val expiresIn: Int?,
    val tokenType: String?,
)

@Serializable
data class HonorOperationResult(
    val code: Int?,
    val message: String?,
)

@Serializable
data class HonorAppIdEntry(
    @SerialName("id")
    val appId: String,
    val packageName: String? = null,
)

data class HonorAppIdList(
    val apps: List<HonorAppIdEntry>,
)

data class HonorAppDetail(
    val appId: String?,
    val packageName: String?,
    val appName: String?,
    val versionName: String?,
    val versionCode: Long?,
)

data class HonorCurrentRelease(
    val versionName: String?,
    val versionCode: Long?,
    val auditStatus: String?,
    val auditResult: Int?,
    val releaseStatus: String?,
)

@Serializable
data class HonorFileUploadPath(
    val fileName: String? = null,
    val uploadUrl: String? = null,
    @SerialName("id")
    val objectId: String? = null,
    val expireTime: Long? = null,
)

data class HonorFileUploadPaths(
    val files: List<HonorFileUploadPath>,
)

@Serializable
internal data class HonorTokenResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
    @SerialName("token_type")
    val tokenType: String? = null,
)

@Serializable
internal data class HonorResponse<T>(
    val code: Int? = null,
    @SerialName("msg")
    val message: String? = null,
    val data: T? = null,
)

@Serializable
internal data class HonorAppDetailData(
    val appId: String? = null,
    val packageName: String? = null,
    val appName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val releaseInfo: HonorAppDetailReleaseInfo? = null,
    val currentRelease: HonorAppDetailReleaseInfo? = null,
)

@Serializable
internal data class HonorAppDetailReleaseInfo(
    val versionName: String? = null,
    val versionCode: Long? = null,
)

@Serializable
internal data class HonorCurrentReleaseData(
    val versionName: String? = null,
    val versionCode: String? = null,
    @SerialName("reviewStatus")
    val auditStatus: String? = null,
    val auditResult: Int? = null,
    @SerialName("publishStatus")
    val releaseStatus: String? = null,
)
