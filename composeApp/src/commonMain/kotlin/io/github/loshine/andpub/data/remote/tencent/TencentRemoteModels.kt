package io.github.loshine.andpub.data.remote.tencent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TencentOperationResult(
    val message: String?,
)

@Serializable
data class TencentAppDetail(
    val packageName: String? = null,
    val appName: String? = null,
    val versionName: String? = null,
)

@Serializable
data class TencentFileUploadInfo(
    val preSignUrl: String?,
    val serialNumber: String?,
)

@Serializable
data class TencentUpdateStatus(
    val auditStatus: Int?,
    val message: String?,
)

@Serializable
internal data class TencentResponse(
    val ret: Int? = null,
    @SerialName("msg")
    val message: String? = null,
    @SerialName("pkg_name")
    val packageName: String? = null,
    @SerialName("app_name")
    val appName: String? = null,
    @SerialName("version_name")
    val versionName: String? = null,
    @SerialName("pre_sign_url")
    val preSignUrl: String? = null,
    @SerialName("serial_number")
    val serialNumber: String? = null,
    @SerialName("audit_status")
    val auditStatus: Int? = null,
)
