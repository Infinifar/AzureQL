package com.autopanel.feature.backup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.BackupModule
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import com.autopanel.core.ui.i18n.localizedText
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
    var pendingPicker by remember { mutableStateOf<BackupPickerAction?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        uri?.let {
            context.persistUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.exportBackup(it.toString())
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.persistUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.importBackup(it.toString(), context.backupLength(it))
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingPicker?.launch(
            launchExport = { exportLauncher.launch(it) },
            launchImport = { importLauncher.launch(it) }
        )
        pendingPicker = null
    }

    val launchPicker: (BackupPickerAction) -> Unit = { action ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingPicker = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action.launch(
                launchExport = { exportLauncher.launch(it) },
                launchImport = { importLauncher.launch(it) }
            )
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
        onExport = { launchPicker(BackupPickerAction.EXPORT) },
        onImport = { launchPicker(BackupPickerAction.IMPORT) },
        onMaxImportSizeChanged = viewModel::onMaxImportSizeChanged,
        onCancelTransfer = viewModel::cancelTransfer
    )
}

private enum class BackupPickerAction { EXPORT, IMPORT }

private fun BackupPickerAction.launch(
    launchExport: (String) -> Unit,
    launchImport: (Array<String>) -> Unit
) {
    when (this) {
        BackupPickerAction.EXPORT -> {
            val suffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            launchExport("azureql_backup_$suffix.tgz")
        }
        BackupPickerAction.IMPORT -> launchImport(
            arrayOf("application/gzip", "application/x-gzip", "application/octet-stream")
        )
    }
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
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(localizedText("数据备份与恢复", "Backup and restore")) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
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
                Text(localizedText("选择导出内容", "Choose export content"), style = MaterialTheme.typography.titleMedium)
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
                Button(onClick = onExport, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null)
                    Text(localizedText("导出到文件", "Export to file"), Modifier.padding(start = 8.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text(localizedText("从文件恢复", "Restore from file"), style = MaterialTheme.typography.titleMedium)
                Text(
                    localizedText(
                        "仅选择由青龙官方导出功能生成的 .tgz 文件。上传不会立即覆盖数据，应用会在下一步再次要求确认。",
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
                        Text(localizedText("默认 1024 MB；大小未知时上传期间仍会持续显示进度", "Default: 1024 MB. Progress remains visible when file size is unknown."))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = onImport, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Upload, null)
                    Text(localizedText("选择备份文件", "Choose backup file"), Modifier.padding(start = 8.dp))
                }
            }
        }

        state.operation?.let {
            BackupProgressOverlay(
                state = state,
                onCancelTransfer = onCancelTransfer,
                onContinueInBackground = onBack
            )
        }
    }
}

@Composable
private fun BackupProgressOverlay(
    state: BackupUiState,
    onCancelTransfer: () -> Unit,
    onContinueInBackground: () -> Unit
) {
    val operation = requireNotNull(state.operation)
    val stage = when (operation) {
        BackupOperation.EXPORTING -> localizedText("正在生成并保存备份…", "Creating and saving backup…")
        BackupOperation.VALIDATING_IMPORT -> localizedText("正在校验备份文件…", "Validating backup…")
        BackupOperation.IMPORTING -> localizedText("正在上传备份…", "Uploading backup…")
        BackupOperation.ACTIVATING_RESTORE -> localizedText("正在激活恢复数据…", "Activating restored data…")
        BackupOperation.WAITING_FOR_SERVICE -> localizedText(
            "正在等待青龙服务恢复… ${state.healthCheckAttempt}/30",
            "Waiting for QingLong… ${state.healthCheckAttempt}/30"
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.54f))
            .padding(24.dp)
            .testTag("backup_progress_overlay")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = stage
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(stage, style = MaterialTheme.typography.titleMedium)
                val progress = state.progress
                if (progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                if (operation != BackupOperation.VALIDATING_IMPORT && state.transferredBytes > 0) {
                    Text(
                        buildString {
                            append(formatBytes(state.transferredBytes))
                            state.totalBytes?.let { append(" / ").append(formatBytes(it)) }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    localizedText(
                        "任务已在后台安全运行，可以留在此处查看进度，也可以离开此页面。",
                        "The task is safely running in the background. Stay to watch progress or leave this page."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (operation.canCancel) {
                        OutlinedButton(onClick = onCancelTransfer) {
                            Text(localizedText("取消传输", "Cancel transfer"))
                        }
                    }
                    Button(onClick = onContinueInBackground) {
                        Text(localizedText("在后台继续", "Continue in background"))
                    }
                }
            }
        }
    }
}

private fun Context.persistUriPermission(uri: Uri, flags: Int) {
    runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
}

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
