package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.tencent.TencentRemoteDataSource
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import org.koin.core.annotation.Single

@Single
class TencentMarketPublisher(
    private val remote: TencentRemoteDataSource,
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Tencent

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val appId = channel.marketAppId ?: error("腾讯查询应用详情需要填写市场侧应用 ID")
            val detail = remote.queryAppDetail(
                userId = channel.requiredCredential("userId"),
                accessSecret = channel.requiredCredential("accessSecret"),
                packageName = app.packageName,
                appId = appId,
            )
            val updateStatus = remote.queryAppUpdateStatus(
                userId = channel.requiredCredential("userId"),
                accessSecret = channel.requiredCredential("accessSecret"),
                packageName = app.packageName,
                appId = appId,
            )
            val auditStatus = updateStatus?.auditStatus
            val onlineVersion = detail.versionName ?: fetchStoreVersion(app.packageName)
            MarketAppInfo(
                marketAppId = appId,
                packageName = detail.packageName ?: app.packageName,
                appName = detail.appName ?: app.name,
                onlineVersion = onlineVersion ?: "腾讯接口和应用宝网页均未返回线上版本",
                reviewingVersion = auditStatus?.let { detail.versionName },
                auditStatus = auditStatus?.let { auditStatusText(it) },
                releaseStatus = releaseStatusText(auditStatus),
                updatedAtText = "腾讯 API",
            )
        }

    private suspend fun fetchStoreVersion(packageName: String): String? =
        runCatching {
            parseTencentStoreVersion(remote.getStorePage(packageName))
        }.getOrNull()

    private fun auditStatusText(status: Int): String =
        when (status) {
            1 -> "审核中"
            2 -> "审核驳回"
            3 -> "审核通过"
            8 -> "开发者主动撤销"
            else -> "未知审核状态($status)"
        }

    private fun releaseStatusText(auditStatus: Int?): String =
        when (auditStatus) {
            1 -> "已上架，更新审核中"
            2 -> "已上架，更新审核驳回"
            3 -> "已上架，更新审核通过"
            8 -> "已上架，更新已撤销"
            else -> "已上架"
        }

    private fun parseTencentStoreVersion(html: String): String? {
        val text = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("<[^>]+>"), "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        val versionPattern = Regex("""[0-9]+(?:[._-][0-9A-Za-z]+)+""")
        Regex("""版本号\s*[:：]?\s*(${versionPattern.pattern})""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            if (line == "版本号") {
                lines.drop(index + 1)
                    .firstOrNull { versionPattern.matches(it) }
                    ?.let { return it }
            }
        }
        return Regex("""\bV\s*(${versionPattern.pattern})\b""")
            .find(text)
            ?.groupValues
            ?.get(1)
    }
}
