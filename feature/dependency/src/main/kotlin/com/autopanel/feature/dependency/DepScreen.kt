package com.autopanel.feature.dependency

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus
import com.autopanel.core.model.DependencyType
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DepViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val englishUi = isEnglishUi()
    val currentEnglishUi by rememberUpdatedState(isEnglishUi())

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is DepEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.text, currentEnglishUi)
                )
            }
        }
    }

    if (state.showAddDialog) {
        AddDepDialog(
            name = state.editName,
            type = state.editType,
            onNameChange = viewModel::onEditNameChanged,
            onTypeChange = viewModel::onEditTypeChanged,
            onConfirm = viewModel::addDependency,
            onDismiss = viewModel::dismissAddDialog
        )
    }

    state.confirmReinstall?.let { dep ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReinstall,
            title = { Text(localizedText("重新安装依赖", "Reinstall dependency")) },
            text = {
                Text(
                    localizedText(
                        "确定重新安装「${dep.name ?: "--"}」吗？任务将在服务端执行。",
                        "Reinstall “${dep.name ?: "--"}”? The task will run on the server."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReinstall) { Text(localizedText("重装", "Reinstall")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReinstall) { Text(localizedText("取消", "Cancel")) }
            }
        )
    }

    state.confirmDelete?.let { dep ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(localizedText("删除依赖", "Delete dependency")) },
            text = {
                Text(
                    localizedText(
                        "确定删除「${dep.name ?: "--"}」吗？此操作不可撤销。",
                        "Delete “${dep.name ?: "--"}”? This cannot be undone."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(localizedText("删除", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text(localizedText("取消", "Cancel")) }
            }
        )
    }

    if (state.showLogSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLog,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp)) {
                Text(
                    localizedText(
                        "${state.logDepName} 安装日志",
                        "${state.logDepName} installation log"
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (state.isLoadingLog) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    SelectionContainer {
                        Text(
                            state.logContent?.let { localizedMessage(it, englishUi) } ?: "",
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
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isBatchMode) {
                DepBatchTopBar(
                    selectedCount = state.selectedIds.size,
                    onBack = viewModel::toggleBatchMode,
                    onSelectAll = viewModel::selectAll,
                    onReinstall = viewModel::batchReinstallSelected,
                    onDelete = viewModel::batchDeleteSelected
                )
            } else {
                DepDefaultTopBar(
                    onBack = onBack,
                    onOpenSettings = onOpenSettings,
                    onSearch = viewModel::onSearch,
                    typeFilter = state.typeFilter,
                    onTypeFilter = viewModel::setTypeFilter,
                    onAdd = viewModel::showAddDialog,
                    onBatchMode = viewModel::toggleBatchMode
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
            if (state.deps.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(localizedText("暂无依赖", "No dependencies"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.deps, key = { it.id ?: it.hashCode().toString() }) { dep ->
                    DepItem(
                        dep = dep,
                        isBatchMode = state.isBatchMode,
                        isSelected = dep.id?.let { state.selectedIds.contains(it) } ?: false,
                        onToggleSelection = { dep.id?.let { viewModel.toggleSelection(it) } },
                        onReinstall = { viewModel.requestReinstall(dep) },
                        onDelete = { viewModel.requestDelete(dep) },
                        onShowLog = { viewModel.showLog(dep) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DepItem(
    dep: DependencyInfo,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onReinstall: () -> Unit,
    onDelete: () -> Unit,
    onShowLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (dep.status) {
        DependencyStatus.INSTALLED -> MaterialTheme.colorScheme.primary
        DependencyStatus.INSTALLING,
        DependencyStatus.UNINSTALLING,
        DependencyStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
        DependencyStatus.INSTALL_FAILED,
        DependencyStatus.UNINSTALL_FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isBatchMode) onToggleSelection() else onReinstall() },
                onLongClick = { if (!isBatchMode) onDelete() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBatchMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dep.name ?: "--",
                    style = MaterialTheme.typography.bodyLarge
                )
                Row {
                    Text(
                        dep.localizedTypeText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dep.localizedStatusText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            if (!isBatchMode) {
                IconButton(onClick = onShowLog) {
                    Icon(Icons.Default.Description, contentDescription = localizedText("查看安装日志", "View installation log"))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepDefaultTopBar(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (String) -> Unit,
    typeFilter: String,
    onTypeFilter: (String) -> Unit,
    onAdd: () -> Unit,
    onBatchMode: () -> Unit
) {
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    if (isSearching) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(localizedText("搜索依赖...", "Search dependencies…")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(onClick = { isSearching = false; query = ""; onSearch("") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                }
            },
            actions = {
                IconButton(onClick = { isSearching = false; onSearch(query) }) {
                    Icon(Icons.Default.Search, localizedText("搜索", "Search"))
                }
            }
        )
    } else {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = localizedText("依赖管理", "Dependencies"),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, localizedText("依赖设置", "Dependency settings"))
                    }
                    IconButton(onClick = { isSearching = true }) { Icon(Icons.Default.Search, localizedText("搜索", "Search")) }
                    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, localizedText("新建", "New")) }
                    IconButton(onClick = onBatchMode) { Icon(Icons.Default.SelectAll, localizedText("批量", "Batch")) }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DepTypeChip(localizedText("全部", "All"), typeFilter == "", onClick = { onTypeFilter("") })
                DepTypeChip("Node.js", typeFilter == DependencyType.NODEJS, onClick = { onTypeFilter(DependencyType.NODEJS) })
                DepTypeChip("Python", typeFilter == DependencyType.PYTHON, onClick = { onTypeFilter(DependencyType.PYTHON) })
                DepTypeChip("Linux", typeFilter == DependencyType.LINUX, onClick = { onTypeFilter(DependencyType.LINUX) })
            }
        }
    }
}

@Composable
private fun DepTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = if (selected) ButtonDefaults.buttonColors()
        else ButtonDefaults.outlinedButtonColors()
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepBatchTopBar(
    selectedCount: Int,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onReinstall: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text(localizedText("已选 $selectedCount", "$selectedCount selected")) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, localizedText("退出", "Exit")) }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, localizedText("全选", "Select all")) }
            TextButton(onClick = onReinstall, enabled = selectedCount > 0) { Text(localizedText("重装", "Reinstall")) }
            TextButton(onClick = onDelete, enabled = selectedCount > 0) { Text("🗑") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDepDialog(
    name: String,
    type: String,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val types = listOf(
        DependencyType.NODEJS to "Node.js",
        DependencyType.PYTHON to "Python",
        DependencyType.LINUX to "Linux"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("新建依赖", "New dependency")) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    label = { Text(localizedText("名称", "Name")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = types.firstOrNull { it.first == type }?.second ?: type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(localizedText("类型", "Type")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        types.forEach { (t, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onTypeChange(t); expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) { Text(localizedText("确定", "Confirm")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("取消", "Cancel")) }
        }
    )
}

@Composable
private fun DependencyInfo.localizedTypeText(): String = when (DependencyType.fromCode(type ?: -1)) {
    DependencyType.NODEJS -> "Node.js"
    DependencyType.PYTHON -> "Python"
    DependencyType.LINUX -> "Linux"
    else -> localizedText("未知", "Unknown")
}

@Composable
private fun DependencyInfo.localizedStatusText(): String = when (status) {
    DependencyStatus.INSTALLING -> localizedText("安装中", "Installing")
    DependencyStatus.INSTALLED -> localizedText("已安装", "Installed")
    DependencyStatus.INSTALL_FAILED -> localizedText("安装失败", "Installation failed")
    DependencyStatus.UNINSTALLING -> localizedText("卸载中", "Uninstalling")
    DependencyStatus.UNINSTALLED -> localizedText("已卸载", "Uninstalled")
    DependencyStatus.UNINSTALL_FAILED -> localizedText("卸载失败", "Uninstallation failed")
    DependencyStatus.QUEUED -> localizedText("队列中", "Queued")
    DependencyStatus.CANCELLED -> localizedText("已取消", "Cancelled")
    else -> localizedText("未知", "Unknown")
}
