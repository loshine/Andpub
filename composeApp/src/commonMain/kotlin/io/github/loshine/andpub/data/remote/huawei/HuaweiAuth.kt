package io.github.loshine.andpub.data.remote.huawei

import io.github.loshine.andpub.domain.market.HuaweiServiceAccountCredentials
import io.github.loshine.andpub.platform.base64UrlNoPadding
import io.github.loshine.andpub.platform.rsaPssSha256Base64Url
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

sealed interface HuaweiAuthContext {
    data class ServiceAccount(val jwt: String) : HuaweiAuthContext

    data class ApiClient(
        val clientId: String,
        val accessToken: String,
    ) : HuaweiAuthContext

    data class OAuthClient(
        val teamId: String,
        val oauth2Token: String,
    ) : HuaweiAuthContext
}

interface HuaweiJwtGenerator {
    fun createServiceAccountJwt(credentials: HuaweiServiceAccountCredentials): String
}

class DefaultHuaweiJwtGenerator(
    private val nowEpochSeconds: () -> Long = {
        Clock.System.now().toEpochMilliseconds() / 1000
    },
) : HuaweiJwtGenerator {
    override fun createServiceAccountJwt(credentials: HuaweiServiceAccountCredentials): String {
        val issuedAt = nowEpochSeconds()
        val header = JsonObject(
            mapOf(
                "kid" to JsonPrimitive(credentials.keyId),
                "typ" to JsonPrimitive("JWT"),
                "alg" to JsonPrimitive("PS256"),
            )
        ).encodeJwtPart()
        val payload = JsonObject(
            mapOf(
                "aud" to JsonPrimitive(credentials.tokenUri),
                "iss" to JsonPrimitive(credentials.subAccount),
                "exp" to JsonPrimitive(issuedAt + 3600),
                "iat" to JsonPrimitive(issuedAt),
            )
        ).encodeJwtPart()
        val plain = "$header.$payload"
        val signature = rsaPssSha256Base64Url(credentials.privateKey, plain)
        return "$plain.$signature"
    }

    private fun JsonObject.encodeJwtPart(): String =
        base64UrlNoPadding(toString().encodeToByteArray())
}
