package com.autopanel.feature.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.LogFile
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import androidx.compose.runtime.rememberUpdatedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
    viewModel: LogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentEnglishUi by rememberUpdatedState(isEnglishUi())

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is LogEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.text, currentEnglishUi)
                )
            }
        }
    }

    state.confirmDelete?.let { log ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(localizedText("删除日志", "Delete log")) },
            text = {
                Text(
                    localizedText(
                        "确定删除 ${log.title.orEmpty()}？删除后无法恢复。",
                        "Delete ${log.title.orEmpty()}? This cannot be undone."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(localizedText("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                }
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
                Text(state.logFileName, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (state.isLoadingContent) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    SelectionContainer {
                        Text(
                            state.logContent?.let { localizedMessage(it, currentEnglishUi) } ?: "",
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
            TopAppBar(
                title = { Text(localizedText("任务日志", "Task logs")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = localizedText("返回", "Back")
                        )
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
            if (state.logs.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(localizedText("暂无日志文件", "No log files"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.logs) { log ->
                    LogItem(
                        log = log,
                        onClick = { viewModel.showLog(log) },
                        onDelete = { viewModel.requestDelete(log) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: LogFile, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    log.title ?: "--",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                log.parent?.takeIf { it.isNotBlank() }?.let { dir ->
                    Text(
                        dir,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = localizedText(
                        "删除 ${log.title.orEmpty()}",
                        "Delete ${log.title.orEmpty()}"
                    ),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
