package com.qinglong.feature.env

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvScreen(viewModel: EnvViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccess()
        }
    }

    if (state.showDuplicateDialog && state.duplicateEnv != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("检测到重复变量") },
            text = { Text("已存在同名变量「${state.duplicateEnv!!.name}」，是否仍然新建？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDuplicate) { Text("仍然新建") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicate) { Text("取消") }
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
                        viewModel.exportEnvs()
                    },
                    onImport = {
                        showMenu = false
                        viewModel.importEnvs()
                    }
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
                    Text("暂无变量", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onLongPress = { viewModel.showEditDialog(env) }
                    )
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
    onImport: () -> Unit
) {
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    if (isSearching) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索变量...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    isSearching = false
                    query = ""
                    onSearch("")
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            },
            actions = {
                IconButton(onClick = {
                    isSearching = false
                    onSearch(query)
                }) { Icon(Icons.Default.Search, "搜索") }
            }
        )
    } else {
        TopAppBar(
            title = { Text("环境变量") },
            actions = {
                IconButton(onClick = { isSearching = true }) { Icon(Icons.Default.Search, "搜索") }
                Box {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(
                            text = { Text("新建变量") },
                            onClick = onNewEnv,
                            leadingIcon = { Icon(Icons.Default.Add, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("批量操作") },
                            onClick = onBatchMode,
                            leadingIcon = { Icon(Icons.Default.SelectAll, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("快捷导入") },
                            onClick = onQuickImport,
                            leadingIcon = { Icon(Icons.Default.ContentPaste, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("导出备份") },
                            onClick = onExport,
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("导入备份") },
                            onClick = onImport,
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
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("已选 $selectedCount / $totalCount") },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, "退出批量") }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "全选") }
            IconButton(onClick = onEnable, enabled = selectedCount > 0) { Text("启用", style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = onDisable, enabled = selectedCount > 0) { Text("禁用", style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) { Text("🗑", style = MaterialTheme.typography.labelMedium) }
        }
    )
}
