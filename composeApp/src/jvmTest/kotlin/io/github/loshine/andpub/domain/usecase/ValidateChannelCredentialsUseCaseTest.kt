package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.HuaweiCredentialKeys
import io.github.loshine.andpub.domain.model.MarketType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ValidateChannelCredentialsUseCaseTest : StringSpec({
    val validate = ValidateChannelCredentialsUseCase()

    "Huawei Service Account accepts private json credentials" {
        validate(
            MarketType.Huawei,
            mapOf(
                HuaweiCredentialKeys.AuthMode to "serviceAccount",
                "serviceAccountJson" to """
                    {
                      "key_id": "kid",
                      "private_key": "private-key",
                      "sub_account": "sub",
                      "token_uri": "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
                    }
                """.trimIndent(),
            ),
        ).shouldBeNull()
    }

    "Huawei Service Account accepts split credentials" {
        validate(
            MarketType.Huawei,
            mapOf(
                HuaweiCredentialKeys.AuthMode to "serviceAccount",
                "keyId" to "kid",
                "privateKey" to "private-key",
                "subAccount" to "sub",
                "tokenUri" to "https://oauth-login.cloud.huawei.com/oauth2/v3/token",
            ),
        ).shouldBeNull()
    }

    "Huawei Service Account reports invalid private json" {
        validate(
            MarketType.Huawei,
            mapOf(
                HuaweiCredentialKeys.AuthMode to "serviceAccount",
                "serviceAccountJson" to """{"key_id":"kid"}""",
            ),
        ) shouldBe "华为 AppGallery 缺少 private_key"
    }

    "Huawei defaults to API Client credentials" {
        validate(
            MarketType.Huawei,
            emptyMap(),
        ) shouldBe "华为 AppGallery 缺少 client_id"
    }

    "Huawei API Client validates client credentials" {
        validate(
            MarketType.Huawei,
            mapOf(
                HuaweiCredentialKeys.AuthMode to "apiClient",
                "clientId" to "cid",
                "clientSecret" to "secret",
            ),
        ).shouldBeNull()

        validate(
            MarketType.Huawei,
            mapOf(HuaweiCredentialKeys.AuthMode to "apiClient"),
        ) shouldBe "华为 AppGallery 缺少 client_id"
    }

    "Huawei OAuth Client validates team token credentials" {
        validate(
            MarketType.Huawei,
            mapOf(
                HuaweiCredentialKeys.AuthMode to "oauthClient",
                "teamId" to "team-1",
                "oauth2Token" to "oauth-token",
            ),
        ).shouldBeNull()

        validate(
            MarketType.Huawei,
            mapOf(HuaweiCredentialKeys.AuthMode to "oauthClient"),
        ) shouldBe "华为 AppGallery 缺少 teamId"
    }
})
