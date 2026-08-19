package com.autopanel.feature.dependency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.DependencyCacheType
import com.autopanel.core.model.DependencySetting

@Composable
fun DependencySettingsScreen(
    onBack: () -> Unit,
    viewModel: DependencySettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DependencySettingsEvent.Message -> snackbarHostState.showSnackbar(event.value)
            }
        }
    }

    state.cacheToClean?.let { type ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCleanCache,
            title = { Text("清理${type.displayName}") },
            text = { Text("将删除服务端缓存，之后安装依赖时需要重新下载。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCleanCache) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCleanCache) { Text("取消") }
            }
        )
    }

    DependencySettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::load,
        onDependenceProxyChanged = viewModel::onDependenceProxyChanged,
        onNodeMirrorChanged = viewModel::onNodeMirrorChanged,
        onPythonMirrorChanged = viewModel::onPythonMirrorChanged,
        onLinuxMirrorChanged = viewModel::onLinuxMirrorChanged,
        onSave = viewModel::save,
        onCleanCache = viewModel::requestCleanCache
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DependencySettingsContent(
    state: DependencySettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDependenceProxyChanged: (String) -> Unit,
    onNodeMirrorChanged: (String) -> Unit,
    onPythonMirrorChanged: (String) -> Unit,
    onLinuxMirrorChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCleanCache: (DependencyCacheType) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("依赖设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isSaving) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
            Text(
                "留空可恢复服务端默认值。Node.js 与 Linux 镜像更新会在青龙后台执行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.dependenceProxy,
                onValueChange = onDependenceProxyChanged,
                label = { Text("依赖代理") },
                placeholder = { Text("例如 http://proxy:7890") },
                isError = state.isSettingError(DependencySetting.PROXY),
                supportingText = state.supportingText(DependencySetting.PROXY),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.nodeMirror,
                onValueChange = onNodeMirrorChanged,
                label = { Text("Node.js 镜像") },
                isError = state.isSettingError(DependencySetting.NODE_MIRROR),
                supportingText = state.supportingText(DependencySetting.NODE_MIRROR),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.pythonMirror,
                onValueChange = onPythonMirrorChanged,
                label = { Text("Python 镜像") },
                isError = state.isSettingError(DependencySetting.PYTHON_MIRROR),
                supportingText = state.supportingText(DependencySetting.PYTHON_MIRROR),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.linuxMirror,
                onValueChange = onLinuxMirrorChanged,
                label = { Text("Linux 软件源") },
                isError = state.isSettingError(DependencySetting.LINUX_MIRROR),
                supportingText = state.supportingText(DependencySetting.LINUX_MIRROR),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSave,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存依赖设置")
                }
            }

            if (state.taskLog.isNotEmpty()) {
                Text("后台任务日志", style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = state.taskLog.joinToString("\n"),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("缓存清理", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DependencyCacheType.entries.forEach { type ->
                    OutlinedButton(
                        onClick = { onCleanCache(type) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(type.displayName)
                    }
                }
            }
        }
    }
}

private fun DependencySettingsUiState.isSettingError(setting: DependencySetting): Boolean =
    settingStates[setting]?.status == DependencySettingSaveStatus.ERROR

private fun DependencySettingsUiState.supportingText(
    setting: DependencySetting
): (@Composable () -> Unit)? {
    val item = settingStates[setting] ?: return null
    if (item.status == DependencySettingSaveStatus.IDLE) return null
    val label = buildString {
        append(item.status.displayName)
        item.detail?.takeIf(String::isNotBlank)?.let { append("：").append(it) }
    }
    return { Text(label) }
}
