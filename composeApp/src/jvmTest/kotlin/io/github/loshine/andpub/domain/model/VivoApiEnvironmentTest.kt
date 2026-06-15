package io.github.loshine.andpub.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VivoApiEnvironmentTest : StringSpec({
    "vivo environment defaults to production for legacy extra fields" {
        VivoApiEnvironment.fromExtraFields(emptyMap()) shouldBe VivoApiEnvironment.Production
        ChannelRecord(
            id = "channel-1",
            appId = "app-1",
            marketType = MarketType.Vivo,
            marketAppId = null,
            credentials = emptyMap(),
            extraFields = emptyMap(),
        ).vivoEnvironment() shouldBe VivoApiEnvironment.Production
    }

    "vivo environment persists through channel extra fields" {
        val extraFields = emptyMap<String, String>().withVivoEnvironment(VivoApiEnvironment.Sandbox)

        extraFields[VivoApiEnvironment.ExtraFieldKey] shouldBe "sandbox"
        VivoApiEnvironment.fromExtraFields(extraFields) shouldBe VivoApiEnvironment.Sandbox
    }
})
