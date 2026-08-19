package com.qinglong.app.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qinglong.core.model.DashboardOverview
import com.qinglong.core.model.DashboardSystem

private val SuccessColor = Color(0xFF2E7D32)
private val ErrorColor = Color(0xFFC62828)

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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardHeader("任务总览", Icons.Default.Assignment)
            Row(Modifier.fillMaxWidth()) {
                StatTile(Icons.Default.Apps, overview.total.fmt(), "任务总数", Modifier.weight(1f))
                StatTile(Icons.Default.CheckCircle, overview.enabled.fmt(), "已启用", Modifier.weight(1f), SuccessColor)
                StatTile(Icons.Default.Block, overview.disabled.fmt(), "已禁用", Modifier.weight(1f), ErrorColor)
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile(Icons.Default.PlayArrow, overview.todayRuns.fmt(), "今日执行", Modifier.weight(1f))
                StatTile(Icons.Default.Done, overview.todaySuccess.fmt(), "今日成功", Modifier.weight(1f), SuccessColor)
                StatTile(Icons.Default.Close, overview.todayFail.fmt(), "今日失败", Modifier.weight(1f), ErrorColor)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            InlineStat(Icons.Default.TrendingUp, "成功率", overview.successRate?.let { "$it%" } ?: "--", SuccessColor, Modifier.fillMaxWidth())
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardHeader("系统状态", Icons.Default.MonitorHeart)
            Row(Modifier.fillMaxWidth()) {
                StatTile(Icons.Default.Computer, system.platform ?: "--", "平台", Modifier.weight(1f))
                StatTile(Icons.Default.Speed, system.cpus.fmt(), "CPU 核数", Modifier.weight(1f))
                StatTile(Icons.Default.Memory, system.memUsagePercent?.let { "$it%" } ?: "--", "内存", Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            InlineStat(Icons.Default.Schedule, "运行时长", formatUptime(system.uptime), Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CardHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InlineStat(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun Int?.fmt(): String = this?.toString() ?: "--"

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
