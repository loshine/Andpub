package io.github.loshine.andpub

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform