package io.github.loshine.andpub.domain.usecase

import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.AppSettingsExport
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.ExportedAppSettings
import io.github.loshine.andpub.domain.model.ExportedChannelSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BuildAppSettingsExportUseCase {
    operator fun invoke(
        app: AppRecord,
        channels: List<ChannelRecord>,
    ): String =
        exportJson.encodeToString(
            AppSettingsExport(
                app = ExportedAppSettings(
                    name = app.name,
                    packageName = app.packageName,
                ),
                channels = channels.map { channel ->
                    ExportedChannelSettings(
                        name = channel.name,
                        marketType = channel.marketType,
                        marketAppId = channel.marketAppId,
                        credentials = channel.credentials,
                        extraFields = channel.extraFields,
                    )
                },
            )
        )

    fun defaultFileName(app: AppRecord): String =
        "${app.packageName.replace('.', '_')}-andpub-settings.json"

    private companion object {
        val exportJson = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
