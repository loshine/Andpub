package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.vivo.VivoRemoteDataSource
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import org.koin.core.annotation.Single

@Single
class VivoMarketPublisher(
    private val remote: VivoRemoteDataSource,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Vivo

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val data = remote.queryAppDetails(
                accessKey = channel.requiredCredential("accessKey"),
                accessSecret = channel.requiredCredential("accessSecret"),
                packageName = app.packageName,
            )
            val saleStatus = data.saleStatus ?: data.onlineStatus
            MarketAppInfo(
                marketAppId = channel.marketAppId ?: app.packageName,
                packageName = data.packageName ?: app.packageName,
                appName = data.appName ?: app.name,
                onlineVersion = data.versionName,
                reviewingVersion = data.versionName,
                auditStatus = auditStatusText(data.status, data.unPassReason),
                releaseStatus = releaseStatusText(saleStatus, data.onlineType)
                    ?: data.saleStatus?.toString()
                    ?: data.onlineStatus?.toString(),
                updatedAtText = "vivo API",
            )
        }

    private fun auditStatusText(
        status: Int?,
        unPassReason: String?,
    ): String? {
        val statusText = when (status) {
            1 -> "草稿"
            2 -> "待审核"
            3 -> "审核通过"
            4 -> "审核不通过"
            5 -> "撤销审核"
            null -> return null
            else -> "未知审核状态($status)"
        }
        val reason = unPassReason?.takeIf { status == 4 && it.isNotBlank() }
        return listOfNotNull(statusText, reason).joinToString("：")
    }

    private fun releaseStatusText(
        saleStatus: Int?,
        onlineType: Int?,
    ): String? {
        val saleText = when (saleStatus) {
            0 -> "待上架"
            1 -> "已上架"
            2 -> "已下架"
            null -> return null
            else -> "未知上架状态($saleStatus)"
        }
        val typeText = onlineType?.let { onlineTypeText(it) }
        return listOfNotNull(saleText, typeText).joinToString("，")
    }

    private fun onlineTypeText(onlineType: Int): String =
        when (onlineType) {
            1 -> "实时上架"
            2 -> "定时上架"
            else -> "未知上架类型($onlineType)"
        }
}
