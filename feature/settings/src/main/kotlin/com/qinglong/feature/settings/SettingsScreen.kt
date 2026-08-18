package com.qinglong.feature.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogout: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

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

            SectionHeader("登录日志", state.logsExpanded, viewModel::toggleLogsExpanded)
            AnimatedVisibility(state.logsExpanded) {
                if (state.isLoadingLogs) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                else if (state.loginLogs.isEmpty()) Text("暂无记录", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    state.loginLogs.take(20).forEach { log ->
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    val addr = log.address
                                    if (!addr.isNullOrBlank()) Text(addr, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.width(8.dp))
                                    val ip = log.ip
                                    if (!ip.isNullOrBlank()) Text(ip, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val t = log.time
                                if (!t.isNullOrBlank()) Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val st = log.statusText
                                Text(st, style = MaterialTheme.typography.labelSmall, color = if (log.status == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("账号", false, onClick = viewModel::showPasswordDialog)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 关于
            SectionHeader("关于", false, onClick = {})
            InfoRow("客户端版本", "1.0.0")
            InfoRow("服务端版本", state.serverVersion ?: "未知")
            InfoRow(
                label = "项目地址",
                value = "https://github.com/yisilan83/qinglong-app-android",
                onClick = { uriHandler.openUri("https://github.com/yisilan83/qinglong-app-android") }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        val valueModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = valueModifier
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
