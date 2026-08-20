package com.autopanel.feature.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.BackupModule
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onRestoreCompleted: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnRestoreCompleted by rememberUpdatedState(onRestoreCompleted)
    val restoreCompletedMessage = localizedText(
        "服务已恢复，请重新登录",
        "Service restored. Sign in again."
    )
    val currentRestoreCompletedMessage by rememberUpdatedState(restoreCompletedMessage)
    val currentEnglishUi by rememberUpdatedState(isEnglishUi())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        uri?.let {
            context.openBackupOutput(it)?.let { output ->
                viewModel.exportBackup(output) {
                    context.contentResolver.delete(it, null, null)
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val input = context.contentResolver.openInputStream(it)
            if (input != null) {
                viewModel.importBackup(input, context.backupLength(it))
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.value, currentEnglishUi)
                )
                BackupEvent.RestoreCompleted -> {
                    snackbarHostState.showSnackbar(currentRestoreCompletedMessage)
                    currentOnRestoreCompleted()
                }
            }
        }
    }

    if (state.showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreConfirmation,
            title = { Text(localizedText("覆盖服务端数据？", "Overwrite server data?")) },
            text = {
                Text(
                    localizedText(
                        "上传已完成。继续后将覆盖当前青龙数据并重启服务。此操作不可撤销，" +
                            "请确认已另行保存当前备份。恢复完成后需要重新登录。",
                        "Upload complete. Continuing will overwrite QingLong data and restart the service. " +
                            "This cannot be undone. Keep a separate backup first. You must sign in again afterward."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(localizedText("覆盖并重启", "Overwrite and restart"))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestoreConfirmation) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    BackupScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleModule = viewModel::toggleModule,
        onExport = {
            val suffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            exportLauncher.launch("qinglong_backup_$suffix.tgz")
        },
        onImport = {
            importLauncher.launch(
                arrayOf("application/gzip", "application/x-gzip", "application/octet-stream")
            )
        },
        onMaxImportSizeChanged = viewModel::onMaxImportSizeChanged,
        onCancelTransfer = viewModel::cancelTransfer
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupScreenContent(
    state: BackupUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleModule: (BackupModule) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onMaxImportSizeChanged: (String) -> Unit = {},
    onCancelTransfer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(localizedText("数据备份与恢复", "Backup and restore")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            localizedText("返回", "Back")
                        )
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
            Text(
                localizedText("选择导出内容", "Choose export content"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                localizedText(
                    "备份由当前青龙服务端生成并直接保存到你选择的位置。基础数据始终包含。",
                    "The current QingLong server creates the backup and saves it directly to your chosen location. Base data is always included."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BackupModule.entries.forEach { module ->
                val checked = module in state.selectedModules
                val enabled = module != BackupModule.BASE && !state.isBusy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_module_${module.apiValue}")
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onToggleModule(module) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    Column(Modifier.weight(1f)) {
                        Text(module.localizedDisplayName(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            module.localizedDescription(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onExport,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Text(localizedText("导出到文件", "Export to file"), Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(localizedText("从文件恢复", "Restore from file"), style = MaterialTheme.typography.titleMedium)
            Text(
                localizedText(
                    "仅选择由青龙官方导出功能生成的 .tgz 文件。上传不会立即覆盖数据，" +
                        "应用会在下一步再次要求确认。",
                    "Select only a .tgz created by QingLong's official export. Uploading does not overwrite data immediately; you will confirm in the next step."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.maxImportSizeMb,
                onValueChange = onMaxImportSizeChanged,
                label = { Text(localizedText("最大备份大小（MB）", "Maximum backup size (MB)")) },
                supportingText = {
                    Text(
                        localizedText(
                            "默认 1024 MB；文件大小未知时会在上传过程中显示进度",
                            "Default: 1024 MB. Progress is shown during upload when size is unknown."
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = onImport,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, null)
                Text(localizedText("选择备份文件", "Choose backup file"), Modifier.padding(start = 8.dp))
            }

            if (state.operation != null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (state.operation) {
                                BackupOperation.EXPORTING -> localizedText(
                                    "正在生成并保存备份…",
                                    "Creating and saving backup…"
                                )
                                BackupOperation.VALIDATING_IMPORT -> localizedText(
                                    "正在校验备份文件…",
                                    "Validating backup…"
                                )
                                BackupOperation.IMPORTING -> localizedText(
                                    "正在上传备份…",
                                    "Uploading backup…"
                                )
                                BackupOperation.ACTIVATING_RESTORE -> localizedText(
                                    "正在激活恢复数据…",
                                    "Activating restored data…"
                                )
                                BackupOperation.WAITING_FOR_SERVICE ->
                                    localizedText(
                                        "正在等待青龙服务恢复… ${state.healthCheckAttempt}/30",
                                        "Waiting for QingLong… ${state.healthCheckAttempt}/30"
                                    )
                            }
                        )
                        if (state.operation.canCancel) {
                            Spacer(Modifier.height(8.dp))
                            val progress = state.progress
                            if (progress == null) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (state.operation != BackupOperation.VALIDATING_IMPORT) {
                                Text(
                                    buildString {
                                        append(formatBytes(state.transferredBytes))
                                        state.totalBytes?.let { append(" / ").append(formatBytes(it)) }
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(onClick = onCancelTransfer) {
                                Text(
                                    if (state.operation == BackupOperation.VALIDATING_IMPORT) {
                                        localizedText("取消导入", "Cancel import")
                                    } else {
                                        localizedText("取消传输", "Cancel transfer")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.openBackupOutput(uri: Uri) =
    runCatching { contentResolver.openOutputStream(uri, "rwt") }.getOrNull()
        ?: contentResolver.openOutputStream(uri, "w")

private fun Context.backupLength(uri: Uri): Long? = runCatching {
    contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        descriptor.length.takeIf { it >= 0 }
    }
}.getOrNull()

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(Locale.US, bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun BackupModule.localizedDisplayName(): String = when (this) {
    BackupModule.BASE -> localizedText("基础数据", "Base data")
    BackupModule.CONFIG -> localizedText("配置文件", "Configuration")
    BackupModule.SCRIPTS -> localizedText("脚本文件", "Scripts")
    BackupModule.LOGS -> localizedText("日志文件", "Task logs")
    BackupModule.DEPENDENCIES -> localizedText("依赖文件", "Dependencies")
    BackupModule.SYSTEM_LOGS -> localizedText("系统日志", "System logs")
    BackupModule.DEPENDENCY_CACHE -> localizedText("依赖缓存", "Dependency cache")
    BackupModule.REMOTE_SCRIPT_CACHE -> localizedText("远程脚本缓存", "Remote script cache")
    BackupModule.REPOSITORY_CACHE -> localizedText("远程仓库缓存", "Repository cache")
    BackupModule.SSH_CACHE -> localizedText("SSH 文件缓存", "SSH cache")
}

@Composable
private fun BackupModule.localizedDescription(): String = when (this) {
    BackupModule.BASE -> localizedText("数据库与上传文件（必选）", "Database and uploads (required)")
    BackupModule.CONFIG -> localizedText("config 目录", "config directory")
    BackupModule.SCRIPTS -> localizedText("scripts 目录", "scripts directory")
    BackupModule.LOGS -> localizedText("任务运行日志", "Task execution logs")
    BackupModule.DEPENDENCIES -> localizedText("已安装依赖", "Installed dependencies")
    BackupModule.SYSTEM_LOGS -> localizedText("青龙系统日志", "QingLong system logs")
    BackupModule.DEPENDENCY_CACHE -> localizedText("Node.js 与 Python 缓存", "Node.js and Python caches")
    BackupModule.REMOTE_SCRIPT_CACHE -> localizedText("下载的远程脚本", "Downloaded remote scripts")
    BackupModule.REPOSITORY_CACHE -> localizedText("拉取的仓库数据", "Cloned repository data")
    BackupModule.SSH_CACHE -> localizedText("SSH 相关文件", "SSH-related files")
}
