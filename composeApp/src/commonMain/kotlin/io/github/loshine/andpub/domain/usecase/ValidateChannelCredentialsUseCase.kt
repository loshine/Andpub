package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.market.missingHuaweiCredentialLabel
import io.github.loshine.andpub.domain.model.MarketType

class ValidateChannelCredentialsUseCase {
    operator fun invoke(
        marketType: MarketType,
        credentials: Map<String, String>,
    ): String? {
        if (marketType == MarketType.Huawei) {
            return credentials.missingHuaweiCredentialLabel()?.let {
                "${marketType.displayName} 缺少 $it"
            }
        }

        val schema = MarketDefinitions.schemaOf(marketType)
        val missingField = schema.credentialFields.firstOrNull { field ->
            field.required && credentials[field.key].orEmpty().isBlank()
        }
        return missingField?.let { "${marketType.displayName} 缺少 ${it.label}" }
    }
}
