package com.autopanel.feature.task

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import com.autopanel.core.model.TaskInfo
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val cronParser5 = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.CRON4J))
private val cronParser6 = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING))
private val cronFormatter = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss")

fun nextExecutionTime(schedule: String?): String {
    if (schedule.isNullOrBlank()) return "--"
    return try {
        val parts = schedule.trim().split(" ")
        val parser = if (parts.size == 6) cronParser6 else if (parts.size == 5) cronParser5 else return "--"
        val exec = ExecutionTime.forCron(parser.parse(schedule))
        exec.nextExecution(ZonedDateTime.now())
            .map { it.format(cronFormatter) }
            .orElse("--")
    } catch (_: Exception) { "--" }
}

fun formatRunningTime(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return "--"
    return if (seconds >= 60) "${seconds / 60}分${seconds % 60}秒" else "${seconds}秒"
}

fun formatTimestamp(ts: Long?): String {
    if (ts == null || ts <= 0) return "--"
    return try {
        java.time.Instant.ofEpochSecond(ts).atZone(java.time.ZoneId.systemDefault())
            .format(cronFormatter)
    } catch (_: Exception) { "--" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: TaskInfo,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onClickTitle: () -> Unit,
    onLongPressTitle: () -> Unit,
    onLongPress: () -> Unit
) {
    val isRunning = task.statusCode == 0 || task.statusCode == 1
    val isDisabled = task.statusCode == 3
    val statusColor = when {
        isRunning -> MaterialTheme.colorScheme.primary
        isDisabled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isBatchMode)
                        Modifier.combinedClickable(onClick = onToggleSelection, onLongClick = {})
                    else
                        Modifier
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBatchMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                Spacer(Modifier.width(4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isBatchMode) Modifier
                        else Modifier.combinedClickable(onClick = onClickTitle, onLongClick = onLongPress)
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.pinned) {
                        Icon(
                            Icons.Default.PushPin, "已置顶",
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        task.name ?: "--",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    "命令: ${task.command ?: "--"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "定时: ${task.schedule ?: "--"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "状态: ${task.statusText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                    Text(
                        "上次运行: ${formatRunningTime(task.lastRunningTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "上次执行: ${formatTimestamp(task.lastExecutionTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "下次执行: ${nextExecutionTime(task.schedule)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 播放/暂停按钮 - 非批量模式下显示
            if (!isBatchMode) {
                IconButton(onClick = { if (isRunning) onStop() else onRun() }) {
                    Icon(
                        if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "停止" else "执行",
                        tint = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
