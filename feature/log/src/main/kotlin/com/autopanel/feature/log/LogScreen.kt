package com.autopanel.feature.log

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.ui.components.WindowedLogViewer
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import com.autopanel.core.ui.i18n.localizedText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemLogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SystemLogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val english by rememberUpdatedState(isEnglishUi())
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SystemLogEvent.Message -> snackbar.showSnackbar(localizedMessage(event.text, english))
            }
        }
    }
    SystemLogContent(
        state = state,
        onBack = onBack,
        onDayClick = viewModel::showDay,
        onRefresh = viewModel::refresh,
        onDismissLog = viewModel::dismissLog,
        snackbarHostState = snackbar,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SystemLogContent(
    state: SystemLogUiState,
    onBack: () -> Unit,
    onDayClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismissLog: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(localizedText("系统日志", "System logs")) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, localizedText("刷新", "Refresh"))
                        }
                    }
                )
            }
        ) { padding ->
            if (state.isInitializing) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            localizedText("按服务端日期查看最近 7 天日志", "View the latest 7 days by server date"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.timezone?.let {
                            Text(
                                localizedText("服务端时区：$it", "Server timezone: $it"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    items(state.days, key = { it }) { day ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onDayClick(day) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(
                                        dayLabel(
                                            day = day,
                                            isToday = state.days.firstOrNull() == day,
                                            isYesterday = state.days.getOrNull(1) == day
                                        ),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(day, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
        SystemLogOverlay(state, onRefresh, onDismissLog, Modifier.zIndex(1f))
    }
}

@Composable
private fun SystemLogOverlay(
    state: SystemLogUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = state.showLogSheet, onBack = onDismiss)
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDistance = with(density) { 96.dp.toPx() }
    val dismissVelocity = with(density) { 800.dp.toPx() }
    val dragDescription = localizedText("下拉关闭系统日志", "Swipe down to close system log")
    val dragState = rememberDraggableState { delta ->
        scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
    }
    LaunchedEffect(state.showLogSheet) {
        if (state.showLogSheet) dragOffset.snapTo(0f)
    }
    AnimatedVisibility(
        visible = state.showLogSheet,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)).clickable(onClick = onDismiss))
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(.9f)
                    .graphicsLayer { translationY = dragOffset.value },
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
                                    if (dragOffset.value >= dismissDistance || velocity >= dismissVelocity) {
                                        onDismiss()
                                    } else {
                                        scope.launch { dragOffset.animateTo(0f, spring()) }
                                    }
                                }
                            )
                            .semantics {
                                contentDescription = dragDescription
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(32.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
                        )
                    }
                    Text(state.selectedDay.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            state.isLoadingContent -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            state.contentError != null -> Text(
                                localizedText("加载失败：${state.contentError}", "Load failed: ${state.contentError}"),
                                color = MaterialTheme.colorScheme.error
                            )
                            state.content.isNullOrEmpty() -> Text(
                                localizedText("当天暂无系统日志", "No system logs for this day"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> Column {
                                if (state.truncated) {
                                    Text(
                                        localizedText(
                                            "日志超过 1 MiB，仅显示服务端返回的最新内容（总计 ${formatBytes(state.totalBytes)}）",
                                            "Log exceeds 1 MiB; showing the latest server response (${formatBytes(state.totalBytes)} total)"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                WindowedLogViewer(content = state.content, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(day: String, isToday: Boolean, isYesterday: Boolean): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    return when {
        isToday -> localizedText("今天", "Today")
        isYesterday -> localizedText("昨天", "Yesterday")
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MiB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}
