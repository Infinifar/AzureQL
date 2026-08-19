package com.autopanel.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.AppInfo
import com.autopanel.core.model.AppScopes
import kotlinx.coroutines.launch

private const val PROJECT_URL = "https://github.com/yisilan83/autopanel-android"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    clientVersion: String,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    fun copyToClipboard(label: String, value: String?) {
        val v = value ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, v))
        scope.launch { snackbarHostState.showSnackbar("$label 已复制") }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
    }

    if (state.showPasswordDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPasswordDialog,
            title = { Text("修改密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.oldPassword, onValueChange = viewModel::onOldPasswordChanged,
                        label = { Text("当前用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.newPassword, onValueChange = viewModel::onNewPasswordChanged,
                        label = { Text("新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::changePassword,
                    enabled = state.oldPassword.isNotEmpty() && state.newPassword.isNotEmpty()
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPasswordDialog) { Text("取消") } }
        )
    }

    if (state.showAppDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAppDialog,
            title = { Text(if (state.editingApp == null) "新建应用" else "编辑应用") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = state.editAppName, onValueChange = viewModel::onAppNameChanged,
                        label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("权限", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppScopes.ALL.forEach { scope ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = scope in state.editAppScopes,
                                role = Role.Checkbox,
                                onValueChange = { viewModel.toggleAppScope(scope) }
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = scope in state.editAppScopes,
                                onCheckedChange = null
                            )
                            Text(AppScopes.label(scope), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveApp, enabled = state.editAppName.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissAppDialog) { Text("取消") } }
        )
    }

    state.confirmDeleteApp?.let { app ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("删除应用") },
            text = { Text("确定要删除应用「${app.name ?: "--"}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteApp,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteConfirm) { Text("取消") } }
        )
    }

    state.confirmResetApp?.let { app ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResetConfirm,
            title = { Text("重置密钥") },
            text = { Text("确定要重置应用「${app.name ?: "--"}」的 Client Secret 吗？重置后旧密钥将立即失效。") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmResetApp,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("重置") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissResetConfirm) { Text("取消") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "退出登录") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // 关于（放在顶部，一打开即可见）
            SectionHeader("关于", false, onClick = { uriHandler.openUri(PROJECT_URL) })
            InfoRow("客户端版本", clientVersion)
            InfoRow("服务端版本", state.serverVersion ?: "未知")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("系统配置", state.configExpanded, viewModel::toggleConfigExpanded,
                action = { IconButton(onClick = viewModel::loadSystemConfig) { Icon(Icons.Default.Refresh, "刷新") } })
            AnimatedVisibility(state.configExpanded) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (state.isLoadingConfig) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    else {
                        OutlinedTextField(
                            value = state.editLogFrequency, onValueChange = viewModel::onLogFrequencyChanged,
                            label = { Text("日志删除频率 (天)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.editConcurrency, onValueChange = viewModel::onConcurrencyChanged,
                            label = { Text("并发数") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::saveSystemConfig, modifier = Modifier.fillMaxWidth()) {
                            Text("保存配置")
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("应用设置", state.appsExpanded, viewModel::toggleAppsExpanded,
                action = { IconButton(onClick = viewModel::loadApps) { Icon(Icons.Default.Refresh, "刷新") } })
            AnimatedVisibility(state.appsExpanded) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    when {
                        state.isLoadingApps -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                        state.apps.isEmpty() -> Text("暂无应用", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> {
                            state.apps.forEach { app ->
                                AppCard(
                                    app = app,
                                    onEdit = { viewModel.showEditApp(app) },
                                    onResetSecret = { viewModel.requestResetSecret(app) },
                                    onDelete = { viewModel.requestDeleteApp(app) },
                                    onCopyClientId = { copyToClipboard("Client ID", app.clientId) },
                                    onCopyClientSecret = { copyToClipboard("Client Secret", app.clientSecret) }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::showCreateApp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("新建应用")
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("登录日志", state.logsExpanded, viewModel::toggleLogsExpanded)
            AnimatedVisibility(state.logsExpanded) {
                Column {
                    if (state.isLoadingLogs) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    else if (state.loginLogs.isEmpty()) Text("暂无记录", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        state.loginLogs.take(20).forEach { log ->
                            Card(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    val addr = log.address
                                    if (!addr.isNullOrBlank()) Text(addr, style = MaterialTheme.typography.bodyMedium)
                                    val ip = log.ip
                                    if (!ip.isNullOrBlank()) {
                                        Text(
                                            ip,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val t = log.time
                                    if (!t.isNullOrBlank()) Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        log.statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (log.status == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("账号", false, onClick = viewModel::showPasswordDialog)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AppCard(
    app: AppInfo,
    onEdit: () -> Unit,
    onResetSecret: () -> Unit,
    onDelete: () -> Unit,
    onCopyClientId: () -> Unit,
    onCopyClientSecret: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.name ?: "--", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
                IconButton(onClick = onResetSecret) { Icon(Icons.Default.Key, "重置密钥") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
            }
            SecretRow("Client ID", app.clientId, onCopy = onCopyClientId, isSecret = false)
            SecretRow("Client Secret", app.clientSecret, onCopy = onCopyClientSecret, isSecret = true)
            Text(
                "权限：" + (app.scopes?.joinToString("、") { AppScopes.label(it) } ?: "--"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SecretRow(
    label: String,
    value: String?,
    onCopy: () -> Unit,
    isSecret: Boolean
) {
    var visible by remember { mutableStateOf(false) }
    val displayValue = when {
        value == null -> "--"
        isSecret && !visible -> "••••••••••••"
        else -> value
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onCopy),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayValue, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        if (isSecret) {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if (visible) "隐藏" else "显示",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onClick: () -> Unit, action: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        action?.invoke()
        Icon(
            if (!expanded) Icons.Default.ChevronRight else Icons.Default.KeyboardArrowDown,
            null, tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
