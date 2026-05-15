package io.github.loshine.andpub.platform

import io.github.loshine.andpub.domain.storage.LocalStateStore

expect fun createLocalStateStore(): LocalStateStore
