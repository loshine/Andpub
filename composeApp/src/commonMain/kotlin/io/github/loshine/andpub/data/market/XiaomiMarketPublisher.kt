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
                auditStatus = updateStatusText(result.updateVersion, result.updateInfo),
                releaseStatus = releaseStatusText(info, result.create),
                updatedAtText = "小米 API",
            )
        }

    private fun updateStatusText(
        updateVersion: Boolean?,
        updateInfo: Boolean?,
    ): String =
        listOfNotNull(
            updateVersion?.let { if (it) "允许更新版本" else "不允许更新版本" },
            updateInfo?.let { if (it) "允许更新资料" else "不允许更新资料" },
        ).joinToString("，").ifBlank { "未返回更新状态" }

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
