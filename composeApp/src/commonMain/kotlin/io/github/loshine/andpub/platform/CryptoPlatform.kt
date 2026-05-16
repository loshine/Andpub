package io.github.loshine.andpub.platform

expect fun hmacSha256Hex(secret: String, plain: String): String

expect fun md5Hex(value: String): String

expect fun rsaPublicEncryptHex(publicKey: String, plain: String): String

expect fun rsaPssSha256Base64Url(privateKey: String, plain: String): String

expect fun base64UrlNoPadding(value: ByteArray): String
