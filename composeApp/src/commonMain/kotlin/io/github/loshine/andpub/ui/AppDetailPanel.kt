package io.github.loshine.andpub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.vivoEnvironment
import io.github.loshine.andpub.presentation.AndpubIntent
import io.github.loshine.andpub.presentation.AndpubUiState
import io.github.loshine.andpub.presentation.PublishGuards
import io.github.loshine.andpub.ui.components.ArtifactSection
import io.github.loshine.andpub.ui.components.ChannelEditorDialog
import io.github.loshine.andpub.ui.components.ChannelInfoDialog
import io.github.loshine.andpub.ui.components.ChannelSummaryCard
import io.github.loshine.andpub.ui.components.EmptyState
import io.github.loshine.andpub.ui.components.VivoPublishOptionSection
import io.github.loshine.andpub.ui.components.displayTitle

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
                    val inPublishProcess = showPublishProcess && selectedTab == AppDetailTab.Publish
                    Crossfade(
                        targetState = inPublishProcess,
                        animationSpec = tween(200),
                        label = "detail-title",
                    ) { process ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (process) "发布过程" else app.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!process) {
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    when {
                        showPublishProcess && selectedTab == AppDetailTab.Publish -> {
                            IconButton(onClick = { showPublishProcess = false }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "返回配置",
                                )
                            }
                        }
                        onBack != null -> {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "返回",
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Space reserved for process-page controls rendered inside content when needed.
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (!(showPublishProcess && selectedTab == AppDetailTab.Publish)) {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    AppDetailTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                if (tab != AppDetailTab.Publish) {
                                    showPublishProcess = false
                                }
                            },
                            text = { Text(tab.title) },
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTab to showPublishProcess,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "detail-tab",
                modifier = Modifier.fillMaxSize(),
            ) { (tab, inProcess) ->
                when (tab) {
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
                        if (inProcess) {
                            PublishProcessPage(
                                state = state,
                                onRefreshTaskStatus = {
                                    onIntent(AndpubIntent.RefreshPublishTaskStatus(it))
                                },
                                onRetryFailedTasks = {
                                    onIntent(AndpubIntent.RetryFailedPublishTasks)
                                },
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
                                val prevTasks = state.publishTasks.filter { it.appId == app.id }
                                if (prevTasks.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("上次发布记录", style = MaterialTheme.typography.titleSmall)
                                        TextButton(onClick = { showPublishProcess = true }) {
                                            Text("查看过程")
                                        }
                                    }
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
}

// ─── Channel management section ────────────────────────────────────────────────

@Composable
private fun ChannelSection(state: AndpubUiState, onIntent: (AndpubIntent) -> Unit) {
    var channelDialogId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var deletingChannelId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var infoChannelId by remember(state.selectedAppId) { mutableStateOf<String?>(null) }
    var notifyAfterQuery by rememberSaveable(state.selectedAppId) { mutableStateOf(false) }
    val expandedChannels = remember(state.selectedAppId) { mutableStateMapOf<String, Boolean>() }
    val editingChannel = state.selectedChannels.firstOrNull { it.id == channelDialogId }
    val deletingChannel = state.selectedChannels.firstOrNull { it.id == deletingChannelId }
    val infoChannel = state.selectedChannels.firstOrNull { it.id == infoChannelId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "渠道以独立记录保存，可按市场新增、编辑、删除，并查询市场侧应用信息。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { notifyAfterQuery = !notifyAfterQuery }) {
                Icon(
                    imageVector = if (notifyAfterQuery) Icons.Filled.Notifications
                    else Icons.Outlined.NotificationsOff,
                    contentDescription = if (notifyAfterQuery) "关闭通知" else "开启通知",
                    tint = if (notifyAfterQuery) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { onIntent(AndpubIntent.SyncAllChannels(notifyAfterQuery)) },
                enabled = state.selectedChannels.isNotEmpty() && !state.busy.syncingAll,
            ) {
                if (state.busy.syncingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(if (state.busy.syncingAll) "查询中…" else "一键查询")
            }
            TextButton(onClick = { channelDialogId = NEW_CHANNEL_DIALOG_ID }) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text("新增渠道")
            }
        }
    }

    if (state.selectedChannels.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Add,
            title = "暂无渠道",
            description = "添加市场渠道后即可查询应用信息并发布",
            actionLabel = "新增渠道",
            onAction = { channelDialogId = NEW_CHANNEL_DIALOG_ID },
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
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (showVivoOptions) {
                            TextButton(
                                onClick = {
                                    expandedVivoOptions[channel.id] =
                                        expandedVivoOptions[channel.id] != true
                                },
                            ) {
                                Text(
                                    if (expandedVivoOptions[channel.id] == true) "收起选项"
                                    else "发布选项",
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = showVivoOptions && expandedVivoOptions[channel.id] == true,
                        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
                    ) {
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
    val blockers = remember(
        state.publishMode,
        state.publishTargetChannels,
        state.unifiedArtifactDraft,
        state.artifactDrafts,
        state.vivoProductionConfirmed,
        state.isCreatingPublishTasks,
        state.selectedAppId,
    ) {
        PublishGuards.blockers(state)
    }
    val canStart = blockers.isEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = blockers.isNotEmpty() && state.publishTargetChannels.isNotEmpty(),
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                blockers.forEach { blocker ->
                    Text(
                        "· $blocker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
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
                enabled = canStart,
                onClick = onCreate,
            ) {
                if (state.isCreatingPublishTasks) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Text("发布中…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("开始发布")
                }
            }
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
    OutlinedCard(
        onClick = onViewProcess,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "查看 ${task.marketType.displayName} 发布过程，状态 ${task.status.displayName}"
            },
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
                Text(task.marketType.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${task.publishMode.displayName} / ${task.artifact.sourceType.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Non-interactive status label (card itself is the only action target).
            Surface(
                color = containerColor,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    task.status.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
