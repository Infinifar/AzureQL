package com.autopanel.feature.task

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.ui.i18n.localizedText

@Composable
fun TaskEditDialog(
    task: TaskInfo?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, command: String, schedule: String) -> Unit
) {
    var name by remember(task) { mutableStateOf(task?.name ?: "") }
    var command by remember(task) { mutableStateOf(task?.command ?: "") }
    var schedule by remember(task) { mutableStateOf(task?.schedule ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (task != null) localizedText("编辑任务", "Edit task")
                else localizedText("新建任务", "New task")
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(localizedText("名称", "Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    label = { Text(localizedText("命令", "Command")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = schedule, onValueChange = { schedule = it },
                    label = {
                        Text(
                            localizedText(
                                "定时规则 (秒(可选) 分 时 天 月 周)",
                                "Schedule (optional seconds, minute, hour, day, month, weekday)"
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), command.trim(), schedule.trim()) },
                enabled = name.isNotBlank() && command.isNotBlank() && schedule.isNotBlank()
            ) { Text(localizedText("确定", "Confirm")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("取消", "Cancel")) }
        }
    )
}
