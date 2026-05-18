package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.ChannelSyncStatus
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BuildAppSettingsExportUseCaseTest : StringSpec({
    "exports selected app channels without runtime fields" {
        val app = AppRecord("app-1", "猫耳", "cn.missevan")
        val channels = listOf(
            ChannelRecord(
                id = "channel-1",
                appId = "app-1",
                name = "vivo 国内",
                marketType = MarketType.Vivo,
                marketAppId = "market-1",
                credentials = mapOf("accessKey" to "ak", "accessSecret" to "secret"),
                extraFields = mapOf("region" to "cn"),
                appInfo = MarketAppInfo(
                    marketAppId = "market-1",
                    packageName = "cn.missevan",
                    appName = "猫耳",
                    onlineVersion = "1.0.0",
                    reviewingVersion = "1.1.0",
                    auditStatus = "待审核",
                    releaseStatus = "已上架",
                    updatedAtText = "vivo API",
                ),
                syncStatus = ChannelSyncStatus.Synced,
                lastError = "old error",
            )
        )

        val json = BuildAppSettingsExportUseCase()(app, channels)
        val root = Json.parseToJsonElement(json).jsonObject
        val channel = root.getValue("channels").jsonArray.single().jsonObject

        root.getValue("schemaVersion").jsonPrimitive.content shouldBe "1"
        root.getValue("app").jsonObject.getValue("packageName").jsonPrimitive.content shouldBe "cn.missevan"
        channel.getValue("name").jsonPrimitive.content shouldBe "vivo 国内"
        channel.getValue("marketType").jsonPrimitive.content shouldBe "Vivo"
        channel.getValue("marketAppId").jsonPrimitive.content shouldBe "market-1"
        channel.getValue("credentials").jsonObject.getValue("accessSecret").jsonPrimitive.content shouldBe "secret"
        json shouldNotContain "appInfo"
        json shouldNotContain "syncStatus"
        json shouldNotContain "lastError"
        json shouldNotContain "publishTasks"
        json shouldNotContain "toolSettings"
    }
})
