package com.autopanel.feature.task

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.autopanel.core.ui.i18n.localizedText

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskLabelManagementDialog(
    labels: List<TaskLabelSummary>,
    isLoading: Boolean,
    isUpdating: Boolean,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var actionTarget by remember { mutableStateOf<TaskLabelSummary?>(null) }
    var editedName by remember(actionTarget?.name) { mutableStateOf(actionTarget?.name.orEmpty()) }

    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isUpdating) actionTarget = null },
            title = { Text(localizedText("编辑标签", "Edit label")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { if (it.length <= 100) editedName = it },
                        label = { Text(localizedText("标签名称", "Label name")) },
                        supportingText = { Text("${editedName.length}/100") },
                        singleLine = true,
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (target.referenceCount == 0) {
                            localizedText("当前没有任务引用，可安全删除。", "No tasks use this label; it can be deleted safely.")
                        } else {
                            localizedText(
                                "被 ${target.referenceCount} 个任务引用，不能删除。重命名会同步更新这些任务。",
                                "Used by ${target.referenceCount} tasks. It cannot be deleted; renaming updates those tasks."
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target.name, editedName)
                        actionTarget = null
                    },
                    enabled = !isUpdating && editedName.trim().isNotEmpty() && editedName.trim() != target.name
                ) { Text(localizedText("保存", "Save")) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onDelete(target.name)
                            actionTarget = null
                        },
                        enabled = !isUpdating && target.referenceCount == 0
                    ) {
                        Text(localizedText("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { actionTarget = null }, enabled = !isUpdating) {
                        Text(localizedText("取消", "Cancel"))
                    }
                }
            }
        )
    }

    if (actionTarget == null) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) onDismiss() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(localizedText("标签管理", "Label management"), modifier = Modifier.weight(1f))
                    if (isLoading || isUpdating) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        localizedText("长按标签可编辑或删除", "Long-press a label to edit or delete it"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when {
                        isLoading && labels.isEmpty() -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalArrangement = Arrangement.Center
                            ) { CircularProgressIndicator() }
                        }
                        labels.isEmpty() -> Text(localizedText("暂无标签", "No labels"))
                        else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                            items(labels, key = TaskLabelSummary::name) { label ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            enabled = !isUpdating,
                                            onClick = {},
                                            onLongClick = { actionTarget = label }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(label.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            localizedText(
                                                "${label.referenceCount} 个任务引用",
                                                "Used by ${label.referenceCount} tasks"
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { actionTarget = label },
                                        enabled = !isUpdating
                                    ) {
                                        Icon(Icons.Default.MoreVert, localizedText("编辑标签", "Edit label"))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss, enabled = !isUpdating) {
                    Text(localizedText("完成", "Done"))
                }
            }
        )
    }
}
