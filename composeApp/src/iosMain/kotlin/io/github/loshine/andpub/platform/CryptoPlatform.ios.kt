package io.github.loshine.andpub.platform

actual fun hmacSha256Hex(secret: String, plain: String): String =
    throw UnsupportedOperationException("iOS 端暂未接入签名算法")

actual fun md5Hex(value: String): String =
    throw UnsupportedOperationException("iOS 端暂未接入 MD5")

actual fun rsaPublicEncryptHex(publicKey: String, plain: String): String =
    throw UnsupportedOperationException("iOS 端暂未接入 RSA 加密")
