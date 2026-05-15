package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.oppo.OppoRemoteDataSource
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import org.koin.core.annotation.Single

@Single
class OppoMarketPublisher(
    private val remote: OppoRemoteDataSource,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Oppo

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val clientSecret = channel.requiredCredential("clientSecret")
            val accessToken = remote.obtainToken(
                clientId = channel.requiredCredential("clientId"),
                clientSecret = clientSecret,
            ).accessToken
            val data = remote.getAppInfo(accessToken, clientSecret, app.packageName).appInfo
            val auditStatus = data?.auditStatus
            val releaseStatus = data?.releaseStatus?.takeIf { it.isNotBlank() }
            MarketAppInfo(
                marketAppId = data?.appId ?: channel.marketAppId ?: app.packageName,
                packageName = data?.packageName ?: app.packageName,
                appName = data?.appName ?: app.name,
                onlineVersion = data?.versionName,
                auditStatus = data?.auditStatusName ?: auditStatus?.let { statusText(it) },
                releaseStatus = releaseStatus?.let { statusText(it) }
                    ?: auditStatus?.let { statusText(it) },
                updatedAtText = "OPPO API",
            )
        }

    private fun statusText(status: String): String =
        when (status) {
            "0" -> "未发布"
            "1" -> "审核中"
            "2" -> "审核通过"
            "3" -> "测试不通过"
            "4" -> "运营审核中"
            "5" -> "运营打回"
            "6" -> "运营通过"
            "7" -> "定时发布"
            "00" -> "资质审核中"
            "11" -> "资质审核通过"
            "-11" -> "资质审核不通过"
            "-22" -> "报备提交成功"
            "22" -> "已冻结"
            "111" -> "上线"
            "222" -> "下线"
            "444" -> "审核不通过"
            else -> status
        }
}
