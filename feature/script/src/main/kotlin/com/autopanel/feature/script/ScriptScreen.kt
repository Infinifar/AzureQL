package com.autopanel.feature.script

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.ScriptFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptScreen(
    viewModel: ScriptViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
    }

    if (state.showNewFileDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNewFileDialog,
            title = { Text("新建脚本") },
            text = {
                OutlinedTextField(
                    value = state.newFileName, onValueChange = viewModel::onNewFileNameChanged,
                    label = { Text("文件名") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::createNewFile, enabled = state.newFileName.isNotBlank()) {
                    Text("创建")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissNewFileDialog) { Text("取消") } }
        )
    }

    val selected = state.selectedScript
    if (state.showActionMenu) {
        DropdownMenu(expanded = true, onDismissRequest = viewModel::dismissActionMenu) {
            if (selected != null && !selected.isDirectory) {
                DropdownMenuItem(
                    text = { Text("下载") },
                    leadingIcon = { Icon(Icons.Default.Download, null) },
                    onClick = viewModel::downloadScript
                )
            }
            DropdownMenuItem(
                text = { Text("新建文件") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = {
                    viewModel.dismissActionMenu()
                    // 目录用其完整路径作为新建位置，文件用其父目录
                    val dir = selected?.let {
                        if (it.isDirectory) (it.key ?: "") else (it.parent ?: "")
                    } ?: ""
                    viewModel.showNewFileDialog(dir)
                }
            )
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = viewModel::showDeleteConfirm
            )
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text("确认删除") },
            text = { Text("确定要删除「${state.selectedScript?.title}」吗？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteConfirm) { Text("取消") } }
        )
    }

    if (state.showContent) {
        ScriptContentDialog(state, viewModel)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("脚本管理") },
                actions = {
                    IconButton(onClick = { viewModel.showNewFileDialog() }) {
                        Icon(Icons.Default.Add, "新建脚本")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
            if (state.scripts.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无脚本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.scripts, key = { it.key ?: it.hashCode().toString() }) { file ->
                    ScriptTreeItem(
                        file = file,
                        depth = 0,
                        onClick = { f ->
                            if (!f.isDirectory) {
                                viewModel.loadContent(f.title ?: "", f.parent ?: "")
                            }
                        },
                        onLongClick = { viewModel.showActionMenu(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptContentDialog(
    state: ScriptUiState,
    viewModel: ScriptViewModel
) {
    Dialog(
        onDismissRequest = viewModel::closeContent,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::closeContent) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                    Text(
                        state.editingFilename,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!state.isLoadingContent) {
                        if (!state.isEditing) {
                            TextButton(onClick = viewModel::enterEditMode) { Text("编辑") }
                        } else {
                            TextButton(onClick = viewModel::cancelEdit) { Text("取消") }
                            TextButton(onClick = viewModel::saveContent) {
                                Text("保存", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                HorizontalDivider()
                when {
                    state.isLoadingContent -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.isEditing -> OutlinedTextField(
                        value = state.editContent,
                        onValueChange = viewModel::onContentChanged,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    else -> SelectionContainer {
                        Text(
                            text = state.editContent,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptTreeItem(
    file: ScriptFile,
    depth: Int,
    onClick: (ScriptFile) -> Unit,
    onLongClick: (ScriptFile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isDir = file.isDirectory
    val indent = (depth * 24).dp

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isDir) expanded = !expanded
                    else onClick(file)
                }
                .padding(start = 16.dp + indent, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDir) {
                Icon(
                    if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(file.title ?: "--", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    file.title ?: "--", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        val children = file.children
        if (isDir && !children.isNullOrEmpty()) {
            AnimatedVisibility(expanded) {
                Column {
                    val sorted = children.sortedWith(
                        compareByDescending<ScriptFile> { it.isDirectory }.thenBy { it.title }
                    )
                    sorted.forEach { child ->
                        ScriptTreeItem(child, depth + 1, onClick, onLongClick)
                    }
                }
            }
        }
    }
}
