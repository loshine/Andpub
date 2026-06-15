package io.github.loshine.andpub.platform

import io.github.loshine.andpub.domain.model.ArtifactInspection
import io.github.loshine.andpub.domain.model.ToolStatus

data class ArtifactDownloadTarget(
    val marketFolder: String,
    val publishTimeFolder: String,
    val artifactFolder: String,
    val variantFolder: String? = null,
)

expect suspend fun pickArtifactFilePath(): String?

expect suspend fun inspectToolSettings(
    androidSdkPath: String,
    bundletoolPath: String,
): ToolStatus

expect suspend fun inspectLocalArtifact(
    path: String,
    androidSdkPath: String,
    bundletoolPath: String,
): Result<ArtifactInspection>

expect suspend fun downloadArtifactFromUrl(
    url: String,
    target: ArtifactDownloadTarget,
): Result<String>

expect suspend fun readBinaryFile(path: String): Result<ByteArray>
