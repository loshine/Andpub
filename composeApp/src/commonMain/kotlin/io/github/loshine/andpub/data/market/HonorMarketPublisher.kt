package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.honor.HonorRemoteDataSource
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import org.koin.core.annotation.Single

@Single
class HonorMarketPublisher(
    private val remote: HonorRemoteDataSource,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Honor

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val token = remote.obtainToken(
                clientId = channel.requiredCredential("clientId"),
                clientSecret = channel.requiredCredential("clientSecret"),
            ).accessToken
            val appId = channel.marketAppId
                ?: remote.getAppId(token, app.packageName).apps.firstOrNull()?.appId
                ?: error("荣耀未返回 APPID")
            val detail = remote.getAppDetail(token, appId)
            val currentRelease = remote.getCurrentRelease(token, appId)
            MarketAppInfo(
                marketAppId = appId,
                packageName = detail.packageName ?: app.packageName,
                appName = detail.appName ?: app.name,
                onlineVersion = currentRelease?.versionName ?: detail.versionName,
                auditStatus = currentRelease?.auditResult?.let { auditStatusText(it) } ?: currentRelease?.auditStatus,
                releaseStatus = currentRelease?.releaseStatus ?: detail.versionName?.let { "已上架" },
                updatedAtText = "荣耀 API",
            )
        }

    private fun auditStatusText(status: Int): String =
        when (status) {
            0 -> "审核通过"
            1 -> "审核不通过"
            else -> "未知审核状态($status)"
        }
}
