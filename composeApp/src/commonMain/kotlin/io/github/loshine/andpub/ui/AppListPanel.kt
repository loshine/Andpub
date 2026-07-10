package io.github.loshine.andpub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.model.AppRecord
import io.github.loshine.andpub.domain.usecase.ValidatePackageNameUseCase
import io.github.loshine.andpub.presentation.AndpubIntent
import io.github.loshine.andpub.presentation.AndpubUiState
import io.github.loshine.andpub.ui.components.EmptyState

internal const val NEW_APP_DIALOG_ID = "new-app"

private val packageNameValidator = ValidatePackageNameUseCase()

// ─── Mobile list screen ────────────────────────────────────────────────────────

/** Compact top-level screen showing all apps in a scrollable list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppListScreen(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
    onAppSelected: (String) -> Unit,
) {
    var appDialogId by remember { mutableStateOf<String?>(null) }
    var deletingAppId by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val importExportBusy = state.busy.importing || state.busy.exporting

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Andpub")
                        Text(
                            "多市场发包客户端",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(AndpubIntent.ImportAppSettings) },
                        enabled = !importExportBusy,
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "导入应用设置")
                    }
                    IconButton(
                        onClick = { onIntent(AndpubIntent.ExportSelectedAppSettings) },
                        enabled = state.selectedApp != null && !importExportBusy,
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "导出当前应用设置")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { appDialogId = NEW_APP_DIALOG_ID }) {
                Icon(Icons.Outlined.Add, contentDescription = "创建应用")
            }
        },
    ) { padding ->
        if (state.apps.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Add,
                title = "还没有应用",
                description = "创建第一个应用，开始配置渠道与发包",
                actionLabel = "创建应用",
                onAction = { appDialogId = NEW_APP_DIALOG_ID },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.apps, key = { it.id }) { app ->
                    AppListCard(
                        app = app,
                        selected = state.selectedAppId == app.id,
                        onClick = { onAppSelected(app.id) },
                        onEdit = { appDialogId = app.id },
                        onDelete = { deletingAppId = app.id },
                        channelCount = state.channels.count { it.appId == app.id },
                    )
                }
            }
        }
    }

    AppCrudDialogs(
        state = state,
        appDialogId = appDialogId,
        deletingAppId = deletingAppId,
        onDismissEditor = { appDialogId = null },
        onDismissDelete = { deletingAppId = null },
        onIntent = onIntent,
    )
}

// ─── Desktop sidebar ───────────────────────────────────────────────────────────

/** Persistent sidebar for expanded (desktop / tablet) layout. */
@Composable
internal fun AppSidebar(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var appDialogId by remember { mutableStateOf<String?>(null) }
    var deletingAppId by remember { mutableStateOf<String?>(null) }
    val importExportBusy = state.busy.importing || state.busy.exporting

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Andpub", style = MaterialTheme.typography.headlineMedium)
            Text(
                "多市场发包客户端",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = { appDialogId = NEW_APP_DIALOG_ID },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text("创建应用")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onIntent(AndpubIntent.ImportAppSettings) },
                enabled = !importExportBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(if (state.busy.importing) "导入中" else "导入")
            }
            OutlinedButton(
                onClick = { onIntent(AndpubIntent.ExportSelectedAppSettings) },
                enabled = state.selectedApp != null && !importExportBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Outlined.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(if (state.busy.exporting) "导出中" else "导出")
            }
        }

        Text("应用列表", style = MaterialTheme.typography.titleSmall)

        if (state.apps.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Add,
                title = "暂无应用",
                description = "点击上方按钮创建第一个应用",
                actionLabel = "创建应用",
                onAction = { appDialogId = NEW_APP_DIALOG_ID },
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.apps, key = { it.id }) { app ->
                AppListCard(
                    app = app,
                    selected = state.selectedAppId == app.id,
                    onClick = { onIntent(AndpubIntent.SelectApp(app.id)) },
                    onEdit = { appDialogId = app.id },
                    onDelete = { deletingAppId = app.id },
                    channelCount = state.channels.count { it.appId == app.id },
                )
            }
        }
    }

    AppCrudDialogs(
        state = state,
        appDialogId = appDialogId,
        deletingAppId = deletingAppId,
        onDismissEditor = { appDialogId = null },
        onDismissDelete = { deletingAppId = null },
        onIntent = onIntent,
    )
}

// ─── Shared CRUD dialogs ───────────────────────────────────────────────────────

@Composable
private fun AppCrudDialogs(
    state: AndpubUiState,
    appDialogId: String?,
    deletingAppId: String?,
    onDismissEditor: () -> Unit,
    onDismissDelete: () -> Unit,
    onIntent: (AndpubIntent) -> Unit,
) {
    val editingApp = state.apps.firstOrNull { it.id == appDialogId }
    val deletingApp = state.apps.firstOrNull { it.id == deletingAppId }
    val existingPackages = remember(state.apps, editingApp?.id) {
        state.apps
            .filter { it.id != editingApp?.id }
            .map { it.packageName }
            .toSet()
    }

    if (appDialogId != null) {
        AppEditorDialog(
            appId = editingApp?.id,
            initialName = editingApp?.name.orEmpty(),
            initialPackageName = editingApp?.packageName.orEmpty(),
            existingPackageNames = existingPackages,
            onDismiss = onDismissEditor,
            onSave = { name, packageName ->
                if (editingApp == null) {
                    onIntent(AndpubIntent.CreateApp(name, packageName))
                } else {
                    onIntent(AndpubIntent.UpdateApp(editingApp.id, name, packageName))
                }
                onDismissEditor()
            },
        )
    }

    deletingApp?.let { app ->
        ConfirmDeleteDialog(
            title = "删除应用",
            message = "删除 ${app.name} 会同时删除它的渠道、产物草稿和发布任务。",
            onDismiss = onDismissDelete,
            onConfirm = {
                onIntent(AndpubIntent.DeleteApp(app.id))
                onDismissDelete()
            },
        )
    }
}

// ─── App list card ─────────────────────────────────────────────────────────────

@Composable
private fun AppListCard(
    app: AppRecord,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    channelCount: Int,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(app.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (channelCount > 0) {
                    Text(
                        "$channelCount 个渠道",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "编辑 ${app.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除 ${app.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// ─── Shared dialogs ────────────────────────────────────────────────────────────

@Composable
internal fun AppEditorDialog(
    appId: String?,
    initialName: String,
    initialPackageName: String,
    existingPackageNames: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var appName by remember(appId) { mutableStateOf(initialName) }
    var packageName by remember(appId) { mutableStateOf(initialPackageName) }

    val nameError = when {
        appName.isBlank() -> "应用名必填"
        else -> null
    }
    val packageError = when {
        packageName.isBlank() -> "包名必填"
        !packageNameValidator(packageName.trim()) -> "包名格式不正确（如 com.example.app）"
        packageName.trim() in existingPackageNames -> "该包名已经存在"
        else -> null
    }
    val canSave = nameError == null && packageError == null

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
                    isError = appName.isNotEmpty() && nameError != null,
                    supportingText = {
                        if (appName.isNotEmpty() && nameError != null) Text(nameError)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("包名") },
                    singleLine = true,
                    isError = packageName.isNotEmpty() && packageError != null,
                    supportingText = {
                        when {
                            packageName.isNotEmpty() && packageError != null -> Text(packageError)
                            else -> Text("至少两段，每段以字母开头")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(appName.trim(), packageName.trim()) },
                enabled = canSave,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
internal fun ConfirmDeleteDialog(
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
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
