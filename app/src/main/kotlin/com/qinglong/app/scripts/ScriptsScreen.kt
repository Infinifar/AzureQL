package com.qinglong.app.scripts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qinglong.core.model.ScriptFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(viewModel: ScriptsViewModel = hiltViewModel()) {
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // content 变化后、ModalBottomSheet 已组合时再展开，避免 show() 时序问题
    LaunchedEffect(content) {
        if (content != null) sheetState.show()
    }

    if (content != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::clearContent,
            sheetState = sheetState
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("脚本内容", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    content ?: "",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("脚本管理") },
                actions = {
                    IconButton(onClick = viewModel::loadScripts) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = viewModel::loadScripts,
            modifier = Modifier.padding(padding)
        ) {
            if (scripts.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无脚本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(scripts, key = { it.key ?: it.title ?: it.hashCode().toString() }) { file ->
                    ScriptTreeItem(
                        file = file,
                        depth = 0,
                        onClick = { f ->
                            if (!f.isDirectory) {
                                viewModel.loadContent(f)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptTreeItem(
    file: ScriptFile,
    depth: Int,
    onClick: (ScriptFile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isDir = file.isDirectory
    val indent = (depth * 20).dp

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isDir) expanded = !expanded
                    else onClick(file)
                }
                .padding(start = indent, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isDir) (if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder)
                else Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isDir) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                file.title ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isDir) {
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (file.size != null) {
                Text(
                    formatSize(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        ScriptTreeItem(child, depth + 1, onClick)
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null) return ""
    return when {
        bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }
}
