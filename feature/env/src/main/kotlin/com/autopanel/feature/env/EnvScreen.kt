package com.autopanel.feature.env

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvScreen(viewModel: EnvViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.let(viewModel::importEnvs)
        }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportEnvs(uri)
    }
    val currentEnglishUi by rememberUpdatedState(isEnglishUi())

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is EnvEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.text, currentEnglishUi)
                )
            }
        }
    }

    if (state.showDuplicateDialog && state.duplicateEnv != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(localizedText("检测到重复变量", "Duplicate variable")) },
            text = {
                Text(
                    localizedText(
                        "已存在同名变量「${state.duplicateEnv!!.name}」，是否仍然新建？",
                        "A variable named “${state.duplicateEnv!!.name}” already exists. Create another one?"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDuplicate) {
                    Text(localizedText("仍然新建", "Create anyway"))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicate) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(localizedText("确认删除", "Delete variables?")) },
            text = {
                Text(
                    localizedText(
                        "确定删除选中的 ${state.selectedIds.size} 个变量吗？此操作不可撤销。",
                        "Delete ${state.selectedIds.size} selected variables? This cannot be undone."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteSelected) {
                    Text(localizedText("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    if (state.showEditDialog) {
        EnvEditDialog(
            env = state.editingEnv,
            onDismiss = viewModel::dismissEditDialog,
            onSubmit = { name, value, remarks ->
                viewModel.submitEdit(name, value, remarks)
            }
        )
    }

    if (state.showImportDialog) {
        EnvImportDialog(
            text = state.importText,
            onTextChange = viewModel::onImportTextChanged,
            onImport = viewModel::parseAndImport,
            onDismiss = viewModel::dismissImportDialog
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                state.isBatchMode -> EnvBatchTopBar(
                    selectedCount = state.selectedIds.size,
                    totalCount = state.envs.size,
                    onBack = viewModel::toggleBatchMode,
                    onSelectAll = viewModel::selectAll,
                    onEnable = viewModel::batchEnableSelected,
                    onDisable = viewModel::batchDisableSelected,
                    onPin = viewModel::batchPinSelected,
                    onUnpin = viewModel::batchUnpinSelected,
                    onDelete = viewModel::batchDeleteSelected
                )
                else -> EnvDefaultTopBar(
                    onMenuClick = { showMenu = true },
                    onSearch = { viewModel.onSearch(it) },
                    showMenu = showMenu,
                    onDismissMenu = { showMenu = false },
                    onNewEnv = {
                        showMenu = false
                        viewModel.showEditDialog(null)
                    },
                    onBatchMode = {
                        showMenu = false
                        viewModel.toggleBatchMode()
                    },
                    onQuickImport = {
                        showMenu = false
                        viewModel.showImportDialog()
                    },
                    onExport = {
                        showMenu = false
                        exportBackupLauncher.launch("envs_backup.json")
                    },
                    onImport = {
                        showMenu = false
                        importBackupLauncher.launch(
                            arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
                        )
                    },
                    isImportingBackup = state.isImportingBackup
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
            if (state.envs.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(localizedText("暂无变量", "No variables"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.envs, key = { it.id ?: it.hashCode().toString() }) { env ->
                    EnvItem(
                        env = env,
                        isBatchMode = state.isBatchMode,
                        isSelected = env.id?.let { state.selectedIds.contains(it) } ?: false,
                        onToggleSelection = { env.id?.let { viewModel.toggleSelection(it) } },
                        onToggleStatus = { viewModel.toggleStatus(env) },
                        onTogglePin = { viewModel.togglePin(env) },
                        onLongPress = { viewModel.showEditDialog(env) }
                    )
                }
            }

            if (state.isImportingBackup) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(localizedText("正在校验并导入环境变量…", "Validating and importing variables…"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvDefaultTopBar(
    onMenuClick: () -> Unit,
    onSearch: (String) -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onNewEnv: () -> Unit,
    onBatchMode: () -> Unit,
    onQuickImport: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    isImportingBackup: Boolean
) {
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(isSearching, query) {
        if (isSearching) {
            // The repository search is remote. Debounce typing rather than requiring
            // a second tap or issuing a request for each keystroke.
            delay(300)
            onSearch(query)
        }
    }

    if (isSearching) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(localizedText("搜索变量...", "Search variables…")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    isSearching = false
                    query = ""
                    onSearch("")
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back")) }
            },
            actions = {
                IconButton(onClick = {
                    // Keep the explicit action as an immediate submit/close affordance.
                    // The keyed effect above already applies text changes while typing.
                    isSearching = false
                    onSearch(query)
                }) { Icon(Icons.Default.Search, localizedText("搜索", "Search")) }
            }
        )
    } else {
        TopAppBar(
            title = { Text(localizedText("环境变量", "Variables")) },
            actions = {
                IconButton(onClick = { isSearching = true }) {
                    Icon(Icons.Default.Search, localizedText("搜索", "Search"))
                }
                Box {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.MoreVert, localizedText("更多", "More"))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(
                            text = { Text(localizedText("新建变量", "New variable")) },
                            onClick = onNewEnv,
                            leadingIcon = { Icon(Icons.Default.Add, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("批量操作", "Batch actions")) },
                            onClick = onBatchMode,
                            leadingIcon = { Icon(Icons.Default.SelectAll, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("快捷导入", "Quick import")) },
                            onClick = onQuickImport,
                            leadingIcon = { Icon(Icons.Default.ContentPaste, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("导出备份", "Export backup")) },
                            onClick = onExport,
                            enabled = !isImportingBackup,
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("导入备份", "Import backup")) },
                            onClick = onImport,
                            enabled = !isImportingBackup,
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvBatchTopBar(
    selectedCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onDelete: () -> Unit
) {
    var showMore by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(localizedText("已选 $selectedCount / $totalCount", "$selectedCount / $totalCount selected"))
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, localizedText("退出批量", "Exit batch mode")) }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, localizedText("全选", "Select all")) }
            IconButton(onClick = onEnable, enabled = selectedCount > 0) {
                Icon(Icons.Default.CheckCircle, localizedText("启用", "Enable"))
            }
            IconButton(onClick = onDisable, enabled = selectedCount > 0) {
                Icon(Icons.Default.Block, localizedText("禁用", "Disable"))
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, localizedText("删除", "Delete"), tint = MaterialTheme.colorScheme.error)
            }
            Box {
                IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Default.MoreVert, localizedText("更多", "More"))
                }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(
                        text = { Text(localizedText("置顶", "Pin")) },
                        onClick = { showMore = false; onPin() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.PushPin, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(localizedText("取消置顶", "Unpin")) },
                        onClick = { showMore = false; onUnpin() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.PushPin, null) }
                    )
                }
            }
        }
    )
}
