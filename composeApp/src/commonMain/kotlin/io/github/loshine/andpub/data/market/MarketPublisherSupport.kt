package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketPublishRequest
import io.github.loshine.andpub.domain.model.PublishTaskLog

internal fun ChannelRecord.requiredCredential(key: String): String =
    credentials[key]?.takeIf { it.isNotBlank() }
        ?: error("${marketType.displayName} 缺少 $key")

internal fun String.fileName(): String =
    trim().substringAfterLast('/').substringAfterLast('\\').ifBlank { "artifact.apk" }

internal fun Long.readableBytes(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        "${(mb * 10).toInt() / 10.0} MB"
    } else {
        "${this / 1024} KB"
    }
}

internal fun MutableList<PublishTaskLog>.emit(
    request: MarketPublishRequest,
    log: PublishTaskLog,
) {
    add(log)
    request.onLog(log)
}

/**
 * Resolves the local filesystem path for an artifact.
 * For LocalFile artifacts, returns the selected path.
 * For URL artifacts, returns the already-downloaded path.
 * Throws with [unsupportedUrlMessage] if the artifact is a URL but has not been downloaded yet.
 */
internal fun ArtifactDraft.resolveLocalPath(unsupportedUrlMessage: String): String =
    if (sourceType == ArtifactSourceType.LocalFile) {
        value.takeIf { it.isNotBlank() } ?: error("缺少 APK 文件路径")
    } else {
        downloadedPath.takeIf { it.isNotBlank() } ?: error(unsupportedUrlMessage)
    }
