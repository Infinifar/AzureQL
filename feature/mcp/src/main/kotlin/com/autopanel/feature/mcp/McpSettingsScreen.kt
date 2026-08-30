package com.autopanel.feature.mcp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.mcp.McpServerConfig
import com.autopanel.core.mcp.McpServerState
import com.autopanel.core.ui.i18n.localizedText

@Composable
fun McpSettingsScreen(
    onBack: () -> Unit,
    viewModel: McpSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.startService() }
    val startService = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startService()
        }
    }

    McpSettingsContent(
        state = state,
        onBack = onBack,
        onStart = startService,
        onStop = viewModel::stopService
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpSettingsContent(
    state: McpServerState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isBusy = state == McpServerState.Starting || state == McpServerState.Stopping
    val isRunning = state is McpServerState.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localizedText("MCP 服务", "MCP service")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon(state), contentDescription = null, tint = statusColor(state))
                        Text(
                            text = statusLabel(state),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Text(
                        text = (state as? McpServerState.Running)?.endpoint
                            ?: McpServerConfig().endpoint,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state is McpServerState.Failed) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("Phase 0 技术验证", "Phase 0 technical preview"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        localizedText(
                            "当前仅提供只读 hello 工具，只监听本机地址，不会暴露青龙凭据。电脑可使用 adb forward tcp:18765 tcp:18765 连接。",
                            "This build exposes only a read-only hello tool on loopback and never exposes QingLong credentials. Connect from a computer with adb forward tcp:18765 tcp:18765."
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            if (isRunning) {
                OutlinedButton(
                    onClick = onStop,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null)
                    Text(localizedText("停止 MCP 服务", "Stop MCP service"), Modifier.padding(start = 8.dp))
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                    }
                    Text(localizedText("启动 MCP 服务", "Start MCP service"), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun statusLabel(state: McpServerState): String = when (state) {
    McpServerState.Stopped -> localizedText("已停止", "Stopped")
    McpServerState.Starting -> localizedText("正在启动", "Starting")
    is McpServerState.Running -> localizedText("运行中", "Running")
    McpServerState.Stopping -> localizedText("正在停止", "Stopping")
    is McpServerState.Failed -> localizedText("启动失败", "Start failed")
}

@Composable
private fun statusColor(state: McpServerState) = when (state) {
    is McpServerState.Running -> MaterialTheme.colorScheme.primary
    is McpServerState.Failed -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusIcon(state: McpServerState) = when (state) {
    is McpServerState.Running -> Icons.Default.CheckCircle
    is McpServerState.Failed -> Icons.Default.Error
    else -> Icons.Default.Info
}
