package com.qinglong.app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qinglong.core.model.DashboardOverview
import com.qinglong.core.model.DashboardSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showLogSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissLog,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(state.logFileName, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (state.isLoadingContent) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
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
        topBar = { TopAppBar(title = { Text("青龙面板") }) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { OverviewCard(state.overview) }
                item { SystemCard(state.system) }
                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                item { Text("系统日志", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp)) }

                if (state.logs.isEmpty() && !state.isLoading) {
                    item { Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                }

                items(state.logs) { log ->
                    Card(
                        Modifier.fillMaxWidth().clickable { viewModel.showLog(log) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    log.title ?: "--",
                                    style = MaterialTheme.typography.bodyMedium,
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(overview: DashboardOverview?) {
    if (overview == null) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("任务总览", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                StatLabel("任务总数", overview.total, modifier = Modifier.weight(1f))
                StatLabel("已启用", overview.enabled, modifier = Modifier.weight(1f))
                StatLabel("已禁用", overview.disabled, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatLabel("今日执行", overview.todayRuns, modifier = Modifier.weight(1f))
                StatLabel("今日成功", overview.todaySuccess, modifier = Modifier.weight(1f))
                StatLabel("今日失败", overview.todayFail, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatLabel("成功率", null, overview.successRate?.let { "$it%" }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SystemCard(system: DashboardSystem?) {
    if (system == null) return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("系统状态", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                StatLabel("平台", null, system.platform, modifier = Modifier.weight(1f))
                StatLabel("CPU 核数", system.cpus, modifier = Modifier.weight(1f))
                StatLabel("内存", null, system.memUsagePercent?.let { "$it%" }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatLabel("运行时长", null, formatUptime(system.uptime), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatLabel(label: String, value: Int?, textValue: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            textValue ?: (value?.toString() ?: "--"),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatUptime(seconds: Long?): String {
    if (seconds == null) return "--"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return when {
        d > 0 -> "${d}天${h}时"
        h > 0 -> "${h}时${m}分"
        else -> "${m}分"
    }
}
