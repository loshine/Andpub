package io.github.loshine.andpub.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MarketType(val displayName: String) {
    Huawei("华为 AppGallery"),
    Honor("荣耀应用市场"),
    Xiaomi("小米应用市场"),
    Oppo("OPPO 应用市场"),
    Vivo("vivo 应用市场"),
    Tencent("腾讯应用开放平台"),
}

@Serializable
enum class HuaweiAuthMode(val displayName: String) {
    ServiceAccount("Service Account（推荐）"),
    ApiClient("API 客户端"),
    OAuthClient("OAuth 客户端"),
}

@Serializable
enum class PublishMode(val displayName: String) {
    UnifiedArtifact("统一产物"),
    PerChannelArtifact("按渠道产物"),
}

@Serializable
enum class ArtifactSourceType(val displayName: String) {
    LocalFile("本地文件"),
    Url("URL"),
}

@Serializable
enum class PackageType(val displayName: String) {
    Apk("统一 APK"),
    SplitApk("32/64 APK"),
    Aab("AAB"),
}

@Serializable
enum class SplitApkSlot(val displayName: String) {
    Arm32("32 位 APK"),
    Arm64("64 位 APK"),
}

@Serializable
enum class PublishTaskStatus(val displayName: String) {
    Created("已创建"),
    Validating("校验中"),
    Ready("待提交"),
    Failed("失败"),
}

@Serializable
enum class FieldKind {
    Text,
    Multiline,
    Password,
    Url,
}

@Serializable
enum class ChannelSyncStatus(val displayName: String) {
    NotSynced("未同步"),
    Syncing("查询中"),
    Synced("已同步"),
    Failed("查询失败"),
}

@Serializable
data class FieldSchema(
    val key: String,
    val label: String,
    val required: Boolean = true,
    val kind: FieldKind = FieldKind.Text,
)

@Serializable
data class MarketCapability(
    val supportsUnifiedApk: Boolean,
    val supportsSplitApk: Boolean,
    val supportsAab: Boolean,
    val supportsUserUrl: Boolean,
    val supportsAppInfoQuery: Boolean,
    val supportsPublishStatusQuery: Boolean,
)

@Serializable
data class MarketSchema(
    val marketType: MarketType,
    val capability: MarketCapability,
    val credentialFields: List<FieldSchema>,
    val channelFields: List<FieldSchema> = emptyList(),
)

@Serializable
data class AppRecord(
    val id: String,
    val name: String,
    val packageName: String,
)

@Serializable
data class ChannelRecord(
    val id: String,
    val appId: String,
    val name: String = "",
    val marketType: MarketType,
    val marketAppId: String?,
    val credentials: Map<String, String>,
    val extraFields: Map<String, String>,
    val appInfo: MarketAppInfo? = null,
    val syncStatus: ChannelSyncStatus = ChannelSyncStatus.NotSynced,
    val lastError: String? = null,
)

@Serializable
data class MarketAppInfo(
    val marketAppId: String,
    val packageName: String,
    val appName: String,
    val onlineVersion: String?,
    val auditStatus: String?,
    val releaseStatus: String?,
    val updatedAtText: String,
)

@Serializable
data class ToolSettings(
    val androidSdkPath: String = "",
    val bundletoolPath: String = "",
)

@Serializable
data class ToolStatus(
    val aapt2Path: String?,
    val bundletoolUsable: Boolean,
    val messages: List<String>,
)

@Serializable
data class ArtifactDraft(
    val sourceType: ArtifactSourceType = ArtifactSourceType.LocalFile,
    val packageType: PackageType = PackageType.Apk,
    val value: String = "",
    val md5: String = "",
    val sha1: String = "",
    val sha256: String = "",
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val abiList: List<String> = emptyList(),
    val message: String? = null,
    val split32: ArtifactPart = ArtifactPart(),
    val split64: ArtifactPart = ArtifactPart(),
)

@Serializable
data class ArtifactPart(
    val value: String = "",
    val md5: String = "",
    val sha1: String = "",
    val sha256: String = "",
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val abiList: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
data class PublishTaskRecord(
    val id: String,
    val appId: String,
    val channelId: String,
    val marketType: MarketType,
    val publishMode: PublishMode,
    val artifact: ArtifactDraft,
    val status: PublishTaskStatus,
    val logs: List<PublishTaskLog>,
)

@Serializable
data class PublishTaskLog(
    val level: LogLevel,
    val message: String,
)

@Serializable
enum class LogLevel {
    Info,
    Warning,
    Error,
}

@Serializable
data class ArtifactInspection(
    val fileName: String,
    val fileSizeBytes: Long,
    val md5: String,
    val sha1: String,
    val sha256: String,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val abiList: List<String>,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class LocalStateSnapshot(
    val apps: List<AppRecord> = emptyList(),
    val channels: List<ChannelRecord> = emptyList(),
    val publishTasks: List<PublishTaskRecord> = emptyList(),
    val channelArtifacts: Map<String, ArtifactDraft> = emptyMap(),
    val unifiedArtifact: ArtifactDraft = ArtifactDraft(),
    val toolSettings: ToolSettings = ToolSettings(),
    val selectedAppId: String? = null,
    val publishMode: PublishMode = PublishMode.UnifiedArtifact,
)
