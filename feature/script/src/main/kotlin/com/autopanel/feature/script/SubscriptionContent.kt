package com.autopanel.feature.script

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.autopanel.core.model.SubscriptionDraft
import com.autopanel.core.model.SubscriptionInfo
import com.autopanel.core.ui.components.WindowedLogViewer
import com.autopanel.core.ui.i18n.localizedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionsContent(
    subscriptions: List<SubscriptionInfo>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    busyIds: Set<Int>,
    onRefresh: () -> Unit,
    onEdit: (SubscriptionInfo) -> Unit,
    onDelete: (SubscriptionInfo) -> Unit,
    onToggleEnabled: (SubscriptionInfo) -> Unit,
    onRunOrStop: (SubscriptionInfo) -> Unit,
    onOpenLog: (SubscriptionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            subscriptions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    localizedText("暂无订阅", "No subscriptions"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                items(subscriptions, key = { it.id ?: it.hashCode() }) { subscription ->
                    SubscriptionCard(
                        subscription = subscription,
                        busy = subscription.id in busyIds,
                        onEdit = { onEdit(subscription) },
                        onDelete = { onDelete(subscription) },
                        onToggleEnabled = { onToggleEnabled(subscription) },
                        onRunOrStop = { onRunOrStop(subscription) },
                        onOpenLog = { onOpenLog(subscription) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: SubscriptionInfo,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRunOrStop: () -> Unit,
    onOpenLog: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isRunning = subscription.status == 0 || subscription.status == 3
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLog),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        subscription.name ?: subscription.alias ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subscription.localizedStatus(),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            subscription.disabled -> MaterialTheme.colorScheme.error
                            isRunning -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = onRunOrStop, enabled = !subscription.disabled) {
                        Icon(
                            if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isRunning) localizedText("停止订阅", "Stop subscription")
                            else localizedText("运行订阅", "Run subscription")
                        )
                    }
                    IconButton(onClick = onToggleEnabled) {
                        Icon(
                            if (subscription.disabled) Icons.Default.CheckCircle else Icons.Default.Block,
                            if (subscription.disabled) localizedText("启用订阅", "Enable subscription")
                            else localizedText("禁用订阅", "Disable subscription")
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, localizedText("更多操作", "More actions"))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(localizedText("编辑", "Edit")) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit() },
                            enabled = !busy
                        )
                        DropdownMenuItem(
                            text = { Text(localizedText("删除", "Delete")) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuExpanded = false; onDelete() },
                            enabled = !busy
                        )
                    }
                }
            }
            Text(
                subscription.url ?: "--",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(
                subscription.localizedSchedule(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, end = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionLogSheet(
    state: SubscriptionLogUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onLoadOlder: () -> Unit,
    onLoadNewer: () -> Unit,
    onCopy: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 680.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.subscription.name ?: state.subscription.alias ?: localizedText("订阅日志", "Subscription log"),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (state.isStreaming) {
                            localizedText("拉取日志 · 实时更新中", "Pull log · Live")
                        } else {
                            localizedText("拉取日志", "Pull log")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isStreaming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onRetry, enabled = !state.isLoading) {
                    Icon(Icons.Default.Refresh, localizedText("刷新日志", "Refresh log"))
                }
                IconButton(onClick = { onCopy(state.content) }, enabled = state.content.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, localizedText("复制日志", "Copy log"))
                }
            }

            if (state.canLoadOlder || state.canLoadNewer) {
                Row {
                    if (state.canLoadOlder) {
                        TextButton(onClick = onLoadOlder, enabled = !state.isLoadingOlder) {
                            if (state.isLoadingOlder) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(localizedText("加载更早日志", "Load older"))
                        }
                    }
                    if (state.canLoadNewer) {
                        TextButton(onClick = onLoadNewer, enabled = !state.isLoadingOlder) {
                            Text(localizedText("加载更新日志", "Load newer"))
                        }
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.content.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(localizedText("重试", "Retry")) }
                }
                state.content.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        localizedText("暂无日志", "No log output"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    WindowedLogViewer(
                        content = state.content,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
internal fun SubscriptionEditorDialog(
    draft: SubscriptionDraft,
    isSaving: Boolean,
    onDraftChange: (SubscriptionDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                if (draft.id == null) localizedText("新建订阅", "New subscription")
                else localizedText("编辑订阅", "Edit subscription")
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text(localizedText("名称", "Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(localizedText("订阅类型", "Subscription type"), style = MaterialTheme.typography.labelMedium)
                SubscriptionTypeSelector(
                    selected = draft.type,
                    onSelected = { onDraftChange(draft.copy(type = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (draft.type == "private-repo") {
                    Text(localizedText("拉取方式", "Authentication"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draft.pullType == "ssh-key",
                            onClick = { onDraftChange(draft.copy(pullType = "ssh-key")) },
                            label = { Text(localizedText("私钥", "SSH key")) }
                        )
                        FilterChip(
                            selected = draft.pullType == "user-pwd",
                            onClick = { onDraftChange(draft.copy(pullType = "user-pwd")) },
                            label = { Text(localizedText("用户名/Token", "Username / token")) }
                        )
                    }
                    if (draft.pullType == "user-pwd") {
                        OutlinedTextField(
                            value = draft.username,
                            onValueChange = { onDraftChange(draft.copy(username = it)) },
                            label = { Text(localizedText("用户名", "Username")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.password,
                            onValueChange = { onDraftChange(draft.copy(password = it)) },
                            label = { Text(localizedText("密码/Token", "Password / token")) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = draft.privateKey,
                            onValueChange = { onDraftChange(draft.copy(privateKey = it)) },
                            label = { Text(localizedText("私钥", "Private key")) },
                            placeholder = { Text(localizedText("请输入私钥", "Enter private key")) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.url,
                    onValueChange = { onDraftChange(draft.copy(url = it)) },
                    label = { Text(localizedText("链接", "URL")) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (draft.type != "file") {
                    OutlinedTextField(
                        value = draft.branch,
                        onValueChange = { onDraftChange(draft.copy(branch = it)) },
                        label = { Text(localizedText("分支（可选）", "Branch (optional)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.whitelist,
                        onValueChange = { onDraftChange(draft.copy(whitelist = it)) },
                        label = { Text(localizedText("白名单", "Whitelist")) },
                        placeholder = {
                            Text(localizedText("例如：得物森林|anmusi", "For example: keyword1|keyword2"))
                        },
                        supportingText = {
                            Text(localizedText("多个关键词使用竖线分割，支持正则表达式", "Separate keywords with |; regular expressions are supported"))
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.blacklist,
                        onValueChange = { onDraftChange(draft.copy(blacklist = it)) },
                        label = { Text(localizedText("黑名单", "Blacklist")) },
                        placeholder = {
                            Text(localizedText("请输入脚本筛选黑名单关键词，多个关键词竖线分割", "Enter script blacklist keywords separated by |"))
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.dependences,
                        onValueChange = { onDraftChange(draft.copy(dependences = it)) },
                        label = { Text(localizedText("依赖文件", "Dependency files")) },
                        placeholder = {
                            Text(localizedText("请输入脚本依赖文件关键词，多个关键词竖线分割", "Enter dependency file keywords separated by |"))
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.extensions,
                        onValueChange = { onDraftChange(draft.copy(extensions = it)) },
                        label = { Text(localizedText("文件后缀", "File extensions")) },
                        placeholder = { Text(localizedText("请输入文件后缀", "Enter file extensions")) },
                        supportingText = {
                            Text(localizedText("多个后缀使用空格分隔", "Separate multiple extensions with spaces"))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.subBefore,
                        onValueChange = { onDraftChange(draft.copy(subBefore = it)) },
                        label = { Text(localizedText("执行前", "Before subscription")) },
                        placeholder = {
                            Text(localizedText("请输入运行订阅前要执行的命令", "Enter commands to run before the subscription"))
                        },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.subAfter,
                        onValueChange = { onDraftChange(draft.copy(subAfter = it)) },
                        label = { Text(localizedText("执行后", "After subscription")) },
                        placeholder = {
                            Text(localizedText("请输入运行订阅后要执行的命令", "Enter commands to run after the subscription"))
                        },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = draft.alias,
                    onValueChange = { onDraftChange(draft.copy(alias = it)) },
                    label = { Text(localizedText("唯一值（留空自动生成）", "Alias (generated if empty)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(localizedText("定时类型", "Schedule type"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.scheduleType == "crontab",
                        onClick = { onDraftChange(draft.copy(scheduleType = "crontab")) },
                        label = { Text("crontab") }
                    )
                    FilterChip(
                        selected = draft.scheduleType == "interval",
                        onClick = { onDraftChange(draft.copy(scheduleType = "interval")) },
                        label = { Text("interval") }
                    )
                }
                if (draft.scheduleType == "crontab") {
                    OutlinedTextField(
                        value = draft.schedule,
                        onValueChange = { onDraftChange(draft.copy(schedule = it)) },
                        label = { Text(localizedText("定时规则", "Schedule")) },
                        placeholder = { Text("0 0 * * *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.intervalValue.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { onDraftChange(draft.copy(intervalValue = it.coerceAtLeast(1))) }
                            },
                            label = { Text(localizedText("间隔", "Every")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Column(Modifier.weight(1.4f)) {
                            listOf("minutes", "hours", "days").forEach { unit ->
                                FilterChip(
                                    selected = draft.intervalType == unit,
                                    onClick = { onDraftChange(draft.copy(intervalType = unit)) },
                                    label = { Text(unit.localizedIntervalUnit()) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.proxy,
                    onValueChange = { onDraftChange(draft.copy(proxy = it)) },
                    label = { Text(localizedText("代理", "Proxy")) },
                    placeholder = {
                        Text(
                            if (draft.type == "private-repo") {
                                localizedText("SOCK5 代理，例如 127.0.0.1:1080", "SOCK5 proxy, for example 127.0.0.1:1080")
                            } else {
                                localizedText("HTTP/SOCK5 代理，例如 http://127.0.0.1:1080", "HTTP/SOCK5 proxy, for example http://127.0.0.1:1080")
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ToggleRow(
                    label = localizedText("自动添加任务", "Automatically add tasks"),
                    checked = draft.autoAddCron,
                    onCheckedChange = { onDraftChange(draft.copy(autoAddCron = it)) }
                )
                ToggleRow(
                    label = localizedText("自动删除失效任务", "Automatically remove obsolete tasks"),
                    checked = draft.autoDelCron,
                    onCheckedChange = { onDraftChange(draft.copy(autoDelCron = it)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !isSaving) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(localizedText("保存", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(localizedText("取消", "Cancel"))
            }
        }
    )
}

@Composable
private fun SubscriptionTypeSelector(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        "public-repo" to localizedText("公开仓库", "Public repo"),
        "private-repo" to localizedText("私有仓库", "Private repo"),
        "file" to localizedText("单文件", "Single file")
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelected(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.weight(1f),
                icon = {}
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SubscriptionInfo.localizedStatus(): String = when {
    disabled -> localizedText("已禁用", "Disabled")
    status == 0 -> localizedText("运行中", "Running")
    status == 3 -> localizedText("排队中", "Queued")
    else -> localizedText("空闲", "Idle")
}

@Composable
private fun SubscriptionInfo.localizedSchedule(): String = if (scheduleType == "interval") {
    val interval = intervalSchedule
    if (interval == null) "--" else localizedText(
        "每 ${interval.value} ${interval.type.localizedIntervalUnit()}",
        "Every ${interval.value} ${interval.type.localizedIntervalUnit()}"
    )
} else {
    schedule ?: "--"
}

@Composable
private fun String.localizedIntervalUnit(): String = when (this) {
    "seconds" -> localizedText("秒", "seconds")
    "minutes" -> localizedText("分钟", "minutes")
    "hours" -> localizedText("小时", "hours")
    else -> localizedText("天", "days")
}
