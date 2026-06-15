package io.github.loshine.andpub.data.remote.vivo

import io.github.loshine.andpub.data.remote.currentMillis
import io.github.loshine.andpub.data.remote.decodeResponse
import io.github.loshine.andpub.data.remote.signPlain
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.platform.hmacSha256Hex
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import org.koin.core.annotation.Single

@Single
class VivoRemoteDataSource(
    private val client: HttpClient,
) {
    suspend fun queryAppDetails(
        accessKey: String,
        accessSecret: String,
        packageName: String,
        environment: VivoApiEnvironment = VivoApiEnvironment.Production,
    ): VivoAppDetail =
        call<VivoAppDetail>(
            marketName = "vivo应用详情",
            method = "app.query.details",
            accessKey = accessKey,
            accessSecret = accessSecret,
            params = mapOf("packageName" to packageName),
            environment = environment,
        )
            .data
            ?: error("vivo 未返回应用详情")

    suspend fun uploadApk(
        accessKey: String,
        accessSecret: String,
        packageName: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMd5: String,
        stageType: String? = null,
        environment: VivoApiEnvironment = VivoApiEnvironment.Production,
    ): VivoUploadResult =
        upload("vivo上传APK", "app.upload.apk.app", accessKey, accessSecret, packageName, fileName, fileBytes, fileMd5, stageType, environment)

    suspend fun uploadApk32(
        accessKey: String,
        accessSecret: String,
        packageName: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMd5: String,
        stageType: String? = null,
        environment: VivoApiEnvironment = VivoApiEnvironment.Production,
    ): VivoUploadResult =
        upload("vivo上传32位APK", "app.upload.apk.app.32", accessKey, accessSecret, packageName, fileName, fileBytes, fileMd5, stageType, environment)

    suspend fun uploadApk64(
        accessKey: String,
        accessSecret: String,
        packageName: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMd5: String,
        stageType: String? = null,
        environment: VivoApiEnvironment = VivoApiEnvironment.Production,
    ): VivoUploadResult =
        upload("vivo上传64位APK", "app.upload.apk.app.64", accessKey, accessSecret, packageName, fileName, fileBytes, fileMd5, stageType, environment)

    suspend fun uploadIcon(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传图标", "app.upload.icon", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadScreenshot(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传截图", "app.upload.screenshot", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadQualification(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传特殊资质", "app.upload.qualification", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadECopyright(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传电子版权证书", "app.upload.ecopyright", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadCopyright(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传版权证明", "app.upload.copyright", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadSafetyReport(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传安全报告", "app.upload.safety.report", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadPrivateSelfCheck(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传隐私自检", "app.upload.private.self.check", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadCommitmentLetter(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传承诺函", "app.upload.commitment.letter", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadVideo(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传视频", "app.upload.video", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadVideoCover(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传视频封面", "app.upload.video.cover", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadIcpAuthLetter(accessKey: String, accessSecret: String, packageName: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo上传备案授权函", "app.upload.icp.auth.letter", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment)

    suspend fun uploadFile(accessKey: String, accessSecret: String, packageName: String, fileType: String, fileName: String, fileBytes: ByteArray, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoUploadResult =
        upload("vivo通用文件上传", "app.upload.file", accessKey, accessSecret, packageName, fileName, fileBytes, null, null, environment, mapOf("fileType" to fileType))

    suspend fun syncCreateApp(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo同步创建应用", "app.sync.create.app", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    suspend fun syncUpdateApp(accessKey: String, accessSecret: String, request: VivoUnifiedUpdateRequest, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo同步更新应用", "app.sync.update.app", accessKey, accessSecret, request.toParams(), environment = environment).toVivoOperationResult()

    suspend fun syncCreateSubpackageApp(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo同步创建分包应用", "app.sync.create.subpackage.app", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    suspend fun syncUpdateSubpackageApp(accessKey: String, accessSecret: String, request: VivoSplitUpdateRequest, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo同步更新分包应用", "app.sync.update.subpackage.app", accessKey, accessSecret, request.toParams(), environment = environment).toVivoOperationResult()

    suspend fun createApp(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo下载方式创建应用", "app.create.app", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    suspend fun updateApp(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo下载方式更新应用", "app.update.app", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    suspend fun createSubpackageApp(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo下载方式创建分包应用", "app.create.subpackage.app", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    suspend fun updateSubpackageApp(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo下载方式更新分包应用", "app.update.subpackage.app", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    suspend fun updateGame(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo游戏下载方式更新", "app.update.game", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    suspend fun queryTaskStatus(accessKey: String, accessSecret: String, packageName: String, packetType: Int, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoTaskStatus =
        call<VivoTaskStatusData>(
            "vivo任务状态",
            "app.query.task.status",
            accessKey,
            accessSecret,
            mapOf("packageName" to packageName, "packetType" to packetType.toString()),
            environment = environment,
        )
            .data
            ?.toVivoTaskStatus()
            ?: error("vivo任务状态未返回 data")

    suspend fun createUpdateStageApp(accessKey: String, accessSecret: String, params: Map<String, String>, environment: VivoApiEnvironment = VivoApiEnvironment.Production): VivoOperationResult =
        call<VivoOperationData>("vivo分阶段创建更新", "app.create.update.stage.app", accessKey, accessSecret, params, environment = environment).toVivoOperationResult()

    private suspend fun upload(
        marketName: String,
        method: String,
        accessKey: String,
        accessSecret: String,
        packageName: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMd5: String?,
        stageType: String?,
        environment: VivoApiEnvironment,
        extraParams: Map<String, String> = emptyMap(),
    ): VivoUploadResult {
        val params = linkedMapOf("packageName" to packageName)
        fileMd5?.let { params["fileMd5"] = it }
        stageType?.let { params["stageType"] = it }
        params.putAll(extraParams)
        return uploadCall<VivoUploadData>(
            marketName = marketName,
            method = method,
            accessKey = accessKey,
            accessSecret = accessSecret,
            params = params,
            fileName = fileName,
            fileBytes = fileBytes,
            environment = environment,
        )
            .data
            ?.toVivoUploadResult()
            ?: error("$marketName 未返回 data")
    }

    private suspend inline fun <reified T> call(
        marketName: String,
        method: String,
        accessKey: String,
        accessSecret: String,
        params: Map<String, String>,
        environment: VivoApiEnvironment = VivoApiEnvironment.Production,
    ): VivoResponse<T> {
        val signedParams = linkedMapOf(
            "method" to method,
            "access_key" to accessKey,
            "timestamp" to currentMillis(),
            "format" to "json",
            "v" to "1.0",
            "sign_method" to "HMAC-SHA256",
            "target_app_key" to "developer",
        )
        signedParams.putAll(params)
        signedParams["sign"] = hmacSha256Hex(accessSecret, signedParams.signPlain())
        val text = client.submitForm(
            url = environment.endpoint,
            formParameters = parameters {
                signedParams.forEach { (key, value) -> append(key, value) }
            },
        ).bodyAsText()
        val response = decodeResponse<VivoResponse<T>>(marketName, text)
        response.requireSuccess(marketName)
        return response
    }

    private suspend inline fun <reified T> uploadCall(
        marketName: String,
        method: String,
        accessKey: String,
        accessSecret: String,
        params: Map<String, String>,
        fileName: String,
        fileBytes: ByteArray,
        environment: VivoApiEnvironment,
    ): VivoResponse<T> {
        val signedParams = linkedMapOf(
            "method" to method,
            "access_key" to accessKey,
            "timestamp" to currentMillis(),
            "format" to "json",
            "v" to "1.0",
            "sign_method" to "HMAC-SHA256",
            "target_app_key" to "developer",
        )
        signedParams.putAll(params)
        signedParams["sign"] = hmacSha256Hex(accessSecret, signedParams.signPlain())
        val text = client.submitFormWithBinaryData(
            url = environment.endpoint,
            formData = formData {
                signedParams.forEach { (key, value) -> append(key, value) }
                append(
                    key = "file",
                    value = fileBytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    },
                )
            },
        ) {
            timeout {
                requestTimeoutMillis = VIVO_UPLOAD_REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = VIVO_UPLOAD_SOCKET_TIMEOUT_MILLIS
            }
        }.bodyAsText()
        val response = decodeResponse<VivoResponse<T>>(marketName, text)
        response.requireSuccess(marketName)
        return response
    }
}

private const val VIVO_UPLOAD_REQUEST_TIMEOUT_MILLIS = 10 * 60 * 1000L
private const val VIVO_UPLOAD_SOCKET_TIMEOUT_MILLIS = 2 * 60 * 1000L

private fun VivoResponse<VivoOperationData>.toVivoOperationResult(): VivoOperationResult =
    VivoOperationResult(
        message = msg ?: subMsg,
        taskId = data?.taskId,
    )

private fun VivoUploadData.toVivoUploadResult(): VivoUploadResult =
    VivoUploadResult(
        packageName = packageName,
        serialNumber = serialNumber,
        md5 = md5,
    )

private fun VivoTaskStatusData.toVivoTaskStatus(): VivoTaskStatus =
    VivoTaskStatus(
        packageName = packageName,
        taskStatus = taskStatus,
        errorReason = errorReason,
    )

private fun VivoResponse<*>.requireSuccess(marketName: String) {
    if (code == "10018") {
        error(
            "vivo 当前接入信息没有 app.query.details 访问权限。10018 表示禁止访问；" +
                "请确认开放平台 API 权限已开通，并且 accessKey 属于该应用主体。"
        )
    }
    val value = code ?: return
    require(value == "0" && (subCode == null || subCode == "0")) {
        "$marketName code=$value: ${(subMsg ?: msg).orEmpty()}"
    }
}
