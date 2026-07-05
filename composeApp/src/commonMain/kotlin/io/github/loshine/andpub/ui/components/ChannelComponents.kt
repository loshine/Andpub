package io.github.loshine.andpub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.market.HuaweiCredentialKeys
import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.market.huaweiAuthMode
import io.github.loshine.andpub.domain.market.storageValue
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.ChannelSyncStatus
import io.github.loshine.andpub.domain.model.FieldKind
import io.github.loshine.andpub.domain.model.FieldSchema
import io.github.loshine.andpub.domain.model.HuaweiAuthMode
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketCapability
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.domain.model.vivoEnvironment
import io.github.loshine.andpub.domain.model.withVivoEnvironment
import io.github.loshine.andpub.presentation.AndpubUiState

// ─── Channel card ──────────────────────────────────────────────────────────────

@Composable
fun ChannelSummaryCard(
    channel: ChannelRecord,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(channel.displayTitle(), style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            channel.marketType.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SyncStatusChip(channel.syncStatus)
                    }
                    channel.appInfo?.let { info ->
                        Text(
                            "线上：${info.onlineVersion ?: "-"}  审核：${info.reviewingVersion ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    channel.lastError?.let { err ->
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "获取应用信息")
                    }
                    IconButton(onClick = onToggleExpanded) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (expanded) {
                ChannelExpandedContent(channel)
            }
        }
    }
}

@Composable
private fun SyncStatusChip(status: ChannelSyncStatus) {
    val containerColor = when (status) {
        ChannelSyncStatus.Synced -> MaterialTheme.colorScheme.primaryContainer
        ChannelSyncStatus.Failed -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when (status) {
        ChannelSyncStatus.Synced -> MaterialTheme.colorScheme.onPrimaryContainer
        ChannelSyncStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SuggestionChip(
        onClick = {},
        label = { Text(status.displayName, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
        ),
    )
}

@Composable
private fun ChannelExpandedContent(channel: ChannelRecord) {
    val schema = MarketDefinitions.schemaOf(channel.marketType)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (channel.marketType == MarketType.Huawei) {
            Text(
                "鉴权方式：${channel.credentials.huaweiAuthMode().displayName}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        channel.marketAppId?.let {
            Text("市场应用 ID：$it", style = MaterialTheme.typography.bodySmall)
        }
        CapabilityChips(schema.capability)
    }
}

@Composable
fun CapabilityChips(capability: MarketCapability) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (capability.supportsUnifiedApk) CapBadge("APK")
        if (capability.supportsSplitApk) CapBadge("32/64")
        if (capability.supportsAab) CapBadge("AAB")
        if (capability.supportsUserUrl) CapBadge("URL")
        if (capability.supportsAppInfoQuery) CapBadge("详情查询")
    }
}

@Composable
private fun CapBadge(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

// ─── Channel info dialog ───────────────────────────────────────────────────────

@Composable
fun ChannelInfoDialog(channel: ChannelRecord, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text("${channel.displayTitle()} 应用信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("同步状态：${channel.syncStatus.displayName}")
                if (channel.syncStatus == ChannelSyncStatus.Syncing) {
                    Text("正在获取市场侧应用信息…")
                } else {
                    channel.appInfo?.let { MarketAppInfoContent(it) }
                }
                channel.lastError?.let {
                    Text("错误：$it", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun MarketAppInfoContent(info: MarketAppInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("市场应用 ID：${info.marketAppId}")
        Text("应用名：${info.appName}")
        Text("包名：${info.packageName}")
        Text("线上版本：${info.onlineVersion ?: "-"}")
        Text("正在审核版本：${info.reviewingVersion ?: "-"}")
        Text("审核状态：${info.auditStatus ?: "-"}")
        Text("上架状态：${info.releaseStatus ?: "-"}")
        Text("更新时间：${info.updatedAtText}")
    }
}

// ─── Channel editor dialog ─────────────────────────────────────────────────────

internal enum class HuaweiServiceAccountInputMode(val displayName: String) {
    Json("粘贴 private.json"),
    SplitFields("拆字段录入"),
}

@Composable
fun ChannelEditorDialog(
    state: AndpubUiState,
    channel: ChannelRecord?,
    onDismiss: () -> Unit,
    onSave: (String?, String, MarketType, String?, Map<String, String>, Map<String, String>) -> Unit,
    onTest: (String, MarketType, String?, Map<String, String>, Map<String, String>) -> Unit,
) {
    var marketType by remember(channel?.id) {
        androidx.compose.runtime.mutableStateOf(channel?.marketType ?: MarketType.Huawei)
    }
    var huaweiAuthMode by remember(channel?.id) {
        androidx.compose.runtime.mutableStateOf(
            channel?.credentials?.huaweiAuthMode() ?: HuaweiAuthMode.ApiClient
        )
    }
    var vivoEnvironment by remember(channel?.id) {
        androidx.compose.runtime.mutableStateOf(
            channel?.vivoEnvironment() ?: VivoApiEnvironment.Production
        )
    }
    var serviceAccountInputMode by remember(channel?.id) {
        androidx.compose.runtime.mutableStateOf(
            if (channel?.credentials?.get(HuaweiCredentialKeys.ServiceAccountJson)
                    .orEmpty().isNotBlank()
            ) HuaweiServiceAccountInputMode.Json
            else HuaweiServiceAccountInputMode.SplitFields
        )
    }
    val schema = MarketDefinitions.schemaOf(marketType)
    val credentials = remember(marketType, channel?.id) {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            if (marketType == MarketType.Huawei) {
                HuaweiCredentialKeys.allKeys.forEach { key ->
                    put(key, channel?.credentials?.get(key).orEmpty())
                }
            } else {
                schema.credentialFields.forEach { field ->
                    put(field.key, channel?.credentials?.get(field.key).orEmpty())
                }
            }
        }
    }
    var channelName by remember(channel?.id) { androidx.compose.runtime.mutableStateOf(channel?.name.orEmpty()) }
    var marketAppId by remember(marketType, channel?.id) {
        androidx.compose.runtime.mutableStateOf(channel?.marketAppId.orEmpty())
    }
    val testKey = channel?.id
        ?: "new-${marketType.name}-${if (marketType == MarketType.Huawei) huaweiAuthMode.name else "default"}"
    val testState = state.channelTests[testKey]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (channel == null) "新增渠道" else "编辑渠道") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CapabilityChips(schema.capability)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MarketType.entries.forEach { type ->
                        StableFilterChip(
                            selected = marketType == type,
                            onClick = { marketType = type },
                            label = { Text(type.displayName) },
                        )
                    }
                }

                androidx.compose.material3.OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("渠道名称，可选") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = marketAppId,
                    onValueChange = { marketAppId = it },
                    label = { Text("市场侧应用 ID，可通过接口查询后回填") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (marketType == MarketType.Vivo) {
                    VivoEnvironmentFields(
                        environment = vivoEnvironment,
                        onEnvironmentChange = { vivoEnvironment = it },
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (marketType == MarketType.Huawei) {
                        HuaweiCredentialFields(
                            authMode = huaweiAuthMode,
                            onAuthModeChange = { huaweiAuthMode = it },
                            serviceAccountInputMode = serviceAccountInputMode,
                            onServiceAccountInputModeChange = { serviceAccountInputMode = it },
                            credentials = credentials,
                        )
                    } else {
                        schema.credentialFields.forEach { field ->
                            SchemaTextField(
                                field = field,
                                value = credentials[field.key].orEmpty(),
                                onValueChange = { credentials[field.key] = it },
                                modifier = Modifier.width(260.dp),
                            )
                        }
                    }
                }

                if (testState?.info != null || testState?.error != null) {
                    ConnectionTestResult(info = testState.info, error = testState.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        channel?.id,
                        channelName,
                        marketType,
                        marketAppId,
                        credentials.toChannelCredentials(
                            marketType, schema.credentialFields, huaweiAuthMode, serviceAccountInputMode
                        ),
                        channel.extraFieldsFor(marketType, vivoEnvironment),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onTest(
                            testKey,
                            marketType,
                            marketAppId,
                            credentials.toChannelCredentials(
                                marketType, schema.credentialFields, huaweiAuthMode, serviceAccountInputMode
                            ),
                            channel.extraFieldsFor(marketType, vivoEnvironment),
                        )
                    },
                ) {
                    Text(if (testState?.isLoading == true) "测试中…" else "测试连接")
                }
                OutlinedButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun VivoEnvironmentFields(
    environment: VivoApiEnvironment,
    onEnvironmentChange: (VivoApiEnvironment) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("vivo API 环境", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VivoApiEnvironment.entries.forEach { item ->
                StableFilterChip(
                    selected = environment == item,
                    onClick = { onEnvironmentChange(item) },
                    label = { Text(item.displayName) },
                )
            }
        }
        Text(
            "测试环境和正式环境的 access_key / access_secret 彼此独立，不能混用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun HuaweiCredentialFields(
    authMode: HuaweiAuthMode,
    onAuthModeChange: (HuaweiAuthMode) -> Unit,
    serviceAccountInputMode: HuaweiServiceAccountInputMode,
    onServiceAccountInputModeChange: (HuaweiServiceAccountInputMode) -> Unit,
    credentials: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HuaweiAuthMode.entries.forEach { mode ->
                StableFilterChip(
                    selected = authMode == mode,
                    onClick = { onAuthModeChange(mode) },
                    label = { Text(mode.displayName) },
                )
            }
        }

        when (authMode) {
            HuaweiAuthMode.ServiceAccount -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HuaweiServiceAccountInputMode.entries.forEach { mode ->
                        StableFilterChip(
                            selected = serviceAccountInputMode == mode,
                            onClick = { onServiceAccountInputModeChange(mode) },
                            label = { Text(mode.displayName) },
                        )
                    }
                }
                when (serviceAccountInputMode) {
                    HuaweiServiceAccountInputMode.Json ->
                        SchemaTextField(
                            field = FieldSchema(
                                HuaweiCredentialKeys.ServiceAccountJson,
                                "Service Account private.json",
                                kind = FieldKind.Multiline,
                            ),
                            value = credentials[HuaweiCredentialKeys.ServiceAccountJson].orEmpty(),
                            onValueChange = { credentials[HuaweiCredentialKeys.ServiceAccountJson] = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    HuaweiServiceAccountInputMode.SplitFields ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                FieldSchema(HuaweiCredentialKeys.KeyId, "key_id"),
                                FieldSchema(HuaweiCredentialKeys.PrivateKey, "private_key", kind = FieldKind.Multiline),
                                FieldSchema(HuaweiCredentialKeys.SubAccount, "sub_account"),
                                FieldSchema(HuaweiCredentialKeys.TokenUri, "token_uri", kind = FieldKind.Url),
                            ).forEach { field ->
                                SchemaTextField(
                                    field = field,
                                    value = credentials[field.key].orEmpty(),
                                    onValueChange = { credentials[field.key] = it },
                                    modifier = Modifier.width(
                                        if (field.kind == FieldKind.Multiline) 532.dp else 260.dp
                                    ),
                                )
                            }
                        }
                }
            }
            HuaweiAuthMode.ApiClient ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        FieldSchema(HuaweiCredentialKeys.ClientId, "client_id"),
                        FieldSchema(HuaweiCredentialKeys.ClientSecret, "client_secret", kind = FieldKind.Password),
                    ).forEach { field ->
                        SchemaTextField(
                            field = field,
                            value = credentials[field.key].orEmpty(),
                            onValueChange = { credentials[field.key] = it },
                            modifier = Modifier.width(260.dp),
                        )
                    }
                }
            HuaweiAuthMode.OAuthClient ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        FieldSchema(HuaweiCredentialKeys.TeamId, "teamId"),
                        FieldSchema(HuaweiCredentialKeys.OAuth2Token, "oauth2Token", kind = FieldKind.Password),
                    ).forEach { field ->
                        SchemaTextField(
                            field = field,
                            value = credentials[field.key].orEmpty(),
                            onValueChange = { credentials[field.key] = it },
                            modifier = Modifier.width(260.dp),
                        )
                    }
                }
        }
    }
}

@Composable
private fun ConnectionTestResult(info: MarketAppInfo?, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("测试连接结果", style = MaterialTheme.typography.titleSmall)
        info?.let { MarketAppInfoContent(it) }
        error?.let { Text("错误：$it", color = MaterialTheme.colorScheme.error) }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun ChannelRecord.displayTitle(): String =
    name.takeIf { it.isNotBlank() } ?: marketType.displayName

private fun ChannelRecord?.extraFieldsFor(
    marketType: MarketType,
    vivoEnvironment: VivoApiEnvironment,
): Map<String, String> {
    val base = this?.extraFields.orEmpty()
    return if (marketType == MarketType.Vivo) {
        base.withVivoEnvironment(vivoEnvironment)
    } else {
        base - VivoApiEnvironment.ExtraFieldKey
    }
}

internal fun Map<String, String>.toChannelCredentials(
    marketType: MarketType,
    fields: List<FieldSchema>,
    huaweiAuthMode: HuaweiAuthMode,
    serviceAccountInputMode: HuaweiServiceAccountInputMode,
): Map<String, String> {
    if (marketType != MarketType.Huawei) {
        return fields.associate { field -> field.key to get(field.key).orEmpty() }
    }
    val selected = mutableMapOf(HuaweiCredentialKeys.AuthMode to huaweiAuthMode.storageValue())
    when (huaweiAuthMode) {
        HuaweiAuthMode.ServiceAccount -> when (serviceAccountInputMode) {
            HuaweiServiceAccountInputMode.Json ->
                selected[HuaweiCredentialKeys.ServiceAccountJson] =
                    get(HuaweiCredentialKeys.ServiceAccountJson).orEmpty()
            HuaweiServiceAccountInputMode.SplitFields -> {
                selected[HuaweiCredentialKeys.KeyId] = get(HuaweiCredentialKeys.KeyId).orEmpty()
                selected[HuaweiCredentialKeys.PrivateKey] = get(HuaweiCredentialKeys.PrivateKey).orEmpty()
                selected[HuaweiCredentialKeys.SubAccount] = get(HuaweiCredentialKeys.SubAccount).orEmpty()
                selected[HuaweiCredentialKeys.TokenUri] = get(HuaweiCredentialKeys.TokenUri).orEmpty()
            }
        }
        HuaweiAuthMode.ApiClient -> {
            selected[HuaweiCredentialKeys.ClientId] = get(HuaweiCredentialKeys.ClientId).orEmpty()
            selected[HuaweiCredentialKeys.ClientSecret] = get(HuaweiCredentialKeys.ClientSecret).orEmpty()
        }
        HuaweiAuthMode.OAuthClient -> {
            selected[HuaweiCredentialKeys.TeamId] = get(HuaweiCredentialKeys.TeamId).orEmpty()
            selected[HuaweiCredentialKeys.OAuth2Token] = get(HuaweiCredentialKeys.OAuth2Token).orEmpty()
        }
    }
    return selected
}
