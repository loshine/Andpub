package io.github.loshine.andpub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import io.github.loshine.andpub.ui.components.AndpubMessageEffect
import io.github.loshine.andpub.ui.components.AndpubSnackbarHost
import io.github.loshine.andpub.ui.components.EmptyState
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
    val snackbarHostState = AndpubMessageEffect(
        message = state.uiMessage,
        onDismiss = { id -> viewModel.onIntent(AndpubIntent.DismissMessage(id)) },
    )

    // NavigationSuiteScaffold outside so SnackbarHost sits above the nav bar in compact layout.
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
        Scaffold(
            snackbarHost = {
                AndpubSnackbarHost(
                    hostState = snackbarHostState,
                    isError = state.uiMessage?.isError == true,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize(),
            ) {
                AnimatedVisibility(
                    visible = state.busy.anyBusy || state.isCreatingPublishTasks,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = currentDestination,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                        label = "top-destination",
                        modifier = Modifier.fillMaxSize(),
                    ) { destination ->
                        when (destination) {
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
            }
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

    AnimatedContent(
        targetState = showDetail,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { -it } + fadeOut(tween(250)))
            } else {
                (slideInHorizontally(tween(250)) { -it } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)))
            }
        },
        label = "compact-nav",
        modifier = Modifier.fillMaxSize(),
    ) { detail ->
        if (!detail) {
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
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedContent(
                targetState = state.selectedApp == null,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "expanded-detail",
            ) { noSelection ->
                if (noSelection) {
                    EmptyState(
                        icon = Icons.Outlined.Apps,
                        title = "尚未选择应用",
                        description = "从左侧列表选择一个应用，或新建一个",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AppDetailScreen(
                        state = state,
                        onIntent = onIntent,
                        onBack = null,
                    )
                }
            }
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

    Column(
        modifier = modifier
            .safeContentPadding()
            .padding(24.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("本地工具", style = MaterialTheme.typography.headlineMedium)
        Text(
            "APK 解析使用 Android SDK build-tools/aapt2；AAB 解析使用 bundletool-all.jar。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = androidSdkPath,
            onValueChange = { androidSdkPath = it },
            label = { Text("Android SDK 路径") },
            supportingText = { Text("留空则读取 ANDROID_HOME / ANDROID_SDK_ROOT") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = bundletoolPath,
            onValueChange = { bundletoolPath = it },
            label = { Text("bundletool-all.jar 路径") },
            supportingText = { Text("用于解析 AAB 文件") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = weComWebhookUrl,
            onValueChange = { weComWebhookUrl = it },
            label = { Text("企业微信机器人 WebHook") },
            supportingText = { Text("发布完成后自动推送通知") },
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
        AnimatedVisibility(
            visible = toolMessages.isNotEmpty(),
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
        ) {
            Column {
                toolMessages.forEach { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
