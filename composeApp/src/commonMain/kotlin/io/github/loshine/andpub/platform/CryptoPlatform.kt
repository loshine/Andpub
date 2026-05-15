package io.github.loshine.andpub.platform

expect fun hmacSha256Hex(secret: String, plain: String): String

expect fun md5Hex(value: String): String

expect fun rsaPublicEncryptHex(publicKey: String, plain: String): String
