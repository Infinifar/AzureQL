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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.BackupModule
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import com.autopanel.core.ui.i18n.localizedText
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onRestoreCompleted: () -> Unit,
    onOpenNetworkStorageSettings: () -> Unit,
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
    var pendingAction by remember { mutableStateOf<BackupAction?>(null) }
    var showNetworkProviderDialog by remember { mutableStateOf(false) }
    var networkProviderPurpose by remember { mutableStateOf(NetworkProviderPurpose.EXPORT) }

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
        pendingAction?.launch(
            launchExport = { exportLauncher.launch(it) },
            launchImport = { importLauncher.launch(it) },
            launchNetworkExport = viewModel::exportBackupToNetwork,
            launchNetworkRestore = viewModel::loadNetworkBackups
        )
        pendingAction = null
    }

    val launchAction: (BackupAction) -> Unit = { action ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action.launch(
                launchExport = { exportLauncher.launch(it) },
                launchImport = { importLauncher.launch(it) },
                launchNetworkExport = viewModel::exportBackupToNetwork,
                launchNetworkRestore = viewModel::loadNetworkBackups
            )
        }
    }

    val requestNetworkExport: () -> Unit = {
        val providers = state.configuredNetworkProviders
        if (providers.isNotEmpty()) {
            networkProviderPurpose = NetworkProviderPurpose.EXPORT
            showNetworkProviderDialog = true
        }
    }
    val requestNetworkRestore: () -> Unit = {
        val providers = state.configuredNetworkProviders
        if (providers.size == 1) {
            launchAction(BackupAction.RestoreNetwork(providers.first()))
        } else if (providers.size > 1) {
            networkProviderPurpose = NetworkProviderPurpose.RESTORE
            showNetworkProviderDialog = true
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

    if (showNetworkProviderDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkProviderDialog = false },
            title = {
                Text(
                    if (networkProviderPurpose == NetworkProviderPurpose.EXPORT) {
                        localizedText("选择导出位置", "Choose export destination")
                    } else {
                        localizedText("选择备份来源", "Choose backup source")
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (NetworkStorageProvider.WEBDAV in state.configuredNetworkProviders) {
                        OutlinedButton(
                            onClick = {
                                showNetworkProviderDialog = false
                                launchAction(networkProviderPurpose.action(NetworkStorageProvider.WEBDAV))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("WebDAV") }
                    }
                    if (NetworkStorageProvider.S3 in state.configuredNetworkProviders) {
                        OutlinedButton(
                            onClick = {
                                showNetworkProviderDialog = false
                                launchAction(networkProviderPurpose.action(NetworkStorageProvider.S3))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("S3") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNetworkProviderDialog = false }) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    if (state.showNetworkRestorePicker) {
        NetworkRestorePickerDialog(
            state = state,
            onDismiss = viewModel::dismissNetworkRestorePicker,
            onRefresh = { state.networkRestoreProvider?.let(viewModel::loadNetworkBackups) },
            onSelect = viewModel::importNetworkBackup
        )
    }

    BackupScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleModule = viewModel::toggleModule,
        onExport = { launchAction(BackupAction.ExportLocal) },
        onExportNetwork = requestNetworkExport,
        onOpenNetworkStorageSettings = onOpenNetworkStorageSettings,
        onImport = { launchAction(BackupAction.Import) },
        onImportNetwork = requestNetworkRestore,
        onMaxImportSizeChanged = viewModel::onMaxImportSizeChanged,
        onCancelTransfer = viewModel::cancelTransfer
    )
}

private sealed interface BackupAction {
    data object ExportLocal : BackupAction
    data class ExportNetwork(val provider: NetworkStorageProvider) : BackupAction
    data class RestoreNetwork(val provider: NetworkStorageProvider) : BackupAction
    data object Import : BackupAction
}

private fun BackupAction.launch(
    launchExport: (String) -> Unit,
    launchImport: (Array<String>) -> Unit,
    launchNetworkExport: (NetworkStorageProvider) -> Unit,
    launchNetworkRestore: (NetworkStorageProvider) -> Unit
) {
    when (this) {
        BackupAction.ExportLocal -> {
            val suffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            launchExport("azureql_backup_$suffix.tgz")
        }
        is BackupAction.ExportNetwork -> launchNetworkExport(provider)
        is BackupAction.RestoreNetwork -> launchNetworkRestore(provider)
        BackupAction.Import -> launchImport(
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
    onExportNetwork: () -> Unit = {},
    onOpenNetworkStorageSettings: () -> Unit = {},
    onImport: () -> Unit,
    onImportNetwork: () -> Unit = {},
    onMaxImportSizeChanged: (String) -> Unit = {},
    onCancelTransfer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAllExportModules by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(localizedText("备份与恢复", "Backup and restore")) },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        localizedText("选择导出内容", "Choose export content"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { showAllExportModules = !showAllExportModules }) {
                        Text(
                            if (showAllExportModules) localizedText("收起", "Collapse")
                            else localizedText("展开全部", "Show all")
                        )
                    }
                }
                Text(
                    localizedText(
                        "备份由当前青龙服务端生成并直接保存到你选择的位置。基础设置始终包含。",
                        "The current QingLong server creates the backup and saves it directly to your chosen location. Base settings are always included."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BackupModule.entries
                    .filter {
                        showAllExportModules || it in DEFAULT_VISIBLE_EXPORT_MODULES
                    }
                    .forEach { module ->
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
                    Text(
                        localizedText("导出到本机存储", "Export to local storage"),
                        Modifier.padding(start = 8.dp)
                    )
                }
                OutlinedButton(
                    onClick = onExportNetwork,
                    enabled = state.canExportToNetwork,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, null)
                    Text(
                        localizedText("导出到网络存储", "Export to network storage"),
                        Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenNetworkStorageSettings,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Storage, null)
                    val providers = state.configuredNetworkProviders
                    val summary = when {
                        providers.size == 2 -> localizedText("WebDAV 与 S3 已配置", "WebDAV and S3 configured")
                        NetworkStorageProvider.WEBDAV in providers -> localizedText("WebDAV 已配置", "WebDAV configured")
                        NetworkStorageProvider.S3 in providers -> localizedText("S3 已配置", "S3 configured")
                        else -> localizedText("配置 WebDAV 或 S3", "Configure WebDAV or S3")
                    }
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(localizedText("网络存储设置", "Network storage settings"))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                OutlinedButton(
                    onClick = onImportNetwork,
                    enabled = state.canRestoreFromNetwork,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, null)
                    Text(
                        localizedText("从网络存储选择备份", "Choose backup from network storage"),
                        Modifier.padding(start = 8.dp)
                    )
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
internal fun NetworkRestorePickerDialog(
    state: BackupUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (NetworkBackupFile) -> Unit
) {
    val providerName = when (state.networkRestoreProvider) {
        NetworkStorageProvider.WEBDAV -> "WebDAV"
        NetworkStorageProvider.S3 -> "S3"
        null -> ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(localizedText("选择网络备份", "Choose network backup"), Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !state.isLoadingNetworkBackups) {
                    Icon(Icons.Default.Refresh, localizedText("刷新", "Refresh"))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    localizedText(
                        "来源：$providerName。选择后先下载并校验，覆盖数据前仍会再次确认。",
                        "Source: $providerName. The archive is downloaded and validated first; you will confirm again before data is overwritten."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    state.isLoadingNetworkBackups -> Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    state.networkBackupListError != null -> Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            localizedMessage(state.networkBackupListError, isEnglishUi()),
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = onRefresh) {
                            Text(localizedText("重试", "Retry"))
                        }
                    }
                    state.networkBackups.isEmpty() -> Text(
                        localizedText("当前目录中没有可恢复的 .tgz/.gz 备份。", "No restorable .tgz/.gz backups were found in this directory."),
                        modifier = Modifier.padding(vertical = 32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(
                            items = state.networkBackups,
                            key = { "${it.provider}:${it.remoteId}" }
                        ) { backup ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(backup) }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(backup.fileName, style = MaterialTheme.typography.bodyLarge)
                                val metadata = buildList {
                                    backup.sizeBytes?.let { add(formatBytes(it)) }
                                    backup.modifiedAtEpochMillis?.let { add(formatBackupTime(it)) }
                                }.joinToString(" · ")
                                if (metadata.isNotEmpty()) {
                                    Text(
                                        metadata,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("取消", "Cancel")) }
        }
    )
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
        BackupOperation.UPLOADING_NETWORK -> localizedText(
            "正在上传到网络存储…",
            "Uploading to network storage…"
        )
        BackupOperation.DOWNLOADING_NETWORK -> localizedText(
            "正在从网络存储下载备份…",
            "Downloading backup from network storage…"
        )
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
    BackupModule.BASE -> localizedText("基础设置", "Base settings")
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
private fun formatBackupTime(epochMillis: Long): String {
    val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date(epochMillis))
}

private enum class NetworkProviderPurpose { EXPORT, RESTORE }

private fun NetworkProviderPurpose.action(provider: NetworkStorageProvider): BackupAction = when (this) {
    NetworkProviderPurpose.EXPORT -> BackupAction.ExportNetwork(provider)
    NetworkProviderPurpose.RESTORE -> BackupAction.RestoreNetwork(provider)
}

private val DEFAULT_VISIBLE_EXPORT_MODULES = setOf(
    BackupModule.BASE,
    BackupModule.CONFIG,
    BackupModule.SCRIPTS
)

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
