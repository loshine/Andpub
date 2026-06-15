package io.github.loshine.andpub.data.remote.vivo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VivoUploadResult(
    val packageName: String? = null,
    val serialNumber: String? = null,
    val md5: String? = null,
)

data class VivoOperationResult(
    val message: String?,
    val taskId: String?,
)

data class VivoUnifiedUpdateRequest(
    val packageName: String,
    val versionCode: Long,
    val apk: String,
    val fileMd5: String,
    val onlineType: String,
    val compatibleDevice: String,
) {
    fun toParams(): Map<String, String> =
        mapOf(
            "packageName" to packageName,
            "versionCode" to versionCode.toString(),
            "apk" to apk,
            "fileMd5" to fileMd5,
            "onlineType" to onlineType,
            "compatibleDevice" to compatibleDevice,
        )
}

data class VivoSplitUpdateRequest(
    val packageName: String,
    val apk32: String,
    val apk64: String,
    val onlineType: String,
    val compatibleDevice: String,
) {
    fun toParams(): Map<String, String> =
        mapOf(
            "packageName" to packageName,
            "apk32" to apk32,
            "apk64" to apk64,
            "onlineType" to onlineType,
            "compatibleDevice" to compatibleDevice,
        )
}

@Serializable
data class VivoTaskStatus(
    val packageName: String? = null,
    val taskStatus: Int? = null,
    val errorReason: String? = null,
)

@Serializable
data class VivoAppDetail(
    val packageName: String? = null,
    val appName: String? = null,
    val versionName: String? = null,
    val status: Int? = null,
    val saleStatus: Int? = null,
    val unPassReason: String? = null,
    val onlineStatus: Int? = null,
    val onlineType: Int? = null,
)

@Serializable
internal data class VivoResponse<T>(
    val code: String? = null,
    val subCode: String? = null,
    val msg: String? = null,
    val subMsg: String? = null,
    val data: T? = null,
)

@Serializable
internal data class VivoOperationData(
    @SerialName("task_id")
    val taskId: String? = null,
)

@Serializable
internal data class VivoUploadData(
    val packageName: String? = null,
    @SerialName("serialnumber")
    val serialNumber: String? = null,
    @SerialName("fileMd5")
    val md5: String? = null,
)

@Serializable
internal data class VivoTaskStatusData(
    val packageName: String? = null,
    @SerialName("status")
    val taskStatus: Int? = null,
    val errorReason: String? = null,
)
