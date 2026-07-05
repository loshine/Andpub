package io.github.loshine.andpub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.VivoApiEnvironment
import io.github.loshine.andpub.domain.model.vivoEnvironment
import io.github.loshine.andpub.presentation.AndpubIntent
import io.github.loshine.andpub.presentation.AndpubUiState
import io.github.loshine.andpub.ui.components.ArtifactSection
import io.github.loshine.andpub.ui.components.ChannelInfoDialog
import io.github.loshine.andpub.ui.components.ChannelSummaryCard
import io.github.loshine.andpub.ui.components.ChannelEditorDialog
import io.github.loshine.andpub.ui.components.EmptyState
import io.github.loshine.andpub.ui.components.StableFilterChip
import io.github.loshine.andpub.ui.components.VivoPublishOptionSection
import io.github.loshine.andpub.ui.components.displayTitle
import io.github.loshine.andpub.ui.components.toChannelCredentials
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults

private enum class AppDetailTab(val title: String) {
    Channels("渠道管理"),
    Publish("发布"),
}

internal const val NEW_CHANNEL_DIALOG_ID = "new-channel"

// ─── Root detail screen ────────────────────────────────────────────────────────

/**
 * App detail for both compact (with back button) and expanded (no back button) layouts.
 * [onBack] is null when running inside the expanded two-pane layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailScreen(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
    onBack: (() -> Unit)?,
) {
    val app = state.selectedApp

    if (app == null) {
        EmptyState(
            icon = Icons.Outlined.Add,
            title = "尚未选择应用",
            description = "从左侧列表选择一个应用，或新建一个",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    var selectedTab by rememberSaveable(app.id) { mutableStateOf(AppDetailTab.Channels) }
    var showPublishProcess by rememberSaveable(app.id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(app.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = {
                    state.message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                AppDetailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) },
                    )
                }
            }

            when (selectedTab) {
                AppDetailTab.Channels ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ChannelSection(state = state, onIntent = onIntent)
                    }
                AppDetailTab.Publish ->
                    if (showPublishProcess) {
                        PublishProcessPage(
                            state = state,
                            onBack = { showPublishProcess = false },
                            onRefreshTaskStatus = { onIntent(AndpubIntent.RefreshPublishTaskStatus(it)) },
                            onRetryFailedTasks = { onIntent(AndpubIntent.RetryFailedPublishTasks) },
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            PublishTargetSection(state = state, onIntent = onIntent)
                            if (state.publishTargetChannels.isNotEmpty()) {
                                ArtifactSection(
                                    state = state,
                                    targetChannels = state.publishTargetChannels,
                                    onIntent = onIntent,
                                )
                            }
                            PublishStartSection(
                                state = state,
                                onCreate = {
                                    showPublishProcess = true
                                    onIntent(AndpubIntent.CreatePublishTasks)
                                },
                            )
                            // Historical task records from previous publish runs
                            val prevTasks = state.publishTasks.filter { it.appId == app.id }
                            if (prevTasks.isNotEmpty()) {
                                Text("上次发布记录", style = MaterialTheme.typography.titleSmall)
                                prevTasks.forEach { task ->
                                    PreviousPublishTaskCard(task) { showPublishProcess = true }
                                }
                            }
                        }
                    }
            }
        }
    }
}

// ─── Channel management section ────────────────────────────────────────────────

@Composable
private fun ChannelSection(state: AndpubUiState, onIntent: (AndpubIntent) -> Unit) {
    var channelDialogId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var deletingChannelId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var infoChannelId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var notifyAfterQuery by remember(state.selectedAppId) { mutableStateOf(false) }
    val expandedChannels = remember(state.selectedAppId) { mutableStateMapOf<String, Boolean>() }
    val editingChannel = state.selectedChannels.firstOrNull { it.id == channelDialogId }
    val deletingChannel = state.selectedChannels.firstOrNull { it.id == deletingChannelId }
    val infoChannel = state.selectedChannels.firstOrNull { it.id == infoChannelId }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "渠道以独立记录保存，可按市场新增、编辑、删除，并查询市场侧应用信息。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { notifyAfterQuery = !notifyAfterQuery },
            ) {
                Icon(
                    if (notifyAfterQuery) Icons.Outlined.Notifications
                    else Icons.Outlined.NotificationsNone,
                    contentDescription = if (notifyAfterQuery) "关闭通知" else "开启通知",
                )
            }
            IconButton(
                onClick = { onIntent(AndpubIntent.SyncAllChannels(notifyAfterQuery)) },
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "一键查询")
            }
            IconButton(onClick = { channelDialogId = NEW_CHANNEL_DIALOG_ID }) {
                Icon(Icons.Outlined.Add, contentDescription = "新增渠道")
            }
        }
    }

    if (state.selectedChannels.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Add,
            title = "暂无渠道",
            description = "点击右上角 + 按钮添加市场渠道",
        )
    } else {
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

// ─── Publish target section ────────────────────────────────────────────────────

@Composable
private fun PublishTargetSection(state: AndpubUiState, onIntent: (AndpubIntent) -> Unit) {
    val targetIds = state.publishTargetChannels.map { it.id }.toSet()
    val expandedVivoOptions = remember(state.selectedAppId) { mutableStateMapOf<String, Boolean>() }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("发布渠道", style = MaterialTheme.typography.titleMedium)
            if (state.selectedChannels.isEmpty()) {
                Text(
                    "暂无渠道，请先在渠道管理中新增渠道。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                if (!it) expandedVivoOptions.remove(channel.id)
                                onIntent(AndpubIntent.TogglePublishChannel(channel.id, it))
                            },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(channel.displayTitle(), style = MaterialTheme.typography.bodyLarge)
                            val detail = if (channel.marketType == MarketType.Vivo) {
                                "${channel.marketType.displayName} / ${channel.vivoEnvironment().displayName}"
                            } else {
                                channel.marketType.displayName
                            }
                            Text(detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (showVivoOptions) {
                            TextButton(onClick = {
                                expandedVivoOptions[channel.id] =
                                    expandedVivoOptions[channel.id] != true
                            }) {
                                Text(
                                    if (expandedVivoOptions[channel.id] == true) "收起选项"
                                    else "发布选项"
                                )
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
                Text(
                    "勾选渠道后才会显示该渠道的发布配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Publish start section ─────────────────────────────────────────────────────

@Composable
private fun PublishStartSection(state: AndpubUiState, onCreate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "勾选渠道并填写产物后点击「开始发布」，所有选中市场并行提交。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Button(
            enabled = state.publishTargetChannels.isNotEmpty() && !state.isCreatingPublishTasks,
            onClick = onCreate,
        ) {
            Text(if (state.isCreatingPublishTasks) "发布中…" else "开始发布")
        }
    }
}

// ─── Historical task card ──────────────────────────────────────────────────────

@Composable
private fun PreviousPublishTaskCard(
    task: PublishTaskRecord,
    onViewProcess: () -> Unit,
) {
    val (containerColor, labelColor) = when (task.status) {
        PublishTaskStatus.Submitted, PublishTaskStatus.Accepted ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PublishTaskStatus.Failed ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(task.marketType.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${task.publishMode.displayName} / ${task.artifact.sourceType.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = onViewProcess,
                label = { Text(task.status.displayName, style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = containerColor,
                    labelColor = labelColor,
                ),
            )
        }
    }
}
