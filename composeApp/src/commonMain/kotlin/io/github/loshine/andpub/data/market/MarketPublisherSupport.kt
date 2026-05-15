package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.domain.model.ChannelRecord

internal fun ChannelRecord.requiredCredential(key: String): String =
    credentials[key]?.takeIf { it.isNotBlank() }
        ?: error("${marketType.displayName} 缺少 $key")
