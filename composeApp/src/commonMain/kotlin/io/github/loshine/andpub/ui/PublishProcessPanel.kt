package io.github.loshine.andpub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.model.LogLevel
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishTaskLog
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.PublishTaskStage
import io.github.loshine.andpub.domain.model.PublishTaskStatus
import io.github.loshine.andpub.domain.model.isTerminal
import io.github.loshine.andpub.presentation.AndpubUiState

// ─── Publish process page (content only; parent owns TopAppBar) ────────────────

@Composable
internal fun PublishProcessPage(
    state: AndpubUiState,
    onRefreshTaskStatus: (String) -> Unit,
    onRetryFailedTasks: () -> Unit,
) {
    val app = state.selectedApp ?: return
    val tasks = state.publishTasks.filter { it.appId == app.id }
    var showDetailLogs by remember(app.id) { mutableStateOf(false) }

    val allTerminal = tasks.isNotEmpty() && !state.isCreatingPublishTasks &&
            tasks.all { it.status.isTerminal() }
    val hasFailedTasks = tasks.any { it.status == PublishTaskStatus.Failed }
    val successCount = tasks.count {
        it.status == PublishTaskStatus.Submitted || it.status == PublishTaskStatus.Accepted
    }
    val failedCount = tasks.count { it.status == PublishTaskStatus.Failed }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "详细日志",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 4.dp),
            )
            Switch(
                checked = showDetailLogs,
                onCheckedChange = { showDetailLogs = it },
            )
        }

        if (allTerminal) {
            PublishSummaryBanner(
                successCount = successCount,
                failedCount = failedCount,
                hasFailedTasks = hasFailedTasks,
                isBusy = state.isCreatingPublishTasks,
                onRetryFailedTasks = onRetryFailedTasks,
            )
        }

        if (state.isCreatingPublishTasks && tasks.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("正在准备并检查产物…")
            }
            return@Column
        }

        if (tasks.isEmpty()) {
            Text(
                "暂无发布任务",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 360.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 1200.dp),
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
private fun PublishSummaryBanner(
    successCount: Int,
    failedCount: Int,
    hasFailedTasks: Boolean,
    isBusy: Boolean,
    onRetryFailedTasks: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (successCount > 0) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${successCount} 个市场已提交",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (failedCount > 0) {
            Icon(
                Icons.Outlined.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                "${failedCount} 个市场失败",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (hasFailedTasks) {
            Button(
                onClick = onRetryFailedTasks,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("重试失败任务（${failedCount} 个）")
            }
        }
    }
}

// ─── Per-market progress card ──────────────────────────────────────────────────

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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskStatusChip(task.status)
                    if (canRefreshStatus) {
                        IconButton(onClick = onRefreshStatus) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新状态")
                        }
                    }
                }
            }
            task.publishEnvironment?.let {
                Text(
                    "目标环境：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    title = "下载",
                    lines = task.downloadSummary(),
                    progressLogs = task.latestProgressLogs(PublishTaskStage.Download),
                )
                PublishProcessSection("校验", task.validationSummary())
                PublishProcessSection(
                    title = "发布",
                    lines = task.publishSummary(),
                    progressLogs = task.latestProgressLogs(PublishTaskStage.Upload),
                )
            }
        }
    }
}

@Composable
private fun TaskStatusChip(status: PublishTaskStatus) {
    val (containerColor, labelColor) = when (status) {
        PublishTaskStatus.Submitted, PublishTaskStatus.Accepted ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PublishTaskStatus.Failed ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        PublishTaskStatus.Uploading, PublishTaskStatus.Validating ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(status.displayName, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            when (status) {
                PublishTaskStatus.Submitted, PublishTaskStatus.Accepted ->
                    Icon(Icons.Outlined.CheckCircle, null)
                PublishTaskStatus.Failed ->
                    Icon(Icons.Outlined.Error, null)
                PublishTaskStatus.Uploading, PublishTaskStatus.Validating ->
                    Icon(Icons.Outlined.HourglassEmpty, null)
                else -> {}
            }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            leadingIconContentColor = labelColor,
        ),
    )
}

@Composable
private fun PublishProcessSection(
    title: String,
    lines: List<String>,
    progressLogs: List<PublishTaskLog> = emptyList(),
) {
    if (lines.isEmpty() && progressLogs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                Text(
                    log.progressLabel ?: log.message,
                    style = MaterialTheme.typography.bodySmall,
                )
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

// ─── Summary helpers ───────────────────────────────────────────────────────────

private fun PublishTaskRecord.canRefreshPublishStatus(): Boolean {
    val capability = MarketDefinitions.schemaOf(marketType).capability
    return capability.supportsPublishStatusQuery &&
            (status == PublishTaskStatus.Submitted || status == PublishTaskStatus.Accepted)
}

internal fun PublishTaskRecord.downloadSummary(): List<String> =
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

internal fun PublishTaskRecord.validationSummary(): List<String> {
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

internal fun PublishTaskRecord.publishSummary(): List<String> =
    when (status) {
        PublishTaskStatus.Failed ->
            listOf("失败：${logs.lastOrNull { it.level == LogLevel.Error }?.message ?: "发布失败"}")
        PublishTaskStatus.Ready ->
            listOf(
                if (marketType == io.github.loshine.andpub.domain.model.MarketType.Vivo) {
                    "待提交：尚未调用 vivo 上传接口"
                } else {
                    "仅完成校验：当前市场暂未接入厂商发布"
                },
            )
        PublishTaskStatus.Created -> listOf("已创建：等待检查")
        PublishTaskStatus.Validating -> listOf("检查中：正在准备产物")
        PublishTaskStatus.Uploading ->
            listOf("上传中：正在调用厂商上传接口") +
                    latestStageMessages(PublishTaskStage.Upload, PublishTaskStage.Submit)
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

internal fun PublishTaskRecord.latestProgressLogs(
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
private fun LogLevel.color() = when (this) {
    LogLevel.Info -> MaterialTheme.colorScheme.onSurface
    LogLevel.Warning -> MaterialTheme.colorScheme.primary
    LogLevel.Error -> MaterialTheme.colorScheme.error
}
