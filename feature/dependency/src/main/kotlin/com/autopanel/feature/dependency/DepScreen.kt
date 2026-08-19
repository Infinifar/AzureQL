package com.autopanel.feature.dependency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.material3.MenuAnchorType
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
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus
import com.autopanel.core.model.DependencyType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepScreen(viewModel: DepViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
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

    if (state.showLogSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLog,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("${state.logDepName} 安装日志", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (state.isLoadingLog) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        state.logContent ?: "",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                    Text("暂无依赖", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onClickTitle = { viewModel.showLog(dep) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DepItem(
    dep: DependencyInfo,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClickTitle: () -> Unit
) {
    val statusColor = when (dep.status) {
        DependencyStatus.INSTALLED -> MaterialTheme.colorScheme.primary
        DependencyStatus.INSTALLING, DependencyStatus.UNINSTALLING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clickable { if (!isBatchMode) onClickTitle() }
                )
                Row {
                    Text(
                        dep.typeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dep.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepDefaultTopBar(
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
                    placeholder = { Text("搜索依赖...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(onClick = { isSearching = false; query = ""; onSearch("") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
            actions = {
                IconButton(onClick = { isSearching = false; onSearch(query) }) {
                    Icon(Icons.Default.Search, "搜索")
                }
            }
        )
    } else {
        Column {
            TopAppBar(
                title = { Text("依赖管理") },
                actions = {
                    IconButton(onClick = { isSearching = true }) { Icon(Icons.Default.Search, "搜索") }
                    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "新建") }
                    IconButton(onClick = onBatchMode) { Icon(Icons.Default.SelectAll, "批量") }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DepTypeChip("全部", typeFilter == "", onClick = { onTypeFilter("") })
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
        title = { Text("已选 $selectedCount") },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, "退出") }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "全选") }
            TextButton(onClick = onReinstall, enabled = selectedCount > 0) { Text("重装") }
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
        title = { Text("新建依赖") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = types.firstOrNull { it.first == type }?.second ?: type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
