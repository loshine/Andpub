package io.github.loshine.andpub.domain.market

import io.github.loshine.andpub.domain.model.HuaweiAuthMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object HuaweiCredentialKeys {
    const val AuthMode = "huaweiAuthMode"
    const val ServiceAccountJson = "serviceAccountJson"
    const val KeyId = "keyId"
    const val PrivateKey = "privateKey"
    const val SubAccount = "subAccount"
    const val TokenUri = "tokenUri"
    const val ClientId = "clientId"
    const val ClientSecret = "clientSecret"
    const val TeamId = "teamId"
    const val OAuth2Token = "oauth2Token"

    val allKeys: List<String> = listOf(
        AuthMode,
        ServiceAccountJson,
        KeyId,
        PrivateKey,
        SubAccount,
        TokenUri,
        ClientId,
        ClientSecret,
        TeamId,
        OAuth2Token,
    )
}

data class HuaweiServiceAccountCredentials(
    val keyId: String,
    val privateKey: String,
    val subAccount: String,
    val tokenUri: String,
)

fun Map<String, String>.huaweiAuthMode(): HuaweiAuthMode {
    val value = this[HuaweiCredentialKeys.AuthMode].orEmpty()
    return when (value) {
        "serviceAccount", HuaweiAuthMode.ServiceAccount.name -> HuaweiAuthMode.ServiceAccount
        "apiClient", HuaweiAuthMode.ApiClient.name -> HuaweiAuthMode.ApiClient
        "oauthClient", HuaweiAuthMode.OAuthClient.name -> HuaweiAuthMode.OAuthClient
        "" -> {
            if (hasLegacyHuaweiServiceAccountCredentials()) {
                HuaweiAuthMode.ServiceAccount
            } else {
                HuaweiAuthMode.ApiClient
            }
        }
        else -> HuaweiAuthMode.ApiClient
    }
}

fun HuaweiAuthMode.storageValue(): String =
    when (this) {
        HuaweiAuthMode.ServiceAccount -> "serviceAccount"
        HuaweiAuthMode.ApiClient -> "apiClient"
        HuaweiAuthMode.OAuthClient -> "oauthClient"
    }

fun Map<String, String>.resolveHuaweiServiceAccountCredentials(): Result<HuaweiServiceAccountCredentials> =
    runCatching {
        val jsonValue = this[HuaweiCredentialKeys.ServiceAccountJson].orEmpty().trim()
        if (jsonValue.isNotEmpty()) {
            val parsed = serviceAccountJson.decodeFromString<HuaweiServiceAccountJson>(jsonValue)
            return@runCatching HuaweiServiceAccountCredentials(
                keyId = parsed.keyId.requireHuaweiField("key_id"),
                privateKey = parsed.privateKey.requireHuaweiField("private_key"),
                subAccount = parsed.subAccount.requireHuaweiField("sub_account"),
                tokenUri = parsed.tokenUri.requireHuaweiField("token_uri"),
            )
        }

        HuaweiServiceAccountCredentials(
            keyId = this[HuaweiCredentialKeys.KeyId].requireHuaweiField("key_id"),
            privateKey = this[HuaweiCredentialKeys.PrivateKey].requireHuaweiField("private_key"),
            subAccount = this[HuaweiCredentialKeys.SubAccount].requireHuaweiField("sub_account"),
            tokenUri = this[HuaweiCredentialKeys.TokenUri].requireHuaweiField("token_uri"),
        )
    }

fun Map<String, String>.missingHuaweiCredentialLabel(): String? =
    when (huaweiAuthMode()) {
        HuaweiAuthMode.ServiceAccount ->
            resolveHuaweiServiceAccountCredentials().exceptionOrNull()?.message
        HuaweiAuthMode.ApiClient ->
            firstBlankHuaweiField(
                HuaweiCredentialKeys.ClientId to "client_id",
                HuaweiCredentialKeys.ClientSecret to "client_secret",
            )
        HuaweiAuthMode.OAuthClient ->
            firstBlankHuaweiField(
                HuaweiCredentialKeys.TeamId to "teamId",
                HuaweiCredentialKeys.OAuth2Token to "oauth2Token",
            )
    }

private fun Map<String, String>.hasLegacyHuaweiServiceAccountCredentials(): Boolean =
    this[HuaweiCredentialKeys.ServiceAccountJson].orEmpty().isNotBlank() ||
            this[HuaweiCredentialKeys.KeyId].orEmpty().isNotBlank() ||
            this[HuaweiCredentialKeys.PrivateKey].orEmpty().isNotBlank() ||
            this[HuaweiCredentialKeys.SubAccount].orEmpty().isNotBlank() ||
            this[HuaweiCredentialKeys.TokenUri].orEmpty().isNotBlank()

private fun Map<String, String>.firstBlankHuaweiField(vararg fields: Pair<String, String>): String? =
    fields.firstOrNull { (key, _) -> this[key].orEmpty().isBlank() }?.second

private fun String?.requireHuaweiField(label: String): String =
    this?.takeIf { it.isNotBlank() } ?: error(label)

@Serializable
private data class HuaweiServiceAccountJson(
    @SerialName("key_id")
    val keyId: String? = null,
    @SerialName("private_key")
    val privateKey: String? = null,
    @SerialName("sub_account")
    val subAccount: String? = null,
    @SerialName("token_uri")
    val tokenUri: String? = null,
)

private val serviceAccountJson = Json {
    ignoreUnknownKeys = true
}
