package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.AppSettingsExport
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.LocalStateSnapshot
import kotlinx.serialization.json.Json

class ImportAppSettingsUseCase {
    data class ImportResult(
        val app: AppRecord,
        val channels: List<ChannelRecord>,
        val warnings: List<String>,
    )

    operator fun invoke(
        json: String,
        snapshot: LocalStateSnapshot,
    ): Result<ImportResult> = runCatching {
        val export = importJson.decodeFromString<AppSettingsExport>(json)
        require(export.schemaVersion == 1) {
            "不支持的配置格式版本：${export.schemaVersion}，当前仅支持版本 1"
        }

        val packageName = export.app.packageName.trim()
        require(packageName.isNotBlank()) { "导入文件中包名为空" }

        val warnings = mutableListOf<String>()

        // Reuse existing app record if package name matches, otherwise create new
        val existingApp = snapshot.apps.firstOrNull { it.packageName == packageName }
        val nextId = snapshot.nextAvailableId()
        val app: AppRecord
        val channelIdOffset: Int

        if (existingApp != null) {
            app = existingApp
            channelIdOffset = nextId
            warnings += "应用包名 $packageName 已存在，将向该应用追加渠道"
        } else {
            app = AppRecord(
                id = "app-$nextId",
                name = export.app.name.trim().ifBlank { packageName },
                packageName = packageName,
            )
            channelIdOffset = nextId + 1
        }

        val existingChannels = snapshot.channels.filter { it.appId == app.id }
        val channels = export.channels.mapIndexed { index, exported ->
            val marketType = exported.marketType
            val duplicate = existingChannels.firstOrNull { ch ->
                ch.marketType == marketType &&
                        ch.credentials == exported.credentials &&
                        ch.marketAppId == exported.marketAppId
            }
            if (duplicate != null) {
                warnings += "${marketType.displayName} 渠道配置与已有渠道完全相同，已跳过"
                null
            } else {
                ChannelRecord(
                    id = "channel-${channelIdOffset + index}",
                    appId = app.id,
                    name = exported.name,
                    marketType = marketType,
                    marketAppId = exported.marketAppId,
                    credentials = exported.credentials,
                    extraFields = exported.extraFields,
                )
            }
        }.filterNotNull()

        ImportResult(app = app, channels = channels, warnings = warnings)
    }

    private companion object {
        val importJson = Json { ignoreUnknownKeys = true }
    }
}
