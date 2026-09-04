package com.autopanel.app.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardRuntime
import com.autopanel.core.model.DashboardSystem
import com.autopanel.core.model.DashboardTopCountItem
import com.autopanel.core.model.DashboardTopTimeItem
import com.autopanel.core.model.DashboardTrendItem
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import kotlinx.coroutines.launch
import java.util.Locale

private val SuccessColor = Color(0xFF2E7D32)
private val ErrorColor = Color(0xFFC62828)
private val EmptyOverview = DashboardOverview(
    total = 0,
    enabled = 0,
    disabled = 0,
    todayRuns = 0,
    todaySuccess = 0,
    todayFail = 0,
    successRate = "0",
    avgTime = 0
)
private val EmptySystem = DashboardSystem(
    platform = null,
    uptime = 0,
    memUsagePercent = "0",
    cpus = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val englishUi = isEnglishUi()

    LaunchedEffect(state.restartMessage, englishUi) {
        state.restartMessage?.let {
            snackbarHostState.showSnackbar(localizedMessage(it, englishUi))
            viewModel.clearRestartMessage()
        }
    }

    if (state.showRestartConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRestartConfirm,
            title = { Text(localizedText("重启青龙", "Restart QingLong")) },
            text = {
                Text(
                    localizedText(
                        "确定要重启青龙服务吗？重启期间面板将短暂不可用。",
                        "Restart the QingLong service? The panel will be briefly unavailable."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestart) {
                    Text(localizedText("重启", "Restart"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestartConfirm) { Text(localizedText("取消", "Cancel")) }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = formatHomeTitle(state.serverAlias),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
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
                    item { OverviewCard(state.overview ?: EmptyOverview, onLongPress = viewModel::showTaskDetails) }
                    item { SystemCard(state.system ?: EmptySystem, onLongPress = viewModel::requestRestart) }
                    item { TrendCard(state.trend) }
                }
            }
        }

        TaskDetailsOverlay(
            visible = state.showTaskDetails,
            overview = state.overview,
            runtime = state.runtime,
            topCount = state.topCount,
            topTime = state.topTime,
            isLoading = state.isTaskDetailsLoading,
            error = state.taskDetailsError?.let { localizedMessage(it, englishUi) },
            onRefresh = viewModel::refreshTaskDetails,
            onDismiss = viewModel::dismissTaskDetails
        )
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
            CardHeader(localizedText("近 7 日任务完成趋势", "7-day task trend"), Icons.AutoMirrored.Filled.TrendingUp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrendLegend(localizedText("成功", "Success"), SuccessColor)
                TrendLegend(localizedText("失败", "Failed"), ErrorColor)
            }
            if (trend.isEmpty()) {
                Text(
                    localizedText("暂无趋势数据", "No trend data"),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverviewCard(overview: DashboardOverview?, onLongPress: () -> Unit) {
    if (overview == null) return
    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClickLabel = localizedText("查看任务详情", "View task details"),
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CardHeader(localizedText("任务总览", "Task overview"), Icons.AutoMirrored.Filled.Assignment)
            Row(Modifier.fillMaxWidth()) {
                StatTile(Icons.Default.Apps, overview.total.fmt(), localizedText("任务总数", "Total"), Modifier.weight(1f))
                StatTile(Icons.Default.CheckCircle, overview.enabled.fmt(), localizedText("已启用", "Enabled"), Modifier.weight(1f), SuccessColor)
                StatTile(Icons.Default.Block, overview.disabled.fmt(), localizedText("已禁用", "Disabled"), Modifier.weight(1f), ErrorColor)
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile(Icons.Default.PlayArrow, overview.todayRuns.fmt(), localizedText("今日执行", "Runs today"), Modifier.weight(1f))
                StatTile(Icons.Default.Done, overview.todaySuccess.fmt(), localizedText("今日成功", "Succeeded"), Modifier.weight(1f), SuccessColor)
                StatTile(Icons.Default.Close, overview.todayFail.fmt(), localizedText("今日失败", "Failed"), Modifier.weight(1f), ErrorColor)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            OverviewSuccessRate(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                value = overview.successRate?.let { "$it%" } ?: "--",
                tint = SuccessColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TaskDetailsOverlay(
    visible: Boolean,
    overview: DashboardOverview?,
    runtime: DashboardRuntime?,
    topCount: List<DashboardTopCountItem>,
    topTime: List<DashboardTopTimeItem>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val noRippleInteraction = remember { MutableInteractionSource() }
    val dragOffsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDistancePx = with(density) { DETAILS_OVERLAY_DISMISS_DISTANCE.toPx() }
    val dismissVelocityPx = with(density) { DETAILS_OVERLAY_DISMISS_VELOCITY.toPx() }
    val dragHandleDescription = localizedText(
        "下拉关闭任务详情",
        "Swipe down to close task details"
    )
    val dragState = rememberDraggableState { delta ->
        coroutineScope.launch {
            dragOffsetY.snapTo((dragOffsetY.value + delta).coerceAtLeast(0f))
        }
    }

    LaunchedEffect(visible) {
        if (visible) dragOffsetY.snapTo(0f)
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
        enter = fadeIn(tween(DETAILS_OVERLAY_FADE_MILLIS)),
        exit = fadeOut(tween(DETAILS_OVERLAY_FADE_MILLIS))
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = noRippleInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
                    .clearAndSetSemantics { }
            )
            Surface(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .graphicsLayer { translationY = dragOffsetY.value }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .draggable(
                                state = dragState,
                                orientation = Orientation.Vertical,
                                onDragStopped = { velocity ->
                                    if (
                                        dragOffsetY.value >= dismissDistancePx ||
                                        velocity >= dismissVelocityPx
                                    ) {
                                        onDismiss()
                                    } else {
                                        coroutineScope.launch {
                                            dragOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring()
                                            )
                                        }
                                    }
                                }
                            )
                            .semantics {
                                contentDescription = dragHandleDescription
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                    }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        localizedText("任务运行详情", "Task runtime details"),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, localizedText("刷新任务详情", "Refresh task details"))
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth()) {
                    StatTile(
                        Icons.Default.PlayArrow,
                        runtime?.runningCount.fmt(),
                        localizedText("运行中", "Running"),
                        Modifier.weight(1f),
                        SuccessColor
                    )
                    StatTile(
                        Icons.Default.Schedule,
                        runtime?.queuedCount.fmt(),
                        localizedText("排队中", "Queued"),
                        Modifier.weight(1f)
                    )
                    StatTile(
                        Icons.Default.Speed,
                        formatDurationMillis(overview?.avgTime?.toLong()),
                        localizedText("今日平均", "Today's average"),
                        Modifier.weight(1f)
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onRefresh, enabled = !isLoading) { Text(localizedText("重试", "Retry")) }
                        }
                    }
                }
            }

            item { DetailSectionTitle(localizedText("运行中任务", "Running tasks")) }
            val runningTasks = runtime?.running.orEmpty()
            if (runningTasks.isEmpty() && !isLoading) {
                item { EmptyDetailText(localizedText("当前没有正在运行的任务", "No tasks are currently running")) }
            } else {
                items(
                    runningTasks,
                    key = { it.instanceId ?: it.id ?: it.name.orEmpty() }
                ) { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.name ?: localizedText("任务 #${task.id ?: "--"}", "Task #${task.id ?: "--"}"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            task.pid?.let {
                                Text(
                                    "PID $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            formatDurationSeconds(task.elapsed),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Text(
                    if ((runtime?.queuedCount ?: 0) > 0) {
                        localizedText(
                            "当前有 ${runtime?.queuedCount} 个任务排队；此版本 API 未提供排队任务名称。",
                            "${runtime?.queuedCount} tasks are queued; this API version does not provide their names."
                        )
                    } else {
                        localizedText("当前没有排队任务", "No tasks are queued")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { HorizontalDivider() }
            item { DetailSectionTitle(localizedText("今日执行次数 Top 5", "Top 5 runs today")) }
            item { TopCountHeader() }
            if (topCount.isEmpty() && !isLoading) {
                item { EmptyDetailText(localizedText("今日暂无执行次数数据", "No run-count data today")) }
            } else {
                items(topCount, key = { "count-${it.rank}-${it.name}" }) { entry ->
                    TopCountRow(entry)
                }
            }

            item { HorizontalDivider() }
            item { DetailSectionTitle(localizedText("今日耗时 Top 5", "Top 5 duration today")) }
            item { TopTimeHeader() }
            if (topTime.isEmpty() && !isLoading) {
                item { EmptyDetailText(localizedText("今日暂无耗时数据", "No duration data today")) }
            } else {
                items(topTime, key = { "time-${it.rank}-${it.name}" }) { entry ->
                    TopTimeRow(entry)
                }
            }
        }
                }
            }
        }
    }
}

private const val DETAILS_OVERLAY_FADE_MILLIS = 120
private val DETAILS_OVERLAY_DISMISS_DISTANCE = 96.dp
private val DETAILS_OVERLAY_DISMISS_VELOCITY = 800.dp

@Composable
private fun DetailSectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun EmptyDetailText(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

@Composable
private fun TopCountHeader() {
    RankingRow(
        rank = "#",
        name = localizedText("定时任务", "Task"),
        first = localizedText("次数", "Runs"),
        second = localizedText("平均", "Average"),
        third = localizedText("成功率", "Success")
    )
}

@Composable
private fun TopCountRow(item: DashboardTopCountItem) {
    RankingRow(
        rank = item.rank.toString(),
        name = item.name ?: "--",
        first = item.runCount?.toString() ?: "--",
        second = formatDurationMillis(item.avgTime),
        third = item.successRate?.let { "$it%" } ?: "--"
    )
}

@Composable
private fun TopTimeHeader() {
    RankingRow(
        rank = "#",
        name = localizedText("定时任务", "Task"),
        first = localizedText("最长", "Longest"),
        second = localizedText("平均", "Average"),
        third = null
    )
}

@Composable
private fun TopTimeRow(item: DashboardTopTimeItem) {
    RankingRow(
        rank = item.rank.toString(),
        name = item.name ?: "--",
        first = formatDurationMillis(item.maxTime),
        second = formatDurationMillis(item.avgTime),
        third = null
    )
}

@Composable
private fun RankingRow(
    rank: String,
    name: String,
    first: String,
    second: String,
    third: String?
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankingCell(rank, 0.45f, TextAlign.Center)
        RankingCell(name, 2.2f, TextAlign.Start)
        RankingCell(first, 0.8f, TextAlign.End)
        RankingCell(second, 0.95f, TextAlign.End)
        if (third != null) RankingCell(third, 0.95f, TextAlign.End)
    }
}

@Composable
private fun RowScope.RankingCell(value: String, weight: Float, alignment: TextAlign) {
    Text(
        value,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        textAlign = alignment,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
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
            CardHeader(localizedText("系统状态", "System status"), Icons.Default.MonitorHeart)
            Row(Modifier.fillMaxWidth()) {
                StatTile(Icons.Default.Computer, system.platform ?: "--", localizedText("平台", "Platform"), Modifier.weight(1f))
                StatTile(Icons.Default.Speed, system.cpus.fmt(), localizedText("CPU 核数", "CPU cores"), Modifier.weight(1f))
                StatTile(Icons.Default.Memory, system.memUsagePercent?.let { "$it%" } ?: "--", localizedText("内存", "Memory"), Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            InlineStat(
                icon = Icons.Default.Schedule,
                label = localizedText("运行时长", "Uptime"),
                value = localizedText(formatUptime(system.uptime), formatUptimeEnglish(system.uptime)),
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

private fun formatDurationSeconds(seconds: Int?): String =
    seconds?.let { formatDurationMillis(it.toLong() * 1_000L) } ?: "--"

private fun formatDurationMillis(milliseconds: Long?): String {
    if (milliseconds == null) return "--"
    return when {
        milliseconds < 1_000L -> "${milliseconds}ms"
        milliseconds < 60_000L -> "%.1fs".format(Locale.US, milliseconds / 1_000.0)
        else -> "%.1fmin".format(Locale.US, milliseconds / 60_000.0)
    }
}

@Composable
private fun OverviewSuccessRate(
    icon: ImageVector,
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
            text = localizedText("成功率：$value", "Success rate: $value"),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

internal fun formatHomeTitle(alias: String?): String =
    alias?.trim()?.takeIf(String::isNotEmpty)?.let { "AzureQL（$it）" } ?: "AzureQL"

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

private fun formatUptimeEnglish(seconds: Long?): String {
    if (seconds == null) return "--"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
