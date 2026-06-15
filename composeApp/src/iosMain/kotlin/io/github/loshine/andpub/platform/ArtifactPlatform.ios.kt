package io.github.loshine.andpub.platform

import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ToolStatus

actual suspend fun pickArtifactFilePath(): String? = null

actual suspend fun inspectToolSettings(
    androidSdkPath: String,
    bundletoolPath: String,
): ToolStatus =
    ToolStatus(
        aapt2Path = null,
        bundletoolUsable = false,
        messages = listOf("iOS 端暂未接入本地工具检测"),
    )

actual suspend fun inspectLocalArtifact(
    path: String,
    androidSdkPath: String,
    bundletoolPath: String,
): Result<ArtifactInspection> =
    Result.failure(UnsupportedOperationException("iOS 端暂未接入文件选择与解析"))

actual suspend fun downloadArtifactFromUrl(url: String): Result<String> =
    Result.failure(UnsupportedOperationException("iOS 端暂未接入 URL 产物下载"))

actual suspend fun readBinaryFile(path: String): Result<ByteArray> =
    Result.failure(UnsupportedOperationException("iOS 端暂未接入文件读取"))
