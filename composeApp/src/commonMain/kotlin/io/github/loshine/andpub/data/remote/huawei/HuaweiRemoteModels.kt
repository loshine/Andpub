package io.github.loshine.andpub.data.remote.huawei

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HuaweiToken(
    val accessToken: String,
    val expiresIn: Int?,
    val tokenType: String?,
)

@Serializable
data class HuaweiRet(
    val code: Int?,
    @SerialName("msg")
    val message: String?,
)

@Serializable
data class HuaweiOperationResult(
    val ret: HuaweiRet?,
)

@Serializable
data class HuaweiAppIdEntry(
    @SerialName("key")
    val appName: String? = null,
    @SerialName("value")
    val appId: String,
)

data class HuaweiAppIdList(
    val appIds: List<HuaweiAppIdEntry>,
)

data class HuaweiAppInfoResult(
    val appInfo: HuaweiAppInfo?,
    val auditInfo: HuaweiAuditInfo?,
    val languages: List<HuaweiLanguageInfo>,
    val phasedReleaseInfo: HuaweiPhasedReleaseInfo?,
)

@Serializable
data class HuaweiAppInfo(
    val releaseState: Int? = null,
    val versionNumber: String? = null,
    val versionCode: Long? = null,
    val versionId: String? = null,
    val onShelfVersionNumber: String? = null,
    val onShelfVersionCode: Long? = null,
    val onShelfVersionId: String? = null,
)

@Serializable
data class HuaweiAuditInfo(
    val auditOpinion: String? = null,
    val copyRightAuditResult: Int? = null,
    val copyRightCodeAuditResult: Int? = null,
    val recordAuditResult: Int? = null,
)

@Serializable
data class HuaweiLanguageInfo(
    val appName: String? = null,
    @SerialName("language")
    val lang: String? = null,
)

@Serializable
data class HuaweiPhasedReleaseInfo(
    val state: String? = null,
)

@Serializable
internal data class HuaweiTokenResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
    @SerialName("token_type")
    val tokenType: String? = null,
)

@Serializable
internal data class HuaweiResponse(
    val ret: String? = null,
    val appids: List<HuaweiAppIdEntry> = emptyList(),
    val appInfo: HuaweiAppInfo? = null,
    val auditInfo: HuaweiAuditInfo? = null,
    val languages: List<HuaweiLanguageInfo> = emptyList(),
    val phasedReleaseInfo: HuaweiPhasedReleaseInfo? = null,
)
