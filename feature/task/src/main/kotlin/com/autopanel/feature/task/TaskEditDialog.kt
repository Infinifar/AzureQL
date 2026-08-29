package com.autopanel.feature.task

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskScheduleType
import com.autopanel.core.model.toDraft
import com.autopanel.core.ui.i18n.localizedText

@Composable
fun TaskEditDialog(
    task: TaskInfo?,
    onDismiss: () -> Unit,
    onSubmit: (TaskDraft) -> Unit
) {
    var draft by remember(task) { mutableStateOf(task?.toDraft() ?: TaskDraft()) }
    var labelInput by remember(task) { mutableStateOf("") }
    val beforeHasTaskCommand = containsTaskCommand(draft.taskBefore)
    val afterHasTaskCommand = containsTaskCommand(draft.taskAfter)
    val canSubmit = draft.name.isNotBlank() &&
        draft.command.isNotBlank() &&
        (draft.scheduleType != TaskScheduleType.NORMAL || draft.schedule.isNotBlank()) &&
        draft.extraSchedules.none(String::isBlank) &&
        !beforeHasTaskCommand &&
        !afterHasTaskCommand &&
        draft.logName.length <= 100

    fun addLabel() {
        val label = labelInput.trim()
        if (label.isNotEmpty() && label !in draft.labels) {
            draft = draft.copy(labels = draft.labels + label)
        }
        labelInput = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (task != null) localizedText("编辑任务", "Edit task")
                else localizedText("新建任务", "New task")
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(localizedText("名称", "Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.command,
                    onValueChange = { draft = draft.copy(command = it) },
                    label = { Text(localizedText("命令/脚本", "Command / script")) },
                    placeholder = {
                        Text(localizedText("支持脚本路径或系统可执行命令", "Enter a script path or executable command"))
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(localizedText("定时类型", "Schedule type"), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleTypeChip(
                        selected = draft.scheduleType == TaskScheduleType.NORMAL,
                        label = localizedText("常规定时", "Regular"),
                        onClick = { draft = draft.copy(scheduleType = TaskScheduleType.NORMAL) }
                    )
                    ScheduleTypeChip(
                        selected = draft.scheduleType == TaskScheduleType.ONCE,
                        label = localizedText("手动运行", "Manual"),
                        onClick = { draft = draft.copy(scheduleType = TaskScheduleType.ONCE) }
                    )
                    ScheduleTypeChip(
                        selected = draft.scheduleType == TaskScheduleType.BOOT,
                        label = localizedText("开机运行", "At boot"),
                        onClick = { draft = draft.copy(scheduleType = TaskScheduleType.BOOT) }
                    )
                }

                if (draft.scheduleType == TaskScheduleType.NORMAL) {
                    OutlinedTextField(
                        value = draft.schedule,
                        onValueChange = { draft = draft.copy(schedule = it) },
                        label = { Text(localizedText("定时规则", "Schedule")) },
                        placeholder = { Text(localizedText("秒(可选) 分 时 天 月 周", "Optional seconds, minute, hour, day, month, weekday")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    draft.extraSchedules.forEachIndexed { index, schedule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = schedule,
                                onValueChange = { value ->
                                    draft = draft.copy(
                                        extraSchedules = draft.extraSchedules.toMutableList().also { it[index] = value }
                                    )
                                },
                                label = { Text(localizedText("附加定时规则", "Additional schedule")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    draft = draft.copy(
                                        extraSchedules = draft.extraSchedules.filterIndexed { itemIndex, _ -> itemIndex != index }
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Delete, localizedText("删除定时规则", "Remove schedule"))
                            }
                        }
                    }
                    TextButton(onClick = { draft = draft.copy(extraSchedules = draft.extraSchedules + "") }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(localizedText("新增定时规则", "Add schedule"))
                    }
                }

                Text(localizedText("标签", "Labels"), style = MaterialTheme.typography.labelMedium)
                if (draft.labels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        draft.labels.forEach { label ->
                            InputChip(
                                selected = false,
                                onClick = { draft = draft.copy(labels = draft.labels - label) },
                                label = { Text(label) },
                                trailingIcon = { Icon(Icons.Default.Close, localizedText("删除标签", "Remove label")) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(localizedText("新建标签", "New label")) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = ::addLabel, enabled = labelInput.isNotBlank()) {
                            Icon(Icons.Default.Add, localizedText("添加标签", "Add label"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(localizedText("实例模式", "Instance mode"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !draft.allowMultipleInstances,
                        onClick = { draft = draft.copy(allowMultipleInstances = false) },
                        label = { Text(localizedText("单实例", "Single instance")) }
                    )
                    FilterChip(
                        selected = draft.allowMultipleInstances,
                        onClick = { draft = draft.copy(allowMultipleInstances = true) },
                        label = { Text(localizedText("多实例", "Multiple instances")) }
                    )
                }

                OutlinedTextField(
                    value = draft.logName,
                    onValueChange = { draft = draft.copy(logName = it) },
                    label = { Text(localizedText("日志名称", "Log name")) },
                    placeholder = { Text("Lenovo_LenovoClub_347") },
                    supportingText = {
                        Text(
                            if (draft.logName.length > 100) {
                                localizedText("日志名称不能超过 100 个字符", "Log name cannot exceed 100 characters")
                            } else {
                                localizedText("留空自动生成；也可使用 /dev/null 丢弃日志", "Leave blank to generate automatically, or use /dev/null")
                            }
                        )
                    },
                    isError = draft.logName.length > 100,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.workDir,
                    onValueChange = { draft = draft.copy(workDir = it) },
                    label = { Text(localizedText("工作目录", "Working directory")) },
                    placeholder = { Text(localizedText("留空自动检测，或输入相对/绝对路径", "Leave blank for auto-detection, or enter a relative/absolute path")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.taskBefore,
                    onValueChange = { draft = draft.copy(taskBefore = it) },
                    label = { Text(localizedText("执行前", "Before task")) },
                    placeholder = { Text(localizedText("请输入运行任务前要执行的命令，不能包含 task 命令", "Enter commands to run before the task; task commands are not allowed")) },
                    supportingText = if (beforeHasTaskCommand) {
                        { Text(localizedText("不能包含 task 命令", "A task command is not allowed")) }
                    } else null,
                    isError = beforeHasTaskCommand,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.taskAfter,
                    onValueChange = { draft = draft.copy(taskAfter = it) },
                    label = { Text(localizedText("执行后", "After task")) },
                    placeholder = { Text(localizedText("请输入运行任务后要执行的命令，不能包含 task 命令", "Enter commands to run after the task; task commands are not allowed")) },
                    supportingText = if (afterHasTaskCommand) {
                        { Text(localizedText("不能包含 task 命令", "A task command is not allowed")) }
                    } else null,
                    isError = afterHasTaskCommand,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(draft) }, enabled = canSubmit) {
                Text(localizedText("保存", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("取消", "Cancel")) }
        }
    )
}

@Composable
private fun ScheduleTypeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
