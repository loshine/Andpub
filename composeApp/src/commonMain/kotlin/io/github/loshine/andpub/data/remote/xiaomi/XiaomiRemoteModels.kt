package io.github.loshine.andpub.data.remote.xiaomi

import kotlinx.serialization.Serializable

@Serializable
data class XiaomiQueryAppResult(
    val packageInfo: XiaomiPackageInfo? = null,
    val create: Boolean? = null,
    val updateVersion: Boolean? = null,
    val updateInfo: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class XiaomiPackageInfo(
    val appName: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
)

@Serializable
data class XiaomiOperationResult(
    val message: String?,
)

@Serializable
internal data class XiaomiResponse(
    val result: Int? = null,
    val message: String? = null,
    val packageInfo: XiaomiPackageInfo? = null,
    val create: Boolean? = null,
    val updateVersion: Boolean? = null,
    val updateInfo: Boolean? = null,
)
