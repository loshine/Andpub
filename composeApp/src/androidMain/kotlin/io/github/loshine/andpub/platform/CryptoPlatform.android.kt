package io.github.loshine.andpub.platform

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun hmacSha256Hex(secret: String, plain: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.encodeToByteArray(), "HmacSHA256"))
    return mac.doFinal(plain.encodeToByteArray()).toHex()
}

actual fun md5Hex(value: String): String =
    MessageDigest.getInstance("MD5")
        .digest(value.encodeToByteArray())
        .toHex()

actual fun rsaPublicEncryptHex(publicKey: String, plain: String): String {
    val key = publicKey.toRsaPublicKey()
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.encryptByBlocks(plain.encodeToByteArray(), key.maxPkcs1PlainBlockSize()).toHex()
}

actual fun rsaPssSha256Base64Url(privateKey: String, plain: String): String {
    val signature = Signature.getInstance("RSASSA-PSS").apply {
        setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        initSign(privateKey.toRsaPrivateKey())
        update(plain.encodeToByteArray())
    }
    return base64UrlNoPadding(signature.sign())
}

actual fun base64UrlNoPadding(value: ByteArray): String =
    Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

private fun String.toRsaPublicKey(): PublicKey {
    val derBytes = Base64.decode(toPemBody(), Base64.DEFAULT)
    if (contains("BEGIN CERTIFICATE")) {
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(derBytes))
            .publicKey
    }

    val keySpec = X509EncodedKeySpec(derBytes)
    return KeyFactory.getInstance("RSA").generatePublic(keySpec)
}

private fun String.toRsaPrivateKey(): PrivateKey {
    val derBytes = Base64.decode(toPemBody(), Base64.DEFAULT)
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(derBytes))
}

private fun String.toPemBody(): String =
    replace(Regex("-----BEGIN [^-]+-----"), "")
        .replace(Regex("-----END [^-]+-----"), "")
        .filterNot { it.isWhitespace() }

private fun PublicKey.maxPkcs1PlainBlockSize(): Int {
    val rsaKey = this as? RSAPublicKey
        ?: error("小米 publicKey 不是 RSA 公钥")
    return (rsaKey.modulus.bitLength() + 7) / 8 - 11
}

private fun Cipher.encryptByBlocks(
    input: ByteArray,
    blockSize: Int,
): ByteArray {
    require(blockSize > 0) { "RSA 公钥长度不合法" }
    return input.asIterable()
        .chunked(blockSize)
        .flatMap { block -> doFinal(block.toByteArray()).asIterable() }
        .toByteArray()
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }
