package io.github.loshine.andpub.data.remote.huawei

import io.github.loshine.andpub.domain.market.HuaweiServiceAccountCredentials
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.Base64

class HuaweiJwtGeneratorTest : StringSpec({
    "Service Account JWT uses documented PS256 header and payload" {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val credentials = HuaweiServiceAccountCredentials(
            keyId = "kid-1",
            privateKey = keyPair.private.encoded.toPrivateKeyPem(),
            subAccount = "sub-1",
            tokenUri = "https://oauth-login.cloud.huawei.com/oauth2/v3/token",
        )

        val jwt = DefaultHuaweiJwtGenerator(nowEpochSeconds = { 1_700_000_000L })
            .createServiceAccountJwt(credentials)

        val parts = jwt.split(".")
        parts.shouldHaveSize(3)
        val header = Json.parseToJsonElement(parts[0].base64UrlDecode()).jsonObject
        val payload = Json.parseToJsonElement(parts[1].base64UrlDecode()).jsonObject

        header["kid"]?.jsonPrimitive?.content shouldBe "kid-1"
        header["typ"]?.jsonPrimitive?.content shouldBe "JWT"
        header["alg"]?.jsonPrimitive?.content shouldBe "PS256"
        payload["aud"]?.jsonPrimitive?.content shouldBe "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
        payload["iss"]?.jsonPrimitive?.content shouldBe "sub-1"
        payload["iat"]?.jsonPrimitive?.content shouldBe "1700000000"
        payload["exp"]?.jsonPrimitive?.content shouldBe "1700003600"

        val verifier = Signature.getInstance("RSASSA-PSS").apply {
            setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
            initVerify(keyPair.public)
            update("${parts[0]}.${parts[1]}".encodeToByteArray())
        }
        verifier.verify(Base64.getUrlDecoder().decode(parts[2])) shouldBe true
    }
})

private fun ByteArray.toPrivateKeyPem(): String =
    buildString {
        appendLine("-----BEGIN PRIVATE KEY-----")
        appendLine(Base64.getMimeEncoder(64, "\n".encodeToByteArray()).encodeToString(this@toPrivateKeyPem))
        appendLine("-----END PRIVATE KEY-----")
    }

private fun String.base64UrlDecode(): String =
    Base64.getUrlDecoder().decode(this).decodeToString()
