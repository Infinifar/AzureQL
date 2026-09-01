package com.autopanel.feature.task

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    onOpenScript: (String) -> Unit = {},
    viewModel: TaskViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.let(viewModel::importTasks)
        }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportTasks(uri)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val englishUi = isEnglishUi()
    val currentEnglishUi by rememberUpdatedState(isEnglishUi())
    var showMenu by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showLabelManager by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is TaskEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.text, currentEnglishUi)
                )
            }
        }
    }

    if (state.showDuplicateDialog && state.duplicateTask != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(localizedText("检测到重复任务", "Duplicate task")) },
            text = {
                Text(
                    localizedText(
                        "已存在相同名称和命令的任务「${state.duplicateTask!!.name}」，是否仍然新建？",
                        "A task with the same name and command already exists: “${state.duplicateTask!!.name}”. Create another one?"
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

    if (showLabelManager) {
        TaskLabelManagementDialog(
            labels = state.labelSummaries,
            isLoading = state.isLoadingLabelSummaries,
            isUpdating = state.isUpdatingLabel,
            onRename = viewModel::renameLabel,
            onDelete = viewModel::deleteUnusedLabel,
            onDismiss = { showLabelManager = false }
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(localizedText("确认删除", "Delete tasks?")) },
            text = {
                Text(
                    localizedText(
                        "确定删除选中的 ${state.selectedIds.size} 个任务吗？此操作不可撤销。",
                        "Delete ${state.selectedIds.size} selected tasks? This cannot be undone."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBatchDeleteConfirm = false
                    viewModel.batchDeleteSelected()
                }) { Text(localizedText("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    if (state.showLogSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLog,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp)
            ) {
                Text(localizedText("任务日志", "Task log"), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SelectionContainer {
                    Text(
                        state.logContent?.let { localizedMessage(it, englishUi) }
                            ?: localizedText("加载中...", "Loading…"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    if (state.showEditDialog) {
        TaskEditDialog(
            task = state.editingTask,
            onDismiss = viewModel::dismissEditDialog,
            onSubmit = viewModel::submitEdit,
            onOpenScript = { path ->
                viewModel.dismissEditDialog()
                onOpenScript(path)
            }
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
                    onDelete = { showBatchDeleteConfirm = true }
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
                    onManageLabels = {
                        showMenu = false
                        showLabelManager = true
                        viewModel.loadLabelSummaries()
                    },
                    onExport = {
                        showMenu = false
                        exportBackupLauncher.launch("tasks_backup.json")
                    },
                    onImport = {
                        showMenu = false
                        importBackupLauncher.launch(
                            arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
                        )
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.availableLabels.isNotEmpty() || state.selectedLabels.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedLabels.isEmpty(),
                            onClick = viewModel::clearLabelFilters,
                            label = { Text(localizedText("全部", "All")) }
                        )
                    }
                    items(state.availableLabels, key = { "label:$it" }) { label ->
                        FilterChip(
                            selected = label in state.selectedLabels,
                            onClick = { viewModel.toggleLabelFilter(label) },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f)
            ) {
                if (state.tasks.isEmpty() && !state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.selectedLabels.isEmpty()) {
                                localizedText("暂无任务", "No tasks")
                            } else {
                                localizedText("没有符合标签筛选的任务", "No tasks match the selected labels")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            onTogglePin = { viewModel.togglePin(task) },
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
                            ) { Text(localizedText("加载更多", "Load more")) }
                        }
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
    onManageLabels: () -> Unit,
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
                    placeholder = { Text(localizedText("搜索任务...", "Search tasks…")) },
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
                    isSearching = false
                    onSearch(query)
                }) { Icon(Icons.Default.Search, localizedText("搜索", "Search")) }
            }
        )
    } else {
        TopAppBar(
            title = { Text(localizedText("任务管理", "Tasks")) },
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
                            text = { Text(localizedText("新建任务", "New task")) },
                            onClick = onNewTask,
                            leadingIcon = { Icon(Icons.Default.Add, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("批量操作", "Batch actions")) },
                            onClick = onBatchMode,
                            leadingIcon = { Icon(Icons.Default.SelectAll, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("标签管理", "Label management")) },
                            onClick = onManageLabels,
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("导出备份", "Export backup")) },
                            onClick = onExport,
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("导入备份", "Import backup")) },
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
        title = {
            Text(
                localizedText("已选 $selectedCount / $totalCount", "$selectedCount / $totalCount selected"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, localizedText("退出批量", "Exit batch mode")) }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, localizedText("全选", "Select all")) }
            IconButton(onClick = onRun, enabled = selectedCount > 0) {
                Icon(Icons.Default.PlayArrow, localizedText("执行", "Run"))
            }
            IconButton(onClick = onStop, enabled = selectedCount > 0) {
                Icon(Icons.Default.Stop, localizedText("停止", "Stop"))
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
                        text = { Text(localizedText("启用", "Enable")) },
                        onClick = { showMore = false; onEnable() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(localizedText("禁用", "Disable")) },
                        onClick = { showMore = false; onDisable() },
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.Block, null) }
                    )
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
