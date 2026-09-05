package com.autopanel.feature.log

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.LogFile
import com.autopanel.core.ui.components.WindowedLogViewer
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch

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

    Box(Modifier.fillMaxSize()) {
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
                        Text(
                            localizedText("暂无日志文件", "No log files"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

        LogFileOverlay(
            visible = state.showLogSheet,
            filename = state.logFileName,
            content = state.logContent,
            truncated = state.logTruncated,
            isLoading = state.isLoadingContent,
            error = state.logError,
            onDismiss = viewModel::dismissLog
        )
    }
}

@Composable
private fun LogFileOverlay(
    visible: Boolean,
    filename: String,
    content: String?,
    truncated: Boolean,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val noRippleInteraction = remember { MutableInteractionSource() }
    val dragOffsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDistancePx = with(density) { LOG_FILE_DISMISS_DISTANCE.toPx() }
    val dismissVelocityPx = with(density) { LOG_FILE_DISMISS_VELOCITY.toPx() }
    val dragHandleDescription = localizedText(
        "下拉关闭任务日志",
        "Swipe down to close task log"
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
        modifier = Modifier.fillMaxSize().zIndex(1f),
        enter = fadeIn(tween(LOG_FILE_OVERLAY_FADE_MILLIS)),
        exit = fadeOut(tween(LOG_FILE_OVERLAY_FADE_MILLIS))
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
                                            dragOffsetY.animateTo(0f, spring())
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
                    Text(filename, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    when {
                        isLoading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        error != null -> Text(
                            text = "${localizedText("加载失败", "Load failed")}: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                        content.isNullOrEmpty() -> Text(
                            localizedText("暂无内容", "No content"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            if (truncated) {
                                Text(
                                    localizedText(
                                        "仅显示最新 256 KiB；服务端原始内容未被改写",
                                        "Showing the latest 256 KiB; server content is unchanged"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            WindowedLogViewer(content = content, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

private const val LOG_FILE_OVERLAY_FADE_MILLIS = 120
private val LOG_FILE_DISMISS_DISTANCE = 96.dp
private val LOG_FILE_DISMISS_VELOCITY = 800.dp

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
