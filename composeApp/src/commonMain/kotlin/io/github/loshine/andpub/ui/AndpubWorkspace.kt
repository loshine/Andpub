package io.github.loshine.andpub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.market.HuaweiCredentialKeys
import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.market.huaweiAuthMode
import io.github.loshine.andpub.domain.market.storageValue
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.ChannelSyncStatus
import io.github.loshine.andpub.domain.model.FieldKind
import io.github.loshine.andpub.domain.model.FieldSchema
import io.github.loshine.andpub.domain.model.HuaweiAuthMode
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketCapability
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.isTerminal
import io.github.loshine.andpub.domain.model.SplitApkSlot
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.domain.model.VivoCompatibleDevice
import io.github.loshine.andpub.domain.model.VivoOnlineType
import io.github.loshine.andpub.domain.model.vivoEnvironment
import io.github.loshine.andpub.domain.model.withVivoEnvironment
import io.github.loshine.andpub.platform.inspectLocalArtifact
import io.github.loshine.andpub.platform.inspectToolSettings
import io.github.loshine.andpub.platform.pickArtifactFilePath
import io.github.loshine.andpub.presentation.AndpubIntent
import io.github.loshine.andpub.presentation.AndpubUiState
import io.github.loshine.andpub.presentation.AndpubViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class HuaweiServiceAccountInputMode(val displayName: String) {
    Json("粘贴 private.json"),
    SplitFields("拆字段录入"),
}

private enum class AppDetailTab(val title: String) {
    Channels("渠道管理"),
    Publish("发布"),
}

@Composable
fun AndpubWorkspace(
    viewModel: AndpubViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            AppSidebar(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = Modifier
                    .width(304.dp)
                    .fillMaxHeight(),
            )
            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                AppDetail(
                    state = state,
                    onIntent = viewModel::onIntent,
                )
            }
        }
    }
}

@Composable
private fun AppSidebar(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var appDialogId by remember { mutableStateOf<String?>(null) }
    var deletingAppId by remember { mutableStateOf<String?>(null) }
    var settingsDialogVisible by remember { mutableStateOf(false) }
    val editingApp = state.apps.firstOrNull { it.id == appDialogId }
    val deletingApp = state.apps.firstOrNull { it.id == deletingAppId }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Andpub", style = MaterialTheme.typography.headlineMedium)
                Text("多市场发包客户端", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { settingsDialogVisible = true }) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "本地工具设置",
                )
            }
        }

        Button(
            onClick = { appDialogId = NEW_APP_DIALOG_ID },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("创建应用")
        }
        OutlinedButton(
            onClick = { onIntent(AndpubIntent.ExportSelectedAppSettings) },
            enabled = state.selectedApp != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("导出当前应用设置")
        }

        Text("应用列表", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.apps, key = { it.id }) { app ->
                val selected = state.selectedAppId == app.id
                if (selected) {
                    OutlinedCard(
                        onClick = { onIntent(AndpubIntent.SelectApp(app.id)) },
                        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppListItem(
                            name = app.name,
                            packageName = app.packageName,
                            onEdit = {
                                appDialogId = app.id
                            },
                            onDelete = {
                                deletingAppId = app.id
                            },
                        )
                    }
                } else {
                    OutlinedCard(
                        onClick = { onIntent(AndpubIntent.SelectApp(app.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppListItem(
                            name = app.name,
                            packageName = app.packageName,
                            onEdit = {
                                onIntent(AndpubIntent.SelectApp(app.id))
                                appDialogId = app.id
                            },
                            onDelete = {
                                onIntent(AndpubIntent.SelectApp(app.id))
                                deletingAppId = app.id
                            },
                        )
                    }
                }
            }
        }
    }

    if (appDialogId != null) {
        AppEditorDialog(
            appId = editingApp?.id,
            initialName = editingApp?.name.orEmpty(),
            initialPackageName = editingApp?.packageName.orEmpty(),
            onDismiss = { appDialogId = null },
            onSave = { name, packageName ->
                if (editingApp == null) {
                    onIntent(AndpubIntent.CreateApp(name, packageName))
                } else {
                    onIntent(AndpubIntent.UpdateApp(editingApp.id, name, packageName))
                }
                appDialogId = null
            },
        )
    }

    deletingApp?.let { app ->
        ConfirmDeleteDialog(
            title = "删除应用",
            message = "删除 ${app.name} 会同时删除它的渠道、产物草稿和发布任务。",
            onDismiss = { deletingAppId = null },
            onConfirm = {
                onIntent(AndpubIntent.DeleteApp(app.id))
                deletingAppId = null
            },
        )
    }

    if (settingsDialogVisible) {
        ToolSettingsDialog(
            state = state,
            onIntent = onIntent,
            onDismiss = { settingsDialogVisible = false },
        )
    }
}

@Composable
private fun AppListItem(
    name: String,
    packageName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(packageName, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) {
                Text("编辑")
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("删除")
            }
        }
    }
}

@Composable
private fun AppEditorDialog(
    appId: String?,
    initialName: String,
    initialPackageName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var appName by remember(appId) { mutableStateOf(initialName) }
    var packageName by remember(appId) { mutableStateOf(initialPackageName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (appId == null) "创建应用" else "编辑应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("应用名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("包名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(appName, packageName) }) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun StableFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        elevation = FilterChipDefaults.filterChipElevation(
            elevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AppDetail(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
) {
    val app = state.selectedApp
    var selectedTab by remember(app?.id) { mutableStateOf(AppDetailTab.Channels) }
    var showPublishProcess by remember(app?.id) { mutableStateOf(false) }
    if (app == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("先创建或选择一个应用")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.name, style = MaterialTheme.typography.headlineSmall)
                Text(app.packageName, style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            AppDetailTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) },
                )
            }
        }

        when (selectedTab) {
            AppDetailTab.Channels -> ChannelSection(state, onIntent)
            AppDetailTab.Publish -> {
                if (showPublishProcess) {
                    PublishProcessPage(
                        state = state,
                        onBack = { showPublishProcess = false },
                        onRefreshTaskStatus = { taskId ->
                            onIntent(AndpubIntent.RefreshPublishTaskStatus(taskId))
                        },
                        onRetryFailedTasks = {
                            onIntent(AndpubIntent.RetryFailedPublishTasks)
                        },
                    )
                } else {
                    PublishTargetSection(state, onIntent)
                    if (state.publishTargetChannels.isNotEmpty()) {
                        ArtifactSection(state, state.publishTargetChannels, onIntent)
                    }
                    PublishTaskSection(
                        state = state,
                        onCreate = {
                            showPublishProcess = true
                            onIntent(AndpubIntent.CreatePublishTasks)
                        },
                    )
                }
            }
        }
    }

}

@Composable
private fun ToolSettingsDialog(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    var androidSdkPath by remember(state.toolSettings) {
        mutableStateOf(state.toolSettings.androidSdkPath)
    }
    var bundletoolPath by remember(state.toolSettings) {
        mutableStateOf(state.toolSettings.bundletoolPath)
    }
    var weComWebhookUrl by remember(state.toolSettings) {
        mutableStateOf(state.toolSettings.weComWebhookUrl)
    }
    var toolMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本地工具设置") },
        text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("APK 解析使用 Android SDK build-tools/aapt2；AAB 解析使用 bundletool-all.jar。")
            OutlinedTextField(
                value = androidSdkPath,
                onValueChange = { androidSdkPath = it },
                label = { Text("Android SDK 路径，留空则读 ANDROID_HOME / ANDROID_SDK_ROOT") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bundletoolPath,
                onValueChange = { bundletoolPath = it },
                label = { Text("bundletool-all.jar 路径，用于解析 AAB") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = weComWebhookUrl,
                onValueChange = { weComWebhookUrl = it },
                label = { Text("企业微信机器人 WebHook 地址") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        toolMessages = inspectToolSettings(
                            androidSdkPath = androidSdkPath,
                            bundletoolPath = bundletoolPath,
                        ).messages
                    }
                },
            ) {
                Text("检测工具")
            }
            toolMessages.forEach { message ->
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
        },
        confirmButton = {
            Button(
                    onClick = {
                        val settings = ToolSettings(
                            androidSdkPath = androidSdkPath,
                            bundletoolPath = bundletoolPath,
                            weComWebhookUrl = weComWebhookUrl,
                        )
                        onIntent(AndpubIntent.UpdateToolSettings(settings))
                        scope.launch {
                            toolMessages = inspectToolSettings(
                                androidSdkPath = settings.androidSdkPath,
                                bundletoolPath = settings.bundletoolPath,
                            ).messages
                        }
                        onDismiss()
                    },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChannelSection(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
) {
    var channelDialogId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var deletingChannelId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var infoChannelId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var notifyAfterQuery by remember(state.selectedAppId) { mutableStateOf(false) }
    val expandedChannels = remember(state.selectedAppId) { mutableStateMapOf<String, Boolean>() }
    val editingChannel = state.selectedChannels.firstOrNull { it.id == channelDialogId }
    val deletingChannel = state.selectedChannels.firstOrNull { it.id == deletingChannelId }
    val infoChannel = state.selectedChannels.firstOrNull { it.id == infoChannelId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("渠道以独立记录保存，可按市场新增、编辑、删除，并查询市场侧应用信息。")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = notifyAfterQuery,
                        onCheckedChange = { notifyAfterQuery = it },
                    )
                    Text("通知")
                }
                OutlinedButton(onClick = { onIntent(AndpubIntent.SyncAllChannels(notifyAfterQuery)) }) {
                    Text("一键查询")
                }
                Button(onClick = { channelDialogId = NEW_CHANNEL_DIALOG_ID }) {
                    Text("新增渠道")
                }
            }
        }

        if (state.selectedChannels.isEmpty()) {
            Text("暂无渠道")
        }

        state.selectedChannels.forEach { channel ->
            ChannelSummaryCard(
                channel = channel,
                expanded = expandedChannels[channel.id] == true,
                onToggleExpanded = {
                    expandedChannels[channel.id] = expandedChannels[channel.id] != true
                },
                onEdit = { channelDialogId = channel.id },
                onDelete = { deletingChannelId = channel.id },
                onSync = {
                    infoChannelId = channel.id
                    onIntent(AndpubIntent.SyncChannel(channel))
                },
            )
        }
    }

    if (channelDialogId != null) {
        ChannelEditorDialog(
            state = state,
            channel = editingChannel,
            onDismiss = { channelDialogId = null },
            onSave = { channelId, name, marketType, marketAppId, credentials, extraFields ->
                onIntent(
                    AndpubIntent.AddOrUpdateChannel(
                        channelId = channelId,
                        name = name,
                        marketType = marketType,
                        marketAppId = marketAppId,
                        credentials = credentials,
                        extraFields = extraFields,
                    )
                )
                channelDialogId = null
            },
            onTest = { testKey, marketType, marketAppId, credentials, extraFields ->
                onIntent(
                    AndpubIntent.TestChannelConfig(
                        testKey = testKey,
                        marketType = marketType,
                        marketAppId = marketAppId,
                        credentials = credentials,
                        extraFields = extraFields,
                    )
                )
            },
        )
    }

    deletingChannel?.let { channel ->
        ConfirmDeleteDialog(
            title = "删除渠道",
            message = "确认删除 ${channel.displayTitle()}？相关发布任务和渠道产物草稿也会删除。",
            onDismiss = { deletingChannelId = null },
            onConfirm = {
                onIntent(AndpubIntent.DeleteChannel(channel.id))
                deletingChannelId = null
            },
        )
    }

    infoChannel?.let { channel ->
        ChannelInfoDialog(
            channel = channel,
            onDismiss = { infoChannelId = null },
        )
    }
}

@Composable
private fun PublishTargetSection(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
) {
    val targetIds = state.publishTargetChannels.map { it.id }.toSet()
    val expandedVivoOptions = remember(state.selectedAppId) { mutableStateMapOf<String, Boolean>() }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("发布渠道", style = MaterialTheme.typography.titleMedium)
            if (state.selectedChannels.isEmpty()) {
                Text("暂无渠道，请先在渠道管理中新增渠道。")
                return@Column
            }
            state.selectedChannels.forEach { channel ->
                val selected = channel.id in targetIds
                val showVivoOptions = selected && channel.marketType == MarketType.Vivo
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                if (!it) {
                                    expandedVivoOptions.remove(channel.id)
                                }
                                onIntent(AndpubIntent.TogglePublishChannel(channel.id, it))
                            },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(channel.displayTitle(), style = MaterialTheme.typography.bodyLarge)
                            val details = if (channel.marketType == MarketType.Vivo) {
                                "${channel.marketType.displayName} / ${channel.vivoEnvironment().displayName}"
                            } else {
                                channel.marketType.displayName
                            }
                            Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                        if (showVivoOptions) {
                            TextButton(
                                onClick = {
                                    expandedVivoOptions[channel.id] = expandedVivoOptions[channel.id] != true
                                },
                            ) {
                                Text(if (expandedVivoOptions[channel.id] == true) "收起选项" else "发布选项")
                            }
                        }
                    }
                    if (showVivoOptions && expandedVivoOptions[channel.id] == true) {
                        VivoPublishOptionSection(
                            state = state,
                            channel = channel,
                            onIntent = onIntent,
                        )
                    }
                }
            }
            if (targetIds.isEmpty()) {
                Text("勾选渠道后才会显示该渠道的发布配置。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VivoPublishOptionSection(
    state: AndpubUiState,
    channel: ChannelRecord,
    onIntent: (AndpubIntent) -> Unit,
) {
    val options = state.snapshot.vivoPublishOptions
    val isProduction = channel.vivoEnvironment() == VivoApiEnvironment.Production
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("vivo 发布选项", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VivoApiEnvironment.entries.forEach { item ->
                StableFilterChip(
                    selected = channel.vivoEnvironment() == item,
                    onClick = {
                        onIntent(
                            AndpubIntent.AddOrUpdateChannel(
                                channelId = channel.id,
                                name = channel.name,
                                marketType = channel.marketType,
                                marketAppId = channel.marketAppId,
                                credentials = channel.credentials,
                                extraFields = channel.extraFields.withVivoEnvironment(item),
                            )
                        )
                    },
                    label = { Text(item.displayName) },
                )
            }
        }
        Text(
            text = "vivo 会先确认目标环境中的应用已存在，再上传文件并提交更新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VivoOnlineType.entries.forEach { item ->
                StableFilterChip(
                    selected = options.onlineType == item,
                    onClick = {
                        onIntent(AndpubIntent.UpdateVivoPublishOptions(options.copy(onlineType = item)))
                    },
                    label = { Text(item.displayName) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VivoCompatibleDevice.entries.forEach { item ->
                StableFilterChip(
                    selected = options.compatibleDevice == item,
                    onClick = {
                        onIntent(AndpubIntent.UpdateVivoPublishOptions(options.copy(compatibleDevice = item)))
                    },
                    label = { Text(item.displayName) },
                )
            }
        }
        if (isProduction) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.vivoProductionConfirmed,
                    onCheckedChange = {
                        onIntent(AndpubIntent.UpdateVivoProductionConfirmed(it))
                    },
                )
                Text("确认向 vivo 正式环境提交更新", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PublishTaskSection(
    state: AndpubUiState,
    onCreate: () -> Unit,
) {
    val app = state.selectedApp ?: return
    val tasks = state.publishTasks.filter { it.appId == app.id }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("勾选渠道并填写产物后点击「开始发布」，所有选中市场并行提交。")
            }
            Button(
                enabled = state.publishTargetChannels.isNotEmpty() && !state.isCreatingPublishTasks,
                onClick = onCreate,
            ) {
                Text(if (state.isCreatingPublishTasks) "发布中..." else "开始发布")
            }
        }

        if (tasks.isEmpty()) {
            Text("暂无任务")
            return@Column
        }

        tasks.forEach { task ->
            PublishTaskCard(task)
        }
    }
}

@Composable
private fun PublishProcessPage(
    state: AndpubUiState,
    onBack: () -> Unit,
    onRefreshTaskStatus: (String) -> Unit,
    onRetryFailedTasks: () -> Unit,
) {
    val app = state.selectedApp ?: return
    val tasks = state.publishTasks.filter { it.appId == app.id }
    var showDetailLogs by remember(app.id) { mutableStateOf(false) }

    val allTerminal = tasks.isNotEmpty() && !state.isCreatingPublishTasks &&
            tasks.all { it.status.isTerminal() }
    val hasFailedTasks = tasks.any { it.status == PublishTaskStatus.Failed }
    val successCount = tasks.count { it.status == PublishTaskStatus.Submitted || it.status == PublishTaskStatus.Accepted }
    val failedCount = tasks.count { it.status == PublishTaskStatus.Failed }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("返回配置")
                }
                Text("发布过程", style = MaterialTheme.typography.titleLarge)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("详细日志")
                Switch(
                    checked = showDetailLogs,
                    onCheckedChange = { showDetailLogs = it },
                )
            }
        }

        if (allTerminal) {
            val summaryText = buildString {
                if (successCount > 0) append("${successCount} 个市场已提交")
                if (failedCount > 0) {
                    if (successCount > 0) append("，")
                    append("${failedCount} 个市场失败")
                }
            }
            if (summaryText.isNotBlank()) {
                Text(
                    summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failedCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            if (hasFailedTasks) {
                Button(
                    onClick = onRetryFailedTasks,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("重试失败任务（${failedCount} 个）")
                }
            }
        }

        if (state.isCreatingPublishTasks && tasks.isEmpty()) {
            Text("正在准备并检查产物...")
            return@Column
        }

        if (tasks.isEmpty()) {
            Text("暂无发布任务")
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 960.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(tasks, key = { it.id }) { task ->
                PublishProcessCard(
                    task = task,
                    showDetailLogs = showDetailLogs,
                    canRefreshStatus = task.canRefreshPublishStatus(),
                    onRefreshStatus = { onRefreshTaskStatus(task.id) },
                )
            }
        }
    }
}

@Composable
private fun PublishProcessCard(
    task: PublishTaskRecord,
    showDetailLogs: Boolean,
    canRefreshStatus: Boolean,
    onRefreshStatus: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(task.marketType.displayName, style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(task.status.displayName, style = MaterialTheme.typography.labelLarge)
                    if (canRefreshStatus) {
                        TextButton(onClick = onRefreshStatus) {
                            Text("刷新状态")
                        }
                    }
                }
            }
            task.publishEnvironment?.let {
                Text("目标环境：$it", style = MaterialTheme.typography.bodySmall)
            }

            if (showDetailLogs) {
                PublishProgressRows(task.latestProgressLogs(PublishTaskStage.Download, PublishTaskStage.Upload))
                task.logs.forEach { log ->
                    val stage = log.stage?.let { "[${it.displayName}] " }.orEmpty()
                    Text(
                        "${log.level.name}: $stage${log.message}",
                        color = log.level.color(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                PublishProcessSection(
                    title = "下载相关信息",
                    lines = task.downloadSummary(),
                    progressLogs = task.latestProgressLogs(PublishTaskStage.Download),
                )
                PublishProcessSection("校验相关信息", task.validationSummary())
                PublishProcessSection(
                    title = "发布相关信息",
                    lines = task.publishSummary(),
                    progressLogs = task.latestProgressLogs(PublishTaskStage.Upload),
                )
            }
        }
    }
}

private fun PublishTaskRecord.canRefreshPublishStatus(): Boolean {
    val capability = MarketDefinitions.schemaOf(marketType).capability
    return capability.supportsPublishStatusQuery &&
            (status == PublishTaskStatus.Submitted || status == PublishTaskStatus.Accepted)
}

@Composable
private fun PublishProcessSection(
    title: String,
    lines: List<String>,
    progressLogs: List<PublishTaskLog> = emptyList(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        lines.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        PublishProgressRows(progressLogs)
    }
}

@Composable
private fun PublishProgressRows(logs: List<PublishTaskLog>) {
    logs.forEach { log ->
        val percent = log.progressPercent ?: return@forEach
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(log.progressLabel ?: log.message, style = MaterialTheme.typography.bodySmall)
                Text("$percent%", style = MaterialTheme.typography.labelSmall)
            }
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(log.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PublishTaskCard(
    task: PublishTaskRecord,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(task.marketType.displayName, style = MaterialTheme.typography.titleMedium)
                Text(task.status.displayName, style = MaterialTheme.typography.labelLarge)
            }
            Text("${task.publishMode.displayName} / ${task.artifact.sourceType.displayName} / ${task.artifact.packageType.displayName}")
            if (task.artifact.value.isNotBlank()) {
                Text(task.artifact.value, style = MaterialTheme.typography.bodySmall)
            }
            if (task.artifact.packageType == PackageType.SplitApk) {
                ArtifactInspectionSummary(task.artifact)
            } else if (task.artifact.message != null) {
                ArtifactInspectionSummary(task.artifact)
            }
            task.logs.forEach { log ->
                val color = when (log.level) {
                    LogLevel.Info -> MaterialTheme.colorScheme.onSurface
                    LogLevel.Warning -> MaterialTheme.colorScheme.primary
                    LogLevel.Error -> MaterialTheme.colorScheme.error
                }
                Text(
                    "${log.level.name}: ${log.message}",
                    color = color,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ChannelSummaryCard(
    channel: ChannelRecord,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    Text(
                        "${channel.marketType.displayName} · ${channel.syncStatus.displayName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    channel.appInfo?.let {
                        Text("线上版本：${it.onlineVersion ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text("正在审核版本：${it.reviewingVersion ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text("审核状态：${it.auditStatus ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    }
                    channel.lastError?.let {
                        Text(
                            "错误：$it",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSync) {
                        Text("获取应用信息")
                    }
                    TextButton(onClick = onToggleExpanded) {
                        Text(if (expanded) "收起" else "展开")
                    }
                    TextButton(onClick = onEdit) {
                        Text("编辑")
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("删除")
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
private fun ChannelExpandedContent(
    channel: ChannelRecord,
) {
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
        CapabilityText(schema.capability)
    }
}

@Composable
private fun ChannelEditorDialog(
    state: AndpubUiState,
    channel: ChannelRecord?,
    onDismiss: () -> Unit,
    onSave: (String?, String, MarketType, String?, Map<String, String>, Map<String, String>) -> Unit,
    onTest: (String, MarketType, String?, Map<String, String>, Map<String, String>) -> Unit,
) {
    var marketType by remember(channel?.id) {
        mutableStateOf(
            channel?.marketType ?: MarketType.Huawei
        )
    }
    var huaweiAuthMode by remember(channel?.id) {
        mutableStateOf(channel?.credentials?.huaweiAuthMode() ?: HuaweiAuthMode.ApiClient)
    }
    var vivoEnvironment by remember(channel?.id) {
        mutableStateOf(channel?.vivoEnvironment() ?: VivoApiEnvironment.Production)
    }
    var serviceAccountInputMode by remember(channel?.id) {
        mutableStateOf(
            if (channel?.credentials?.get(HuaweiCredentialKeys.ServiceAccountJson).orEmpty().isNotBlank()) {
                HuaweiServiceAccountInputMode.Json
            } else {
                HuaweiServiceAccountInputMode.SplitFields
            }
        )
    }
    val schema = MarketDefinitions.schemaOf(marketType)
    val credentials = remember(marketType, channel?.id) {
        mutableStateMapOf<String, String>().apply {
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
    var channelName by remember(channel?.id) { mutableStateOf(channel?.name.orEmpty()) }
    var marketAppId by remember(marketType, channel?.id) {
        mutableStateOf(channel?.marketAppId.orEmpty())
    }
    val testKey = channel?.id ?: "new-${marketType.name}-${if (marketType == MarketType.Huawei) huaweiAuthMode.name else "default"}"
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
                CapabilityText(schema.capability)

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

                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("渠道名称，可选") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
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
                    ConnectionTestResult(
                        info = testState.info,
                        error = testState.error,
                    )
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
                        credentials.toChannelCredentials(marketType, schema.credentialFields, huaweiAuthMode, serviceAccountInputMode),
                        channel.extraFieldsFor(marketType, vivoEnvironment),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onTest(
                            testKey,
                            marketType,
                            marketAppId,
                            credentials.toChannelCredentials(marketType, schema.credentialFields, huaweiAuthMode, serviceAccountInputMode),
                            channel.extraFieldsFor(marketType, vivoEnvironment),
                        )
                    },
                ) {
                    Text(if (testState?.isLoading == true) "测试中" else "测试连接")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
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
            text = "测试环境和正式环境的 access_key / access_secret 彼此独立，不能混用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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

@Composable
private fun HuaweiCredentialFields(
    authMode: HuaweiAuthMode,
    onAuthModeChange: (HuaweiAuthMode) -> Unit,
    serviceAccountInputMode: HuaweiServiceAccountInputMode,
    onServiceAccountInputModeChange: (HuaweiServiceAccountInputMode) -> Unit,
    credentials: MutableMap<String, String>,
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
                                FieldSchema(
                                    HuaweiCredentialKeys.PrivateKey,
                                    "private_key",
                                    kind = FieldKind.Multiline,
                                ),
                                FieldSchema(HuaweiCredentialKeys.SubAccount, "sub_account"),
                                FieldSchema(HuaweiCredentialKeys.TokenUri, "token_uri", kind = FieldKind.Url),
                            ).forEach { field ->
                                SchemaTextField(
                                    field = field,
                                    value = credentials[field.key].orEmpty(),
                                    onValueChange = { credentials[field.key] = it },
                                    modifier = Modifier.width(if (field.kind == FieldKind.Multiline) 532.dp else 260.dp),
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

private fun Map<String, String>.toChannelCredentials(
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
        HuaweiAuthMode.ServiceAccount -> {
            when (serviceAccountInputMode) {
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

@Composable
private fun ConnectionTestResult(
    info: MarketAppInfo?,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("测试连接结果", style = MaterialTheme.typography.titleSmall)
        if (info != null) {
            Text("市场应用 ID：${info.marketAppId}")
            Text("应用名：${info.appName}")
            Text("包名：${info.packageName}")
            Text("线上版本：${info.onlineVersion ?: "-"}")
            Text("正在审核版本：${info.reviewingVersion ?: "-"}")
            Text("审核状态：${info.auditStatus ?: "-"}")
        }
        error?.let {
            Text("错误：$it", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SchemaTextField(
    field: FieldSchema,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (field.required) "${field.label} *" else field.label) },
        singleLine = field.kind != FieldKind.Multiline,
        minLines = if (field.kind == FieldKind.Multiline) 4 else 1,
        maxLines = if (field.kind == FieldKind.Multiline) 4 else 1,
        visualTransformation = if (field.kind == FieldKind.Password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = modifier,
    )
}

@Composable
private fun CapabilityText(
    capability: MarketCapability,
) {
    val text = buildList {
        if (capability.supportsUnifiedApk) add("APK")
        if (capability.supportsSplitApk) add("32/64")
        if (capability.supportsAab) add("AAB")
        if (capability.supportsUserUrl) add("URL")
        if (capability.supportsAppInfoQuery) add("应用详情")
    }.joinToString(" / ")
    Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChannelInfoDialog(
    channel: ChannelRecord,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${channel.displayTitle()} 应用信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("同步状态：${channel.syncStatus.displayName}")
                if (channel.syncStatus == ChannelSyncStatus.Syncing) {
                    Text("正在获取市场侧应用信息...")
                }
                if (channel.syncStatus != ChannelSyncStatus.Syncing) {
                    channel.appInfo?.let { info ->
                        MarketAppInfoContent(info)
                    }
                }
                channel.lastError?.let {
                    Text("错误：$it", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun MarketAppInfoContent(
    info: MarketAppInfo,
) {
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

@Composable
private fun ArtifactSection(
    state: AndpubUiState,
    targetChannels: List<ChannelRecord>,
    onIntent: (AndpubIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PublishMode.entries.forEach { mode ->
                StableFilterChip(
                    selected = state.publishMode == mode,
                    onClick = { onIntent(AndpubIntent.UpdatePublishMode(mode)) },
                    label = { Text(mode.displayName) },
                )
            }
        }

        if (state.publishMode == PublishMode.UnifiedArtifact) {
            val selectedCapabilities = targetChannels
                .map { MarketDefinitions.schemaOf(it.marketType).capability }
            val urlFallbackMarkets = targetChannels
                .filter { !MarketDefinitions.schemaOf(it.marketType).capability.supportsUserUrl }
                .joinToString("、") { it.marketType.displayName }
            ArtifactEditor(
                title = "统一产物",
                draft = state.unifiedArtifactDraft,
                toolSettings = state.toolSettings,
                allowedPackageTypes = selectedCapabilities.allowedPackageTypes(),
                allowUrl = true,
                urlFallbackMessage = urlFallbackMarkets.takeIf { it.isNotBlank() }?.let {
                    "$it 不支持 URL 直传，创建发布任务时会先下载到本地再按文件上传。"
                },
                onDraftChange = { onIntent(AndpubIntent.UpdateUnifiedArtifact(it)) },
                onPickFile = { path, inspectionResult ->
                    inspectionResult.fold(
                        onSuccess = { onIntent(AndpubIntent.ApplyInspectionToUnified(path, it)) },
                        onFailure = {
                            onIntent(
                                AndpubIntent.ApplyArtifactErrorToUnified(
                                    path,
                                    it
                                )
                            )
                        },
                    )
                },
                onPickSplitFile = { slot, path, inspectionResult ->
                    inspectionResult.fold(
                        onSuccess = {
                            onIntent(
                                AndpubIntent.ApplySplitInspectionToUnified(
                                    slot,
                                    path,
                                    it
                                )
                            )
                        },
                        onFailure = {
                            onIntent(
                                AndpubIntent.ApplySplitArtifactErrorToUnified(
                                    slot,
                                    path,
                                    it
                                )
                            )
                        },
                    )
                },
            )
        } else {
            val channels = targetChannels
            if (channels.isEmpty()) {
                Text("先勾选要发布的渠道，再为每个渠道配置独立产物。")
            }
            channels.forEach { channel ->
                val capability = MarketDefinitions.schemaOf(channel.marketType).capability
                ArtifactEditor(
                    title = channel.displayTitle(),
                    draft = state.artifactDrafts[channel.id] ?: ArtifactDraft(),
                    toolSettings = state.toolSettings,
                    allowedPackageTypes = listOf(capability).allowedPackageTypes(),
                    allowUrl = true,
                    urlFallbackMessage = if (capability.supportsUserUrl) {
                        null
                    } else {
                        "${channel.marketType.displayName} 不支持 URL 直传，创建发布任务时会先下载到本地再按文件上传。"
                    },
                    onDraftChange = {
                        onIntent(
                            AndpubIntent.UpdateChannelArtifact(
                                channel.id,
                                it
                            )
                        )
                    },
                    onPickFile = { path, inspectionResult ->
                        inspectionResult.fold(
                            onSuccess = {
                                onIntent(
                                    AndpubIntent.ApplyInspectionToChannel(
                                        channel.id,
                                        path,
                                        it
                                    )
                                )
                            },
                            onFailure = {
                                onIntent(
                                    AndpubIntent.ApplyArtifactErrorToChannel(
                                        channel.id,
                                        path,
                                        it
                                    )
                                )
                            },
                        )
                    },
                    onPickSplitFile = { slot, path, inspectionResult ->
                        inspectionResult.fold(
                            onSuccess = {
                                onIntent(
                                    AndpubIntent.ApplySplitInspectionToChannel(
                                        channel.id,
                                        slot,
                                        path,
                                        it
                                    )
                                )
                            },
                            onFailure = {
                                onIntent(
                                    AndpubIntent.ApplySplitArtifactErrorToChannel(
                                        channel.id,
                                        slot,
                                        path,
                                        it
                                    )
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ArtifactEditor(
    title: String,
    draft: ArtifactDraft,
    toolSettings: ToolSettings,
    allowedPackageTypes: Set<PackageType>,
    allowUrl: Boolean,
    urlFallbackMessage: String?,
    onDraftChange: (ArtifactDraft) -> Unit,
    onPickFile: (String, Result<io.github.loshine.andpub.domain.model.ArtifactInspection>) -> Unit,
    onPickSplitFile: (
        SplitApkSlot,
        String,
        Result<io.github.loshine.andpub.domain.model.ArtifactInspection>,
    ) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selectedPackageType = draft.packageType.takeIf { it in allowedPackageTypes }
        ?: allowedPackageTypes.first()
    val effectiveDraft = draft.copy(
        sourceType = if (draft.sourceType == ArtifactSourceType.Url && !allowUrl) {
            ArtifactSourceType.LocalFile
        } else {
            draft.sourceType
        },
        packageType = selectedPackageType,
    )

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sourceTypes = ArtifactSourceType.entries
                    .filter { it == ArtifactSourceType.LocalFile || allowUrl }
                sourceTypes
                    .forEach { type ->
                        StableFilterChip(
                            selected = effectiveDraft.sourceType == type,
                            onClick = {
                                onDraftChange(
                                    effectiveDraft.copy(sourceType = type).withoutInspection()
                                )
                            },
                            label = { Text(type.displayName) },
                        )
                    }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackageType.entries.filter { it in allowedPackageTypes }.forEach { type ->
                    StableFilterChip(
                        selected = effectiveDraft.packageType == type,
                        onClick = {
                            onDraftChange(
                                effectiveDraft.copy(
                                    packageType = type,
                                    sourceType = effectiveDraft.sourceType,
                                )
                            )
                        },
                        label = { Text(type.displayName) },
                    )
                }
            }
            urlFallbackMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (effectiveDraft.packageType == PackageType.SplitApk) {
                SplitArtifactFileRow(
                    slot = SplitApkSlot.Arm32,
                    part = effectiveDraft.split32,
                    sourceType = effectiveDraft.sourceType,
                    toolSettings = toolSettings,
                    onPartChange = { onDraftChange(effectiveDraft.copy(split32 = it)) },
                    onPickFile = onPickSplitFile,
                )
                SplitArtifactFileRow(
                    slot = SplitApkSlot.Arm64,
                    part = effectiveDraft.split64,
                    sourceType = effectiveDraft.sourceType,
                    toolSettings = toolSettings,
                    onPartChange = { onDraftChange(effectiveDraft.copy(split64 = it)) },
                    onPickFile = onPickSplitFile,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = effectiveDraft.value,
                        onValueChange = { onDraftChange(effectiveDraft.withEditedValue(it)) },
                        label = { Text(if (effectiveDraft.sourceType == ArtifactSourceType.Url) "URL" else "本地文件路径") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (effectiveDraft.sourceType == ArtifactSourceType.LocalFile) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val path = pickArtifactFilePath() ?: return@launch
                                    onPickFile(
                                        path,
                                        inspectLocalArtifact(
                                            path = path,
                                            androidSdkPath = toolSettings.androidSdkPath,
                                            bundletoolPath = toolSettings.bundletoolPath,
                                        )
                                    )
                                }
                            },
                        ) {
                            Text("选择")
                        }
                    }
                }

                if (effectiveDraft.sourceType == ArtifactSourceType.Url) {
                    UrlHashFields(effectiveDraft, onDraftChange)
                }
                ArtifactInspectionSummary(effectiveDraft)
            }
        }
    }
}

@Composable
private fun SplitArtifactFileRow(
    slot: SplitApkSlot,
    part: ArtifactPart,
    sourceType: ArtifactSourceType,
    toolSettings: ToolSettings,
    onPartChange: (ArtifactPart) -> Unit,
    onPickFile: (
        SplitApkSlot,
        String,
        Result<io.github.loshine.andpub.domain.model.ArtifactInspection>,
    ) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = part.value,
                onValueChange = { onPartChange(part.withEditedValue(it)) },
                label = { Text(if (sourceType == ArtifactSourceType.Url) "${slot.displayName} URL" else "${slot.displayName} 文件路径") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (sourceType == ArtifactSourceType.LocalFile) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val path = pickArtifactFilePath() ?: return@launch
                            onPickFile(
                                slot,
                                path,
                                inspectLocalArtifact(
                                    path = path,
                                    androidSdkPath = toolSettings.androidSdkPath,
                                    bundletoolPath = toolSettings.bundletoolPath,
                                )
                            )
                        }
                    },
                ) {
                    Text("选择")
                }
            }
        }
        ArtifactPartSummary(slot.displayName, part)
    }
}

private fun List<MarketCapability>.allowedPackageTypes(): Set<PackageType> {
    if (isEmpty()) return PackageType.entries.toSet()

    val candidates = PackageType.entries.toSet()
    return filterSupportedPackageTypes(candidates)
}

private fun ChannelRecord.displayTitle(): String =
    name.takeIf { it.isNotBlank() } ?: marketType.displayName

private const val NEW_APP_DIALOG_ID = "new-app"
private const val NEW_CHANNEL_DIALOG_ID = "new-channel"

private fun List<MarketCapability>.filterSupportedPackageTypes(
    candidates: Set<PackageType>,
): Set<PackageType> =
    candidates.filterTo(mutableSetOf()) { packageType ->
        all { capability ->
            when (packageType) {
                PackageType.Apk -> capability.supportsUnifiedApk
                PackageType.SplitApk -> capability.supportsSplitApk
                PackageType.Aab -> capability.supportsAab
            }
        }
    }.ifEmpty { setOf(PackageType.Apk) }

@Composable
private fun UrlHashFields(
    draft: ArtifactDraft,
    onDraftChange: (ArtifactDraft) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.md5,
            onValueChange = { onDraftChange(draft.copy(md5 = it)) },
            label = { Text("MD5") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.sha256,
            onValueChange = { onDraftChange(draft.copy(sha256 = it)) },
            label = { Text("SHA-256") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ArtifactInspectionSummary(
    draft: ArtifactDraft,
) {
    if (draft.packageType == PackageType.SplitApk) {
        ArtifactPartSummary(SplitApkSlot.Arm32.displayName, draft.split32)
        ArtifactPartSummary(SplitApkSlot.Arm64.displayName, draft.split64)
        return
    }

    if (draft.value.isBlank() && draft.message == null) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (draft.downloadedPath.isNotBlank()) Text(
            "下载到本地：${draft.downloadedPath}",
            style = MaterialTheme.typography.bodySmall
        )
        draft.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (draft.md5.isNotBlank()) Text(
            "MD5：${draft.md5}",
            style = MaterialTheme.typography.bodySmall
        )
        if (draft.sha1.isNotBlank()) Text(
            "SHA-1：${draft.sha1}",
            style = MaterialTheme.typography.bodySmall
        )
        if (draft.sha256.isNotBlank()) Text(
            "SHA-256：${draft.sha256}",
            style = MaterialTheme.typography.bodySmall
        )
        draft.packageName?.let { Text("包名：$it", style = MaterialTheme.typography.bodySmall) }
        draft.versionName?.let { Text("版本名：$it", style = MaterialTheme.typography.bodySmall) }
        draft.versionCode?.let { Text("版本号：$it", style = MaterialTheme.typography.bodySmall) }
        if (draft.abiList.isNotEmpty()) {
            Text("ABI：${draft.abiList.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ArtifactPartSummary(
    label: String,
    part: ArtifactPart,
) {
    if (part.value.isBlank() && part.message == null) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (part.value.isNotBlank()) Text(part.value, style = MaterialTheme.typography.bodySmall)
        if (part.downloadedPath.isNotBlank()) Text(
            "下载到本地：${part.downloadedPath}",
            style = MaterialTheme.typography.bodySmall
        )
        part.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (part.md5.isNotBlank()) Text(
            "MD5：${part.md5}",
            style = MaterialTheme.typography.bodySmall
        )
        if (part.sha1.isNotBlank()) Text(
            "SHA-1：${part.sha1}",
            style = MaterialTheme.typography.bodySmall
        )
        if (part.sha256.isNotBlank()) Text(
            "SHA-256：${part.sha256}",
            style = MaterialTheme.typography.bodySmall
        )
        part.packageName?.let { Text("包名：$it", style = MaterialTheme.typography.bodySmall) }
        part.versionName?.let { Text("版本名：$it", style = MaterialTheme.typography.bodySmall) }
        part.versionCode?.let { Text("版本号：$it", style = MaterialTheme.typography.bodySmall) }
        if (part.abiList.isNotEmpty()) {
            Text("ABI：${part.abiList.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(4.dp))
}

private fun ArtifactDraft.withEditedValue(value: String): ArtifactDraft =
    copy(value = value).withoutInspection()

private fun ArtifactDraft.withoutInspection(): ArtifactDraft =
    copy(
        downloadedPath = "",
        md5 = "",
        sha1 = "",
        sha256 = "",
        packageName = null,
        versionName = null,
        versionCode = null,
        abiList = emptyList(),
        message = null,
    )

private fun ArtifactPart.withEditedValue(value: String): ArtifactPart =
    copy(
        value = value,
        downloadedPath = "",
        md5 = "",
        sha1 = "",
        sha256 = "",
        packageName = null,
        versionName = null,
        versionCode = null,
        abiList = emptyList(),
        message = null,
    )

private fun PublishTaskRecord.downloadSummary(): List<String> =
    if (artifact.packageType == PackageType.SplitApk) {
        listOf(
            "32 位来源：${artifact.split32.value.ifBlank { "-" }}",
            "32 位本地：${artifact.split32.downloadedPath.ifBlank { "-" }}",
            "64 位来源：${artifact.split64.value.ifBlank { "-" }}",
            "64 位本地：${artifact.split64.downloadedPath.ifBlank { "-" }}",
        ) + latestStageMessages(PublishTaskStage.Download)
    } else {
        listOf(
            "来源：${artifact.value.ifBlank { "-" }}",
            "本地：${artifact.downloadedPath.ifBlank { "-" }}",
        ) + latestStageMessages(PublishTaskStage.Download)
    }

private fun PublishTaskRecord.validationSummary(): List<String> {
    val base = if (artifact.packageType == PackageType.SplitApk) {
        listOf(
            "32 位包名：${artifact.split32.packageName ?: "-"}",
            "32 位版本：${artifact.split32.versionName ?: "-"}",
            "64 位包名：${artifact.split64.packageName ?: "-"}",
            "64 位版本：${artifact.split64.versionName ?: "-"}",
        )
    } else {
        listOf(
            "包名：${artifact.packageName ?: "-"}",
            "版本：${artifact.versionName ?: "-"}",
            "versionCode：${artifact.versionCode?.toString() ?: "-"}",
        )
    }
    val errors = logs.filter { it.level == LogLevel.Error }.map { "错误：${it.message}" }
    return base + errors
}

private fun PublishTaskRecord.publishSummary(): List<String> =
    when (status) {
        PublishTaskStatus.Failed -> listOf("失败：${logs.lastOrNull { it.level == LogLevel.Error }?.message ?: "发布失败"}")
        PublishTaskStatus.Ready -> listOf(
            if (marketType == MarketType.Vivo) {
                "待提交：尚未调用 vivo 上传接口"
            } else {
                "仅完成校验：当前市场暂未接入厂商发布"
            }
        )
        PublishTaskStatus.Created -> listOf("已创建：等待检查")
        PublishTaskStatus.Validating -> listOf("检查中：正在准备产物")
        PublishTaskStatus.Uploading -> listOf("上传中：正在调用厂商上传接口") + latestStageMessages(PublishTaskStage.Upload, PublishTaskStage.Submit)
        PublishTaskStatus.Submitted -> buildList {
            add("已提交：等待厂商处理")
            vendorTaskId?.let { add("任务 ID：$it") }
            if (vendorUploadIds.isNotEmpty()) add("上传流水号：${vendorUploadIds.joinToString()}")
        }
        PublishTaskStatus.Accepted -> buildList {
            add("已受理：厂商接口已返回成功")
            vendorTaskId?.let { add("任务 ID：$it") }
            if (vendorUploadIds.isNotEmpty()) add("上传流水号：${vendorUploadIds.joinToString()}")
        }
    }

private fun PublishTaskRecord.latestProgressLogs(
    vararg stages: PublishTaskStage,
): List<PublishTaskLog> {
    if (status !in listOf(PublishTaskStatus.Validating, PublishTaskStatus.Uploading)) {
        return emptyList()
    }
    return logs
        .filter { log ->
            log.stage in stages &&
                    log.progressPercent != null &&
                    (log.progressKey != null || log.progressLabel != null)
        }
        .groupBy { it.progressKey ?: "${it.stage}:${it.progressLabel}" }
        .values
        .mapNotNull { it.lastOrNull() }
        .sortedBy { it.progressSortOrder() }
}

private fun PublishTaskLog.progressSortOrder(): Int =
    when (progressKey) {
        "download:bundle" -> 10
        "download:apk:universal" -> 20
        "download:apk:32" -> 30
        "download:apk:64" -> 40
        "upload:apk:universal" -> 50
        "upload:apk:32" -> 60
        "upload:apk:64" -> 70
        else -> Int.MAX_VALUE
    }

private fun PublishTaskRecord.latestStageMessages(
    vararg stages: PublishTaskStage,
): List<String> =
    logs.asReversed()
        .filter { it.stage in stages && it.progressPercent == null }
        .distinctBy { it.message }
        .take(2)
        .map { it.message }
        .asReversed()

@Composable
private fun LogLevel.color() =
    when (this) {
        LogLevel.Info -> MaterialTheme.colorScheme.onSurface
        LogLevel.Warning -> MaterialTheme.colorScheme.primary
        LogLevel.Error -> MaterialTheme.colorScheme.error
    }
