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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.Switch
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
import com.autopanel.core.mcp.McpAuditEvent
import com.autopanel.core.mcp.McpIssuedCredential
import com.autopanel.core.mcp.McpOperation
import com.autopanel.core.mcp.McpOperationState
import com.autopanel.core.mcp.MAX_AGENT_NAME_LENGTH
import com.autopanel.core.mcp.McpServerConfig
import com.autopanel.core.mcp.McpServerState
import com.autopanel.core.mcp.hasPhase2Access
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
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val auditEvents by viewModel.auditEvents.collectAsStateWithLifecycle()
    val credential by viewModel.credential.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val agentBusy by viewModel.agentOperationInProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val agentAuthSubtitle = localizedText(
        "验证身份后创建 Agent Token；默认只读，可随后授权受控写入与执行",
        "Authenticate to create an Agent token. It is read-only by default and can later receive controlled write and execution access"
    )
    val permissionAuthSubtitle = localizedText(
        "验证身份后修改此 Agent 的写入与执行权限",
        "Authenticate to change this Agent's write and execution permissions"
    )
    val approvalAuthSubtitle = localizedText(
        "验证身份后批准这一次 MCP 操作",
        "Authenticate to approve this MCP operation"
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
    val setPhase2Access: (McpAgentId, Boolean) -> Unit = { agentId, enabled ->
        context.findActivity()?.let { activity ->
            DeviceAuthenticator.authenticate(
                activity = activity,
                title = "AzureQL MCP",
                subtitle = permissionAuthSubtitle
            ) { result ->
                if (result is AuthenticationResult.Success) viewModel.setPhase2Access(agentId, enabled)
            }
        }
    }
    val approveOperation: (String) -> Unit = { operationId ->
        context.findActivity()?.let { activity ->
            DeviceAuthenticator.authenticate(
                activity = activity,
                title = "AzureQL MCP",
                subtitle = approvalAuthSubtitle
            ) { result ->
                if (result is AuthenticationResult.Success) viewModel.approveOperation(operationId)
            }
        }
    }

    McpSettingsContent(
        state = state,
        agents = agents,
        operations = operations,
        auditEvents = auditEvents,
        credential = credential,
        error = error,
        agentBusy = agentBusy,
        onBack = onBack,
        onStart = startService,
        onStop = viewModel::stopService,
        onCreateAgent = createAgent,
        onRenameAgent = viewModel::renameAgent,
        onSetPhase2Access = setPhase2Access,
        onRevokeAgent = viewModel::revokeAgent,
        onApproveOperation = approveOperation,
        onDenyOperation = viewModel::denyOperation,
        onClearAudit = viewModel::clearAudit,
        onDismissCredential = viewModel::dismissCredential,
        onDismissError = viewModel::dismissError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpSettingsContent(
    state: McpServerState,
    agents: List<McpAgent>,
    operations: List<McpOperation>,
    auditEvents: List<McpAuditEvent>,
    credential: McpIssuedCredential?,
    error: String?,
    agentBusy: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCreateAgent: () -> Unit,
    onRenameAgent: (McpAgentId, String) -> Unit,
    onSetPhase2Access: (McpAgentId, Boolean) -> Unit,
    onRevokeAgent: (McpAgentId) -> Unit,
    onApproveOperation: (String) -> Unit,
    onDenyOperation: (String) -> Unit,
    onClearAudit: () -> Unit,
    onDismissCredential: () -> Unit,
    onDismissError: () -> Unit
) {
    val context = LocalContext.current
    val isBusy = state == McpServerState.Starting || state == McpServerState.Stopping
    val isRunning = state is McpServerState.Running
    val pendingOperations = operations.filter { it.state == McpOperationState.WAITING_CONFIRMATION }
    var renameTarget by remember { mutableStateOf<McpAgent?>(null) }
    var auditExpanded by rememberSaveable { mutableStateOf(false) }
    var showClearAuditConfirmation by rememberSaveable { mutableStateOf(false) }
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
    if (showClearAuditConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAuditConfirmation = false },
            title = { Text(localizedText("清除 MCP 审计", "Clear MCP audit")) },
            text = {
                Text(localizedText(
                    "确定清除本机保存的全部 MCP 审计记录吗？此操作不会影响 Agent 权限。",
                    "Clear all locally stored MCP audit records? Agent permissions will not change."
                ))
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearAuditConfirmation = false
                    onClearAudit()
                }) { Text(localizedText("清除", "Clear")) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAuditConfirmation = false }) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
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
            if (pendingOperations.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            localizedText("待确认操作", "Pending confirmations"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        pendingOperations.forEach { operation ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(operation.tool, style = MaterialTheme.typography.titleSmall)
                                    Text(operation.targetSummary, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        localizedText(
                                            "Agent：${operation.agentName}",
                                            "Agent: ${operation.agentName}"
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { onDenyOperation(operation.id) }) {
                                            Text(localizedText("拒绝", "Deny"))
                                        }
                                        TextButton(onClick = { onApproveOperation(operation.id) }) {
                                            Text(localizedText("验证并允许", "Authenticate & allow"))
                                        }
                                    }
                                }
                            }
                        }
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
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Security, null)
                                        Text(
                                            localizedText("受控写入与执行", "Controlled writes & execution"),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    Switch(
                                        checked = agent.hasPhase2Access(),
                                        onCheckedChange = { onSetPhase2Access(agent.id, it) },
                                        enabled = !agentBusy
                                    )
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
                        Text(
                            localizedText("创建 Agent（默认只读）", "Create Agent (read-only by default)"),
                            Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("MCP 审计", "MCP audit"), style = MaterialTheme.typography.titleMedium)
                    if (auditEvents.isEmpty()) {
                        Text(localizedText("暂无调用记录", "No calls recorded"))
                    } else {
                        auditEvents.take(
                            if (auditExpanded) MAX_VISIBLE_AUDIT_EVENTS else COLLAPSED_AUDIT_EVENTS
                        ).forEach { event ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        listOfNotNull(event.tool, event.outcome).joinToString(" · "),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        listOfNotNull(event.agentName, event.targetSummary).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(event.timestampEpochMs)),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (auditEvents.size > COLLAPSED_AUDIT_EVENTS) {
                                TextButton(onClick = { auditExpanded = !auditExpanded }) {
                                    Icon(
                                        if (auditExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                    Text(
                                        localizedText(
                                            if (auditExpanded) "收起" else "展开全部",
                                            if (auditExpanded) "Collapse" else "Show all"
                                        )
                                    )
                                }
                            }
                            TextButton(onClick = { showClearAuditConfirmation = true }) {
                                Text(localizedText("清除审计记录", "Clear audit records"))
                            }
                        }
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

private const val MAX_VISIBLE_AUDIT_EVENTS = 20
private const val COLLAPSED_AUDIT_EVENTS = 3
