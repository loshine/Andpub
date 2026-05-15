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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.application.AndpubController
import io.github.loshine.andpub.domain.market.MarketDefinitions
import io.github.loshine.andpub.domain.model.ArtifactDraft
import io.github.loshine.andpub.domain.model.ArtifactPart
import io.github.loshine.andpub.domain.model.ArtifactSourceType
import io.github.loshine.andpub.domain.model.ChannelRecord
import io.github.loshine.andpub.domain.model.FieldKind
import io.github.loshine.andpub.domain.model.FieldSchema
import io.github.loshine.andpub.domain.model.MarketAppInfo
import io.github.loshine.andpub.domain.model.MarketCapability
import io.github.loshine.andpub.domain.model.MarketType
import io.github.loshine.andpub.domain.model.PackageType
import io.github.loshine.andpub.domain.model.PublishMode
import io.github.loshine.andpub.domain.model.PublishTaskRecord
import io.github.loshine.andpub.domain.model.SplitApkSlot
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.platform.inspectLocalArtifact
import io.github.loshine.andpub.platform.inspectToolSettings
import io.github.loshine.andpub.platform.pickArtifactFilePath
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun AndpubWorkspace(
    controller: AndpubController,
) {
    LaunchedEffect(controller) {
        controller.loadState()
        snapshotFlow { controller.stateVersion }
            .drop(1)
            .collect { controller.saveState() }
    }

    Scaffold { padding ->
        Row(
            modifier = Modifier
                .padding(padding)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            AppSidebar(
                controller = controller,
                modifier = Modifier
                    .width(304.dp)
                    .fillMaxHeight(),
            )
            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                AppDetail(controller = controller)
            }
        }
    }
}

@Composable
private fun AppSidebar(
    controller: AndpubController,
    modifier: Modifier = Modifier,
) {
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Andpub", style = MaterialTheme.typography.headlineMedium)
        Text("多市场发包客户端", style = MaterialTheme.typography.bodyMedium)

        OutlinedCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("添加应用", style = MaterialTheme.typography.titleMedium)
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
                Button(
                    onClick = {
                        controller.createApp(appName, packageName)
                        appName = ""
                        packageName = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("创建")
                }
            }
        }

        Text("应用列表", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(controller.apps, key = { it.id }) { app ->
                val selected = controller.selectedAppId == app.id
                if (selected) {
                    ElevatedCard(
                        onClick = { controller.selectApp(app.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppListItem(app.name, app.packageName)
                    }
                } else {
                    OutlinedCard(
                        onClick = { controller.selectApp(app.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppListItem(app.name, app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    name: String,
    packageName: String,
) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(packageName, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AppDetail(
    controller: AndpubController,
) {
    val app = controller.selectedApp
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
            controller.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        ToolSettingsSection(controller)
        ChannelSection(controller)
        ArtifactSection(controller)
        PublishTaskSection(controller)
    }
}

@Composable
private fun ToolSettingsSection(
    controller: AndpubController,
) {
    var androidSdkPath by remember(controller.toolSettings) {
        mutableStateOf(controller.toolSettings.androidSdkPath)
    }
    var bundletoolPath by remember(controller.toolSettings) {
        mutableStateOf(controller.toolSettings.bundletoolPath)
    }
    var toolMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本地工具设置", style = MaterialTheme.typography.titleLarge)
                    Text("APK 解析使用 Android SDK build-tools/aapt2；AAB 解析使用 bundletool-all.jar。")
                }
                Button(
                    onClick = {
                        val settings = ToolSettings(
                            androidSdkPath = androidSdkPath,
                            bundletoolPath = bundletoolPath,
                        )
                        controller.updateToolSettings(settings)
                        scope.launch {
                            toolMessages = inspectToolSettings(
                                androidSdkPath = settings.androidSdkPath,
                                bundletoolPath = settings.bundletoolPath,
                            ).messages
                        }
                    },
                ) {
                    Text("保存设置")
                }
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
            toolMessages.forEach { message ->
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChannelSection(
    controller: AndpubController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("渠道配置和应用信息", style = MaterialTheme.typography.titleLarge)
        Text("每个市场独立保存账号配置；点击后按市场文档调用应用详情或可用状态查询接口。")

        MarketType.entries.forEach { marketType ->
            val channel = controller.selectedChannels.firstOrNull { it.marketType == marketType }
            ChannelCard(
                controller = controller,
                marketType = marketType,
                channel = channel,
            )
        }
    }
}

@Composable
private fun PublishTaskSection(
    controller: AndpubController,
) {
    val app = controller.selectedApp ?: return
    val tasks = controller.publishTasks.filter { it.appId == app.id }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("发布任务", style = MaterialTheme.typography.titleLarge)
                Text("当前阶段只创建 mock 任务，用于验证任务模型、状态和日志。")
            }
            Button(onClick = controller::createMockPublishTasks) {
                Text("创建 mock 任务")
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
            task.logs.forEach { log ->
                val color = when (log.level) {
                    io.github.loshine.andpub.domain.model.LogLevel.Info -> MaterialTheme.colorScheme.onSurface
                    io.github.loshine.andpub.domain.model.LogLevel.Warning -> MaterialTheme.colorScheme.primary
                    io.github.loshine.andpub.domain.model.LogLevel.Error -> MaterialTheme.colorScheme.error
                }
                Text("${log.level.name}: ${log.message}", color = color, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChannelCard(
    controller: AndpubController,
    marketType: MarketType,
    channel: ChannelRecord?,
) {
    val schema = MarketDefinitions.schemaOf(marketType)
    val credentials = remember(marketType, channel?.id) {
        mutableStateMapOf<String, String>().apply {
            schema.credentialFields.forEach { field ->
                put(field.key, channel?.credentials?.get(field.key).orEmpty())
            }
        }
    }
    var marketAppId by remember(marketType, channel?.id) {
        mutableStateOf(channel?.marketAppId.orEmpty())
    }
    var testInfo by remember(marketType, channel?.id) {
        mutableStateOf<MarketAppInfo?>(null)
    }
    var testError by remember(marketType, channel?.id) {
        mutableStateOf<String?>(null)
    }
    val scope = rememberCoroutineScope()

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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(marketType.displayName, style = MaterialTheme.typography.titleMedium)
                    CapabilityText(schema.capability)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                controller.testChannelConfig(
                                    marketType = marketType,
                                    marketAppId = marketAppId,
                                    credentials = credentials.toMap(),
                                    extraFields = emptyMap(),
                                ).fold(
                                    onSuccess = {
                                        testInfo = it
                                        testError = null
                                    },
                                    onFailure = {
                                        testInfo = null
                                        testError = it.message ?: "测试连接失败"
                                    },
                                )
                            }
                        },
                    ) {
                        Text("测试连接")
                    }
                    OutlinedButton(
                        enabled = channel != null,
                        onClick = {
                            val current = channel ?: return@OutlinedButton
                            scope.launch { controller.syncChannel(current) }
                        },
                    ) {
                        Text("获取应用信息")
                    }
                    Button(
                        onClick = {
                            controller.addOrUpdateChannel(
                                marketType = marketType,
                                marketAppId = marketAppId,
                                credentials = credentials.toMap(),
                                extraFields = emptyMap(),
                            )
                        },
                    ) {
                        Text(if (channel == null) "添加渠道" else "保存")
                    }
                }
            }

            OutlinedTextField(
                value = marketAppId,
                onValueChange = { marketAppId = it },
                label = { Text("市场侧应用 ID，可通过接口查询后回填") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                schema.credentialFields.forEach { field ->
                    SchemaTextField(
                        field = field,
                        value = credentials[field.key].orEmpty(),
                        onValueChange = { credentials[field.key] = it },
                        modifier = Modifier.width(260.dp),
                    )
                }
            }

            if (testInfo != null || testError != null) {
                ConnectionTestResult(
                    info = testInfo,
                    error = testError,
                )
            }

            if (channel != null) {
                HorizontalDivider()
                ChannelInfo(channel)
            }
        }
    }
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
private fun ChannelInfo(
    channel: ChannelRecord,
) {
    val info = channel.appInfo
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("渠道已配置", style = MaterialTheme.typography.titleSmall)
        Text("同步状态：${channel.syncStatus.displayName}")
        if (info != null) {
            Text("市场应用 ID：${info.marketAppId}")
            Text("线上版本：${info.onlineVersion ?: "-"}")
            Text("审核状态：${info.auditStatus ?: "-"}")
            Text("上架状态：${info.releaseStatus ?: "-"}")
            Text("更新时间：${info.updatedAtText}")
        }
        channel.lastError?.let {
            Text("错误：$it", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ArtifactSection(
    controller: AndpubController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("阶段 3：本地产物处理", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PublishMode.entries.forEach { mode ->
                FilterChip(
                    selected = controller.publishMode == mode,
                    onClick = { controller.updatePublishMode(mode) },
                    label = { Text(mode.displayName) },
                )
            }
        }

        if (controller.publishMode == PublishMode.UnifiedArtifact) {
            val selectedCapabilities = controller.selectedChannels
                .map { MarketDefinitions.schemaOf(it.marketType).capability }
            ArtifactEditor(
                title = "统一产物",
                draft = controller.unifiedArtifactDraft,
                toolSettings = controller.toolSettings,
                allowedPackageTypes = selectedCapabilities.allowedPackageTypes(),
                allowUrl = selectedCapabilities.all { it.supportsUserUrl },
                onDraftChange = controller::updateUnifiedArtifact,
                onPickFile = { path, inspectionResult ->
                    inspectionResult.fold(
                        onSuccess = { controller.applyInspectionToUnified(path, it) },
                        onFailure = { controller.applyArtifactErrorToUnified(path, it) },
                    )
                },
                onPickSplitFile = { slot, path, inspectionResult ->
                    inspectionResult.fold(
                        onSuccess = { controller.applySplitInspectionToUnified(slot, path, it) },
                        onFailure = { controller.applySplitArtifactErrorToUnified(slot, path, it) },
                    )
                },
            )
        } else {
            val channels = controller.selectedChannels
            if (channels.isEmpty()) {
                Text("先添加渠道，再为每个渠道配置独立产物。")
            }
            channels.forEach { channel ->
                val capability = MarketDefinitions.schemaOf(channel.marketType).capability
                ArtifactEditor(
                    title = channel.marketType.displayName,
                    draft = controller.artifactDrafts[channel.id] ?: ArtifactDraft(),
                    toolSettings = controller.toolSettings,
                    allowedPackageTypes = listOf(capability).allowedPackageTypes(),
                    allowUrl = capability.supportsUserUrl,
                    onDraftChange = { controller.updateChannelArtifact(channel.id, it) },
                    onPickFile = { path, inspectionResult ->
                        inspectionResult.fold(
                            onSuccess = { controller.applyInspectionToChannel(channel.id, path, it) },
                            onFailure = { controller.applyArtifactErrorToChannel(channel.id, path, it) },
                        )
                    },
                    onPickSplitFile = { slot, path, inspectionResult ->
                        inspectionResult.fold(
                            onSuccess = { controller.applySplitInspectionToChannel(channel.id, slot, path, it) },
                            onFailure = { controller.applySplitArtifactErrorToChannel(channel.id, slot, path, it) },
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
        sourceType = if (
            selectedPackageType == PackageType.SplitApk ||
            (draft.sourceType == ArtifactSourceType.Url && !allowUrl)
        ) {
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
                val sourceTypes = if (effectiveDraft.packageType == PackageType.SplitApk) {
                    listOf(ArtifactSourceType.LocalFile)
                } else {
                    ArtifactSourceType.entries.filter { it == ArtifactSourceType.LocalFile || allowUrl }
                }
                sourceTypes
                    .forEach { type ->
                    FilterChip(
                        selected = effectiveDraft.sourceType == type,
                        onClick = { onDraftChange(effectiveDraft.copy(sourceType = type)) },
                        label = { Text(type.displayName) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackageType.entries.filter { it in allowedPackageTypes }.forEach { type ->
                    FilterChip(
                        selected = effectiveDraft.packageType == type,
                        onClick = {
                            onDraftChange(
                                effectiveDraft.copy(
                                    packageType = type,
                                    sourceType = if (type == PackageType.SplitApk) {
                                        ArtifactSourceType.LocalFile
                                    } else {
                                        effectiveDraft.sourceType
                                    },
                                )
                            )
                        },
                        label = { Text(type.displayName) },
                    )
                }
            }
            if (!allowUrl) {
                Text("当前目标渠道不支持用户 URL 拉包。", style = MaterialTheme.typography.bodySmall)
            }
            if (effectiveDraft.packageType == PackageType.SplitApk) {
                Text("32/64 APK 当前阶段仅支持本地文件。", style = MaterialTheme.typography.bodySmall)
            }

            if (effectiveDraft.packageType == PackageType.SplitApk) {
                SplitArtifactFileRow(
                    slot = SplitApkSlot.Arm32,
                    part = effectiveDraft.split32,
                    toolSettings = toolSettings,
                    onPartChange = { onDraftChange(effectiveDraft.copy(split32 = it)) },
                    onPickFile = onPickSplitFile,
                )
                SplitArtifactFileRow(
                    slot = SplitApkSlot.Arm64,
                    part = effectiveDraft.split64,
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
                        onValueChange = { onDraftChange(effectiveDraft.copy(value = it)) },
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
                onValueChange = { onPartChange(part.copy(value = it)) },
                label = { Text("${slot.displayName} 文件路径") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
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
        ArtifactPartSummary(slot.displayName, part)
    }
}

private fun List<MarketCapability>.allowedPackageTypes(): Set<PackageType> {
    if (isEmpty()) return PackageType.entries.toSet()

    val candidates = PackageType.entries.toSet()
    return filterSupportedPackageTypes(candidates)
}

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
        draft.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (draft.md5.isNotBlank()) Text("MD5：${draft.md5}", style = MaterialTheme.typography.bodySmall)
        if (draft.sha1.isNotBlank()) Text("SHA-1：${draft.sha1}", style = MaterialTheme.typography.bodySmall)
        if (draft.sha256.isNotBlank()) Text("SHA-256：${draft.sha256}", style = MaterialTheme.typography.bodySmall)
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
        part.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (part.md5.isNotBlank()) Text("MD5：${part.md5}", style = MaterialTheme.typography.bodySmall)
        if (part.sha1.isNotBlank()) Text("SHA-1：${part.sha1}", style = MaterialTheme.typography.bodySmall)
        if (part.sha256.isNotBlank()) Text("SHA-256：${part.sha256}", style = MaterialTheme.typography.bodySmall)
        part.packageName?.let { Text("包名：$it", style = MaterialTheme.typography.bodySmall) }
        part.versionName?.let { Text("版本名：$it", style = MaterialTheme.typography.bodySmall) }
        part.versionCode?.let { Text("版本号：$it", style = MaterialTheme.typography.bodySmall) }
        if (part.abiList.isNotEmpty()) {
            Text("ABI：${part.abiList.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(4.dp))
}
