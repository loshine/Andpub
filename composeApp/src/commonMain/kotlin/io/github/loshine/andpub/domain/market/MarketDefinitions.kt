package io.github.loshine.andpub.domain.market

import io.github.loshine.andpub.domain.model.FieldKind
import io.github.loshine.andpub.domain.model.FieldSchema
import io.github.loshine.andpub.domain.model.MarketCapability
import io.github.loshine.andpub.domain.model.MarketSchema
import io.github.loshine.andpub.domain.model.MarketType

object MarketDefinitions {
    val schemas: List<MarketSchema> = listOf(
        MarketSchema(
            marketType = MarketType.Huawei,
            capability = MarketCapability(
                supportsUnifiedApk = true,
                supportsSplitApk = false,
                supportsAab = true,
                supportsUserUrl = true,
                supportsAppInfoQuery = true,
                supportsPublishStatusQuery = true,
            ),
            credentialFields = listOf(
                FieldSchema("clientId", "client_id"),
                FieldSchema("accessToken", "access_token", kind = FieldKind.Password),
            ),
        ),
        MarketSchema(
            marketType = MarketType.Honor,
            capability = MarketCapability(
                supportsUnifiedApk = true,
                supportsSplitApk = false,
                supportsAab = false,
                supportsUserUrl = true,
                supportsAppInfoQuery = true,
                supportsPublishStatusQuery = true,
            ),
            credentialFields = clientCredentialFields(),
        ),
        MarketSchema(
            marketType = MarketType.Xiaomi,
            capability = MarketCapability(
                supportsUnifiedApk = true,
                supportsSplitApk = false,
                supportsAab = false,
                supportsUserUrl = false,
                supportsAppInfoQuery = true,
                supportsPublishStatusQuery = false,
            ),
            credentialFields = listOf(
                FieldSchema("userName", "userName"),
                FieldSchema("password", "访问密码", kind = FieldKind.Password),
                FieldSchema("publicKey", "公钥 PEM / X.509 证书内容", kind = FieldKind.Multiline),
            ),
        ),
        MarketSchema(
            marketType = MarketType.Oppo,
            capability = MarketCapability(
                supportsUnifiedApk = true,
                supportsSplitApk = true,
                supportsAab = false,
                supportsUserUrl = false,
                supportsAppInfoQuery = true,
                supportsPublishStatusQuery = true,
            ),
            credentialFields = clientCredentialFields(),
        ),
        MarketSchema(
            marketType = MarketType.Vivo,
            capability = MarketCapability(
                supportsUnifiedApk = true,
                supportsSplitApk = true,
                supportsAab = false,
                supportsUserUrl = true,
                supportsAppInfoQuery = true,
                supportsPublishStatusQuery = true,
            ),
            credentialFields = listOf(
                FieldSchema("accessKey", "access_key"),
                FieldSchema("accessSecret", "access_secret", kind = FieldKind.Password),
            ),
        ),
        MarketSchema(
            marketType = MarketType.Tencent,
            capability = MarketCapability(
                supportsUnifiedApk = true,
                supportsSplitApk = true,
                supportsAab = false,
                supportsUserUrl = false,
                supportsAppInfoQuery = true,
                supportsPublishStatusQuery = true,
            ),
            credentialFields = listOf(
                FieldSchema("userId", "user_id"),
                FieldSchema("accessSecret", "access_secret", kind = FieldKind.Password),
            ),
        ),
    )

    fun schemaOf(marketType: MarketType): MarketSchema =
        schemas.first { it.marketType == marketType }

    private fun clientCredentialFields(): List<FieldSchema> =
        listOf(
            FieldSchema("clientId", "client_id"),
            FieldSchema("clientSecret", "client_secret", kind = FieldKind.Password),
        )
}
