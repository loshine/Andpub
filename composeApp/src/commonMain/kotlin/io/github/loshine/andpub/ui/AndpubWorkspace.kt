package io.github.loshine.andpub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.loshine.andpub.domain.model.ToolSettings
import io.github.loshine.andpub.platform.inspectToolSettings
import io.github.loshine.andpub.presentation.AndpubIntent
import io.github.loshine.andpub.presentation.AndpubUiState
import io.github.loshine.andpub.presentation.AndpubViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class TopLevelDestination(
    val icon: ImageVector,
    val label: String,
) {
    Apps(Icons.Outlined.Apps, "应用"),
    Settings(Icons.Outlined.Settings, "设置"),
}

@Composable
fun AndpubWorkspace(
    viewModel: AndpubViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.Apps) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { dest ->
                item(
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = currentDestination == dest,
                    onClick = { currentDestination = dest },
                )
            }
        },
    ) {
        when (currentDestination) {
            TopLevelDestination.Apps -> AdaptiveAppsLayout(
                state = state,
                onIntent = viewModel::onIntent,
            )
            TopLevelDestination.Settings -> SettingsScreen(
                state = state,
                onIntent = viewModel::onIntent,
            )
        }
    }
}

// ─── Adaptive layout ──────────────────────────────────────────────────────────

@Composable
private fun AdaptiveAppsLayout(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp
        if (isCompact) {
            CompactAppsLayout(state = state, onIntent = onIntent)
        } else {
            ExpandedAppsLayout(state = state, onIntent = onIntent)
        }
    }
}

/** Mobile: App list first; tap an app to navigate into its detail. */
@Composable
private fun CompactAppsLayout(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
) {
    var showDetail by rememberSaveable { mutableStateOf(false) }
    // If the user deletes the selected app, go back to the list automatically.
    if (state.selectedApp == null) showDetail = false

    if (!showDetail) {
        AppListScreen(
            state = state,
            onIntent = onIntent,
            onAppSelected = { appId ->
                onIntent(AndpubIntent.SelectApp(appId))
                showDetail = true
            },
        )
    } else {
        AppDetailScreen(
            state = state,
            onIntent = onIntent,
            onBack = { showDetail = false },
        )
    }
}

/** Desktop / tablet: permanent sidebar + detail pane. */
@Composable
private fun ExpandedAppsLayout(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AppSidebar(
            state = state,
            onIntent = onIntent,
            modifier = Modifier
                .width(304.dp)
                .fillMaxHeight(),
        )
        HorizontalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
        )
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            AppDetailScreen(
                state = state,
                onIntent = onIntent,
                onBack = null,
            )
        }
    }
}

// ─── Settings screen ──────────────────────────────────────────────────────────

@Composable
internal fun SettingsScreen(
    state: AndpubUiState,
    onIntent: (AndpubIntent) -> Unit,
    modifier: Modifier = Modifier,
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

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .safeContentPadding()
                .padding(24.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("本地工具", style = MaterialTheme.typography.headlineMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Text(
                "APK 解析使用 Android SDK build-tools/aapt2；AAB 解析使用 bundletool-all.jar。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            OutlinedTextField(
                value = androidSdkPath,
                onValueChange = { androidSdkPath = it },
                label = { Text("Android SDK 路径") },
                supportingText = { Text("留空则读取 ANDROID_HOME / ANDROID_SDK_ROOT") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            OutlinedTextField(
                value = bundletoolPath,
                onValueChange = { bundletoolPath = it },
                label = { Text("bundletool-all.jar 路径") },
                supportingText = { Text("用于解析 AAB 文件") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            OutlinedTextField(
                value = weComWebhookUrl,
                onValueChange = { weComWebhookUrl = it },
                label = { Text("企业微信机器人 WebHook") },
                supportingText = { Text("发布完成后自动推送通知") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
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
                    Icon(
                        Icons.Outlined.Build,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("检测工具")
                }
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
                    },
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("保存设置")
                }
            }
            toolMessages.forEach { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
