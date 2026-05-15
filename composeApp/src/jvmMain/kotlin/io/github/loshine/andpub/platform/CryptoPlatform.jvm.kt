package io.github.loshine.andpub.platform

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
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

private fun String.toRsaPublicKey(): PublicKey {
    val derBytes = Base64.getDecoder().decode(toPemBody())
    if (contains("BEGIN CERTIFICATE")) {
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(derBytes.inputStream())
            .publicKey
    }

    val keySpec = X509EncodedKeySpec(derBytes)
    return KeyFactory.getInstance("RSA").generatePublic(keySpec)
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
