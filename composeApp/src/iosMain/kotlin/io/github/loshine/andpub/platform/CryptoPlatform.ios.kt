package io.github.loshine.andpub.platform

actual fun hmacSha256Hex(secret: String, plain: String): String =
    throw UnsupportedOperationException("iOS 端暂未接入签名算法")

actual fun md5Hex(value: String): String =
    throw UnsupportedOperationException("iOS 端暂未接入 MD5")

actual fun rsaPublicEncryptHex(publicKey: String, plain: String): String =
    throw UnsupportedOperationException("iOS 端暂未接入 RSA 加密")

actual fun rsaPssSha256Base64Url(privateKey: String, plain: String): String =
    throw UnsupportedOperationException("Huawei Service Account signing is not supported on iOS yet")

actual fun base64UrlNoPadding(value: ByteArray): String =
    throw UnsupportedOperationException("iOS 端暂未接入 Base64URL 编码")
