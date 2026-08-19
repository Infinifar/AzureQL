package com.autopanel.app.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
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
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardSystem
import com.autopanel.core.model.DashboardTrendItem

private val SuccessColor = Color(0xFF2E7D32)
private val ErrorColor = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.restartMessage) {
        state.restartMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRestartMessage()
        }
    }

    if (state.showRestartConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRestartConfirm,
            title = { Text("重启青龙") },
            text = { Text("确定要重启青龙服务吗？重启期间面板将短暂不可用。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestart) {
                    Text("重启", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestartConfirm) { Text("取消") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                item { SystemCard(state.system, onLongPress = viewModel::requestRestart) }
                item { TrendCard(state.trend) }
            }
        }
    }
}

@Composable
private fun TrendCard(
    trend: List<DashboardTrendItem>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader("近 7 日任务完成趋势", Icons.AutoMirrored.Filled.TrendingUp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrendLegend("成功", SuccessColor)
                TrendLegend("失败", ErrorColor)
            }
            if (trend.isEmpty()) {
                Text(
                    "暂无趋势数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                val maxTotal = trend.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
                Row(
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    trend.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                day.total.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f).width(20.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                val fraction = day.total.toFloat() / maxTotal.toFloat()
                                val barHeight = (maxHeight * fraction).coerceAtLeast(2.dp)
                                Column(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                ) {
                                    if (day.fail > 0) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .weight(day.fail.toFloat())
                                                .background(ErrorColor)
                                        )
                                    }
                                    if (day.success > 0) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .weight(day.success.toFloat())
                                                .background(SuccessColor)
                                        )
                                    }
                                    val unclassified = (day.total - day.success - day.fail)
                                        .coerceAtLeast(0)
                                    if (unclassified > 0) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .weight(unclassified.toFloat())
                                                .background(MaterialTheme.colorScheme.tertiary)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(day.date, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendLegend(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
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
            CardHeader("任务总览", Icons.AutoMirrored.Filled.Assignment)
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
            InlineStat(Icons.AutoMirrored.Filled.TrendingUp, "成功率", overview.successRate?.let { "$it%" } ?: "--", SuccessColor, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemCard(system: DashboardSystem?, onLongPress: () -> Unit) {
    if (system == null) return
    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
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
            InlineStat(
                icon = Icons.Default.Schedule,
                label = "运行时长",
                value = formatUptime(system.uptime),
                modifier = Modifier.fillMaxWidth()
            )
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
