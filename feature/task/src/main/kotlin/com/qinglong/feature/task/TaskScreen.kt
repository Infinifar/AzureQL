package com.qinglong.feature.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
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

    if (state.showDuplicateDialog && state.duplicateTask != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("检测到重复任务") },
            text = {
                Text("已存在相同名称和命令的任务「${state.duplicateTask!!.name}」，是否仍然新建？")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDuplicate) { Text("仍然新建") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicate) { Text("取消") }
            }
        )
    }

    if (state.showLogSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLog,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("任务日志", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    state.logContent ?: "加载中...",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (state.showEditDialog) {
        TaskEditDialog(
            task = state.editingTask,
            onDismiss = viewModel::dismissEditDialog,
            onSubmit = { name, cmd, sched -> viewModel.submitEdit(name, cmd, sched) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                state.isBatchMode -> BatchTopBar(
                    selectedCount = state.selectedIds.size,
                    totalCount = state.tasks.size,
                    onBack = viewModel::toggleBatchMode,
                    onSelectAll = viewModel::selectAll,
                    onRun = viewModel::batchRunSelected,
                    onStop = viewModel::batchStopSelected,
                    onEnable = viewModel::batchEnableSelected,
                    onDisable = viewModel::batchDisableSelected,
                    onPin = viewModel::batchPinSelected,
                    onUnpin = viewModel::batchUnpinSelected,
                    onDelete = viewModel::batchDeleteSelected
                )
                else -> DefaultTopBar(
                    onMenuClick = { showMenu = true },
                    onSearch = { viewModel.onSearch(it) },
                    showMenu = showMenu,
                    onDismissMenu = { showMenu = false },
                    onNewTask = {
                        showMenu = false
                        viewModel.showEditDialog(null)
                    },
                    onBatchMode = {
                        showMenu = false
                        viewModel.toggleBatchMode()
                    },
                    onExport = {
                        showMenu = false
                        viewModel.exportTasks()
                    },
                    onImport = {
                        showMenu = false
                        viewModel.importTasks()
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
            if (state.tasks.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tasks, key = { it.id ?: it.hashCode().toString() }) { task ->
                    TaskItem(
                        task = task,
                        isBatchMode = state.isBatchMode,
                        isSelected = task.id?.let { state.selectedIds.contains(it) } ?: false,
                        onToggleSelection = { task.id?.let { viewModel.toggleSelection(it) } },
                        onRun = { viewModel.runTask(task) },
                        onStop = { viewModel.stopTask(task) },
                        onClickTitle = { viewModel.showLog(task) },
                        onLongPressTitle = { },
                        onLongPress = { viewModel.showEditDialog(task) }
                    )
                }

                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (state.hasMore && !state.isLoadingMore) {
                    item {
                        TextButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("加载更多") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    onMenuClick: () -> Unit,
    onSearch: (String) -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onNewTask: () -> Unit,
    onBatchMode: () -> Unit,
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
                    placeholder = { Text("搜索任务...") },
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
            title = { Text("任务管理") },
            actions = {
                IconButton(onClick = { isSearching = true }) { Icon(Icons.Default.Search, "搜索") }
                Box {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(
                            text = { Text("新建任务") },
                            onClick = onNewTask,
                            leadingIcon = { Icon(Icons.Default.Add, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("批量操作") },
                            onClick = onBatchMode,
                            leadingIcon = { Icon(Icons.Default.SelectAll, null) }
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
private fun BatchTopBar(
    selectedCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onDelete: () -> Unit
) {
    var showMore by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("已选 $selectedCount / $totalCount") },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, "退出批量") }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "全选") }
            IconButton(onClick = onRun, enabled = selectedCount > 0) { Icon(Icons.Default.PlayArrow, "执行") }
            IconButton(onClick = onStop, enabled = selectedCount > 0) { Icon(Icons.Default.Stop, "停止") }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
            Box {
                IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, "更多") }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(
                        text = { Text("启用") },
                        onClick = { showMore = false; onEnable() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("禁用") },
                        onClick = { showMore = false; onDisable() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.Block, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("置顶") },
                        onClick = { showMore = false; onPin() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.PushPin, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("取消置顶") },
                        onClick = { showMore = false; onUnpin() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.PushPin, null) }
                    )
                }
            }
        }
    )
}
