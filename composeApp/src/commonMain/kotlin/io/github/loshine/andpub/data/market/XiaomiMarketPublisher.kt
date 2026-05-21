package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.xiaomi.XiaomiPackageInfo
import io.github.loshine.andpub.data.remote.xiaomi.XiaomiRemoteDataSource
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import org.koin.core.annotation.Single

@Single
class XiaomiMarketPublisher(
    private val remote: XiaomiRemoteDataSource,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Xiaomi

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val result = remote.queryApp(
                userName = channel.requiredCredential("userName"),
                password = channel.requiredCredential("password"),
                publicKey = channel.requiredCredential("publicKey"),
                packageName = app.packageName,
            )
            val info = result.packageInfo
            MarketAppInfo(
                marketAppId = channel.marketAppId ?: app.packageName,
                packageName = info?.packageName ?: app.packageName,
                appName = info?.appName ?: app.name,
                onlineVersion = info?.versionName,
                reviewingVersion = "小米接口暂不支持获取审核中版本",
                auditStatus = "小米暂不支持获取审核状态",
                releaseStatus = releaseStatusText(info, result.create),
                updatedAtText = "小米 API",
            )
        }

    private fun releaseStatusText(
        packageInfo: XiaomiPackageInfo?,
        create: Boolean?,
    ): String =
        when {
            packageInfo != null -> "已存在"
            create == true -> "未创建，可新增"
            create == false -> "未创建，不可新增"
            else -> "未返回上架状态"
        }
}
