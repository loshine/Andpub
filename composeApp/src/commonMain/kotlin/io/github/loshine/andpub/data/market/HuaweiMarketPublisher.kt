package io.github.loshine.andpub.data.market

import io.github.loshine.andpub.data.remote.huawei.HuaweiAuditInfo
import io.github.loshine.andpub.data.remote.huawei.DefaultHuaweiJwtGenerator
import io.github.loshine.andpub.data.remote.huawei.HuaweiAuthContext
import io.github.loshine.andpub.data.remote.huawei.HuaweiJwtGenerator
import io.github.loshine.andpub.data.remote.huawei.HuaweiRemoteDataSource
import io.github.loshine.andpub.domain.market.HuaweiCredentialKeys
import io.github.loshine.andpub.domain.market.MarketPublisher
import io.github.loshine.andpub.domain.market.huaweiAuthMode
import io.github.loshine.andpub.domain.market.resolveHuaweiServiceAccountCredentials
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.HuaweiAuthMode
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketType
import org.koin.core.annotation.Single

@Single
class HuaweiMarketPublisher(
    private val remote: HuaweiRemoteDataSource,
    private val jwtGenerator: HuaweiJwtGenerator = DefaultHuaweiJwtGenerator(),
) : MarketPublisher {
    override val marketType: MarketType = MarketType.Huawei

    override suspend fun fetchAppInfo(app: AppRecord, channel: ChannelRecord): Result<MarketAppInfo> =
        runCatching {
            val auth = channel.huaweiAuthContext()
            val configuredAppId = channel.marketAppId
            val appId = configuredAppId ?: fetchAppId(auth, app.packageName)
            val appInfoResult = runCatching {
                remote.getAppInfo(auth, appId)
            }.recoverCatching { error ->
                val resolvedAppId = configuredAppId?.let { fetchAppId(auth, app.packageName) }
                if (resolvedAppId != null && resolvedAppId != configuredAppId) {
                    remote.getAppInfo(auth, resolvedAppId)
                } else {
                    throw error
                }
            }.getOrThrow()
            val appInfo = appInfoResult.appInfo
            val releaseState = appInfo?.releaseState
            MarketAppInfo(
                marketAppId = appId,
                packageName = app.packageName,
                appName = appInfoResult.languages.firstNotNullOfOrNull { it.appName } ?: app.name,
                onlineVersion = appInfo?.onShelfVersionNumber ?: appInfo?.versionNumber
                    ?: "华为接口未返回线上版本",
                reviewingVersion = appInfo?.versionNumber,
                auditStatus = auditStatusText(releaseState, appInfoResult.auditInfo)
                    ?: "华为接口未返回审核状态",
                releaseStatus = releaseStatusText(releaseState, appInfoResult.phasedReleaseInfo?.state),
                updatedAtText = "华为 API",
            )
        }

    private suspend fun fetchAppId(
        auth: HuaweiAuthContext,
        packageName: String,
    ): String =
        remote.getAppIdList(auth, packageName)
            .appIds
            .firstOrNull()
            ?.appId
            ?: error("华为未返回 appId")

    private suspend fun ChannelRecord.huaweiAuthContext(): HuaweiAuthContext =
        when (credentials.huaweiAuthMode()) {
            HuaweiAuthMode.ServiceAccount -> {
                val serviceAccount = credentials.resolveHuaweiServiceAccountCredentials().getOrThrow()
                HuaweiAuthContext.ServiceAccount(jwtGenerator.createServiceAccountJwt(serviceAccount))
            }
            HuaweiAuthMode.ApiClient -> {
                val clientId = requiredCredential(HuaweiCredentialKeys.ClientId)
                val accessToken = remote.obtainToken(
                    clientId = clientId,
                    clientSecret = requiredCredential(HuaweiCredentialKeys.ClientSecret),
                ).accessToken
                HuaweiAuthContext.ApiClient(clientId = clientId, accessToken = accessToken)
            }
            HuaweiAuthMode.OAuthClient ->
                HuaweiAuthContext.OAuthClient(
                    teamId = requiredCredential(HuaweiCredentialKeys.TeamId),
                    oauth2Token = requiredCredential(HuaweiCredentialKeys.OAuth2Token),
                )
        }

    private fun auditStatusText(
        releaseState: Int?,
        auditInfo: HuaweiAuditInfo?,
    ): String? {
        val mainStatus = releaseState?.let { releaseStateText(it) }
        val auditOpinion = auditInfo?.auditOpinion?.takeIf { it.isNotBlank() }
        val auditResults = listOfNotNull(
            auditInfo?.copyRightAuditResult?.let { "版权审核${passStatusText(it)}" },
            auditInfo?.copyRightCodeAuditResult?.let { "版号审核${passStatusText(it)}" },
            auditInfo?.recordAuditResult?.let { "备案审核${passStatusText(it)}" },
        )
        return listOfNotNull(mainStatus, auditOpinion)
            .plus(auditResults)
            .joinToString("，")
            .takeIf { it.isNotBlank() }
    }

    private fun releaseStateText(releaseState: Int): String =
        when (releaseState) {
            0 -> "审核通过"
            1 -> "上架审核不通过"
            2 -> "已下架，无审核中版本"
            3 -> "待上架，预约上架"
            4 -> "审核中"
            5 -> "升级审核中"
            6 -> "下架审核中"
            7 -> "草稿，未提交审核"
            8 -> "升级审核不通过"
            9 -> "下架审核不通过"
            10 -> "开发者已下架，无审核中版本"
            11 -> "已撤销上架"
            12 -> "预审中"
            13 -> "预审不通过"
            else -> "未知审核状态($releaseState)"
        }

    private fun passStatusText(status: Int): String =
        when (status) {
            0 -> "通过"
            1 -> "不通过"
            else -> "未知($status)"
        }

    private fun releaseStatusText(
        releaseState: Int?,
        phasedReleaseState: String?,
    ): String {
        val releaseText = when (releaseState) {
            0 -> "已上架"
            1 -> "上架审核不通过"
            2 -> "已下架（含强制下架）"
            3 -> "待上架，预约上架"
            4 -> "审核中"
            5 -> "升级审核中"
            6 -> "申请下架"
            7 -> "草稿"
            8 -> "升级审核不通过"
            9 -> "下架审核不通过"
            10 -> "应用被开发者下架"
            11 -> "撤销上架"
            12 -> "预审中"
            13 -> "预审不通过"
            null -> "华为接口未返回上架状态"
            else -> "未知上架状态($releaseState)"
        }
        val phasedText = phasedReleaseState?.let { phasedReleaseStatusText(it) }
        return listOfNotNull(releaseText, phasedText).joinToString("，")
    }

    private fun phasedReleaseStatusText(state: String): String =
        when (state) {
            "SUSPEND" -> "分阶段发布暂停"
            "RELEASE" -> "分阶段发布中"
            "CANCEL" -> "分阶段发布取消"
            "DRAFT" -> "分阶段发布草稿"
            else -> "未知分阶段发布状态($state)"
        }
}
