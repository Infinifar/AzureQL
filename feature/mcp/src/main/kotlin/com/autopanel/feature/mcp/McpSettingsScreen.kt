package com.autopanel.feature.mcp

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.mcp.McpAgent
import com.autopanel.core.mcp.McpAgentId
import com.autopanel.core.mcp.McpIssuedCredential
import com.autopanel.core.mcp.MAX_AGENT_NAME_LENGTH
import com.autopanel.core.mcp.McpServerConfig
import com.autopanel.core.mcp.McpServerState
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.security.AuthenticationResult
import com.autopanel.core.ui.security.DeviceAuthenticator
import java.text.DateFormat
import java.util.Date

@Composable
fun McpSettingsScreen(
    onBack: () -> Unit,
    viewModel: McpSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val credential by viewModel.credential.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val agentBusy by viewModel.agentOperationInProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val agentAuthSubtitle = localizedText(
        "验证身份后创建只读 Agent Token",
        "Authenticate to create a read-only Agent token"
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startService() }
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
    val createAgent: () -> Unit = {
        context.findActivity()?.let { activity ->
            DeviceAuthenticator.authenticate(
                activity = activity,
                title = "AzureQL MCP",
                subtitle = agentAuthSubtitle
                ) { result -> if (result is AuthenticationResult.Success) viewModel.createReadOnlyAgent() }
        }
    }

    McpSettingsContent(
        state = state,
        agents = agents,
        credential = credential,
        error = error,
        agentBusy = agentBusy,
        onBack = onBack,
        onStart = startService,
        onStop = viewModel::stopService,
        onCreateAgent = createAgent,
        onRenameAgent = viewModel::renameAgent,
        onRevokeAgent = viewModel::revokeAgent,
        onDismissCredential = viewModel::dismissCredential,
        onDismissError = viewModel::dismissError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpSettingsContent(
    state: McpServerState,
    agents: List<McpAgent>,
    credential: McpIssuedCredential?,
    error: String?,
    agentBusy: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCreateAgent: () -> Unit,
    onRenameAgent: (McpAgentId, String) -> Unit,
    onRevokeAgent: (McpAgentId) -> Unit,
    onDismissCredential: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val isBusy = state == McpServerState.Starting || state == McpServerState.Stopping
    val isRunning = state is McpServerState.Running
    var renameTarget by remember { mutableStateOf<McpAgent?>(null) }
    var renameName by rememberSaveable(renameTarget?.id?.value) {
        mutableStateOf(renameTarget?.name.orEmpty())
    }

    credential?.let { issued ->
        AlertDialog(
            onDismissRequest = onDismissCredential,
            title = { Text(localizedText("复制 Agent Token", "Copy Agent token")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(localizedText(
                        "Token 只显示这一次。关闭后无法找回，只能撤销并重新创建。",
                        "This token is shown once. After closing, it can only be replaced by revoking and creating another Agent."
                    ))
                    SelectionContainer { Text(issued.token, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                        ClipData.newPlainText("AzureQL MCP Agent token", issued.token)
                    )
                    onDismissCredential()
                }) { Text(localizedText("复制并关闭", "Copy and close")) }
            },
            dismissButton = { TextButton(onClick = onDismissCredential) { Text(localizedText("关闭", "Close")) } }
        )
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(localizedText("操作失败", "Operation failed")) },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(localizedText("确定", "OK")) } }
        )
    }
    renameTarget?.let { agent ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(localizedText("修改 Agent 名称", "Rename Agent")) },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { value ->
                        if (value.length <= MAX_AGENT_NAME_LENGTH) renameName = value
                    },
                    label = { Text(localizedText("名称", "Name")) },
                    supportingText = { Text("${renameName.length}/$MAX_AGENT_NAME_LENGTH") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameAgent(agent.id, renameName)
                        renameTarget = null
                    },
                    enabled = renameName.trim().isNotEmpty() && renameName.trim() != agent.name && !agentBusy
                ) { Text(localizedText("保存", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(statusIcon(state), null, tint = statusColor(state))
                            Text(statusLabel(state), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                        }
                        Text((state as? McpServerState.Running)?.endpoint ?: McpServerConfig().endpoint)
                        if (state is McpServerState.Failed) Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText("Phase 1 只读工具", "Phase 1 read-only tools"), style = MaterialTheme.typography.titleMedium)
                        Text(localizedText(
                            "仅监听 127.0.0.1。提供状态、任务、脚本、依赖、脱敏环境变量和限长日志等 10 个只读工具；不提供执行、写入、删除或青龙凭据。",
                            "Loopback only. Ten read-only tools expose status, tasks, scripts, dependencies, masked environment metadata and bounded logs; execution, writes, deletion and QingLong credentials remain unavailable."
                        ))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("已授权 Agent", "Authorized Agents"), style = MaterialTheme.typography.titleMedium)
                    if (agents.isEmpty()) {
                        Text(localizedText(
                            "先创建并复制一次性 Token，才能启动安全 MCP 服务。",
                            "Create and copy a one-time token before starting the secured MCP service."
                        ))
                    }
                    agents.forEach { agent ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(agent.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(agent.createdAtEpochMs)),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            renameName = agent.name
                                            renameTarget = agent
                                        },
                                        enabled = !agentBusy
                                    ) {
                                        Icon(Icons.Default.Edit, localizedText("修改名称", "Rename"))
                                    }
                                    IconButton(onClick = { onRevokeAgent(agent.id) }, enabled = !isRunning) {
                                        Icon(Icons.Default.Delete, localizedText("撤销", "Revoke"))
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onCreateAgent,
                        enabled = !agentBusy && !isRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (agentBusy) CircularProgressIndicator() else Icon(Icons.Default.Add, null)
                        Text(localizedText("创建只读 Agent", "Create read-only Agent"), Modifier.padding(start = 8.dp))
                    }
                }
            }
            item {
                if (isRunning) {
                    OutlinedButton(onClick = onStop, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.StopCircle, null)
                        Text(localizedText("停止 MCP 服务", "Stop MCP service"), Modifier.padding(start = 8.dp))
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = !isBusy && agents.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isBusy) CircularProgressIndicator() else Icon(Icons.Default.CheckCircle, null)
                        Text(localizedText("启动 MCP 服务", "Start MCP service"), Modifier.padding(start = 8.dp))
                    }
                }
            }
            item { Text("", Modifier.padding(bottom = 8.dp)) }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
