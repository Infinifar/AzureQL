package com.autopanel.feature.script

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autopanel.core.model.ScriptHistoryVersion
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import com.autopanel.core.ui.i18n.localizedText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ScriptHistoryDialog(
    filename: String,
    state: ScriptHistoryUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectVersion: (String) -> Unit,
    onRequestRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onConfirmRestore: () -> Unit
) {
    val englishUi = isEnglishUi()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, enabled = !state.isRestoring) {
                        Icon(Icons.Default.Close, localizedText("关闭", "Close"))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = localizedText("历史版本", "Version history"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = filename,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onRefresh, enabled = !state.isLoading && !state.isRestoring) {
                        Text(localizedText("刷新", "Refresh"))
                    }
                    if (state.isRestoring) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = onRequestRestore,
                            enabled = state.detail != null &&
                                state.detail.version.hash != state.detail.currentHash
                        ) {
                            Text(localizedText("恢复", "Restore"))
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    text = localizedText(
                        "青龙最多保留 ${state.limit} 个版本；历史仅记录通过面板保存或恢复的 UTF-8 脚本。",
                        "QingLong keeps up to ${state.limit} versions. History covers UTF-8 scripts saved or restored through the panel."
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.versions.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.error?.let { localizedMessage(it, englishUi) }
                                ?: localizedText("暂无历史版本", "No history yet"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                                vertical = 4.dp
                            )
                        ) {
                            items(state.versions, key = ScriptHistoryVersion::id) { version ->
                                FilterChip(
                                    selected = version.id == state.selectedVersionId,
                                    onClick = { onSelectVersion(version.id) },
                                    enabled = !state.isRestoring,
                                    label = {
                                        Text(
                                            "${formatHistoryTime(version.createdAt, englishUi)} · " +
                                                historySourceLabel(version.source, englishUi)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        state.error?.let { error ->
                            Text(
                                localizedMessage(error, englishUi),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        when {
                            state.isLoadingDetail -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                            state.detail != null -> {
                                val rows = remember(
                                    state.detail.version.id,
                                    state.detail.currentHash
                                ) {
                                    buildScriptHistoryDiff(
                                        oldContent = state.detail.version.content.orEmpty(),
                                        currentContent = state.detail.current
                                    )
                                }
                                if (state.detail.version.hash == state.detail.currentHash) {
                                    Text(
                                        localizedText("此版本与当前内容相同", "This version matches the current content"),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                ScriptHistoryDiff(rows)
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = onDismissRestore,
            title = { Text(localizedText("恢复这个历史版本？", "Restore this version?")) },
            text = {
                Text(
                    localizedText(
                        "当前内容会先保存为一个历史版本，再恢复所选内容。若脚本已被其他操作修改，恢复会自动取消。",
                        "The current content is archived before restoration. If another operation changed the script, restoration is cancelled automatically."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore) {
                    Text(localizedText("确认恢复", "Restore"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestore) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun ScriptHistoryDiff(rows: List<ScriptDiffRow>) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            val background = when (row.kind) {
                ScriptDiffKind.ADDED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ScriptDiffKind.REMOVED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ScriptDiffKind.CONTEXT -> Color.Transparent
                ScriptDiffKind.NOTICE -> MaterialTheme.colorScheme.surfaceVariant
            }
            val prefix = when (row.kind) {
                ScriptDiffKind.ADDED -> "+"
                ScriptDiffKind.REMOVED -> "−"
                ScriptDiffKind.CONTEXT -> " "
                ScriptDiffKind.NOTICE -> "…"
            }
            Row(
                Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = prefix,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = if (row.kind == ScriptDiffKind.NOTICE) {
                        localizedText(
                            "差异过长，省略 ${row.text} 行",
                            "Diff truncated: ${row.text} more lines"
                        )
                    } else {
                        row.text.ifEmpty { " " }
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal enum class ScriptDiffKind { CONTEXT, ADDED, REMOVED, NOTICE }

internal data class ScriptDiffRow(
    val key: String,
    val kind: ScriptDiffKind,
    val text: String
)

internal fun buildScriptHistoryDiff(oldContent: String, currentContent: String): List<ScriptDiffRow> {
    val oldLines = oldContent.split('\n')
    val currentLines = currentContent.split('\n')
    val rows = if (oldLines.size.toLong() * currentLines.size.toLong() <= MAX_DIFF_MATRIX_CELLS) {
        lcsDiff(oldLines, currentLines)
    } else {
        oldLines.map { ScriptDiffKind.REMOVED to it } +
            currentLines.map { ScriptDiffKind.ADDED to it }
    }
    val visible = rows.take(MAX_DIFF_ROWS).mapIndexed { index, (kind, text) ->
        ScriptDiffRow("$index-${kind.name}", kind, text.removeSuffix("\r"))
    }.toMutableList()
    if (rows.size > MAX_DIFF_ROWS) {
        visible += ScriptDiffRow(
            key = "truncated",
            kind = ScriptDiffKind.NOTICE,
            text = (rows.size - MAX_DIFF_ROWS).toString()
        )
    }
    return visible
}

private fun lcsDiff(
    oldLines: List<String>,
    currentLines: List<String>
): List<Pair<ScriptDiffKind, String>> {
    val table = Array(oldLines.size + 1) { IntArray(currentLines.size + 1) }
    for (oldIndex in oldLines.indices.reversed()) {
        for (currentIndex in currentLines.indices.reversed()) {
            table[oldIndex][currentIndex] = if (oldLines[oldIndex] == currentLines[currentIndex]) {
                table[oldIndex + 1][currentIndex + 1] + 1
            } else {
                maxOf(table[oldIndex + 1][currentIndex], table[oldIndex][currentIndex + 1])
            }
        }
    }
    val rows = mutableListOf<Pair<ScriptDiffKind, String>>()
    var oldIndex = 0
    var currentIndex = 0
    while (oldIndex < oldLines.size || currentIndex < currentLines.size) {
        when {
            oldIndex < oldLines.size && currentIndex < currentLines.size &&
                oldLines[oldIndex] == currentLines[currentIndex] -> {
                rows += ScriptDiffKind.CONTEXT to oldLines[oldIndex]
                oldIndex++
                currentIndex++
            }
            currentIndex < currentLines.size &&
                (oldIndex == oldLines.size ||
                    table[oldIndex][currentIndex + 1] > table[oldIndex + 1][currentIndex]) -> {
                rows += ScriptDiffKind.ADDED to currentLines[currentIndex++]
            }
            else -> rows += ScriptDiffKind.REMOVED to oldLines[oldIndex++]
        }
    }
    return rows
}

private fun formatHistoryTime(value: String, englishUi: Boolean): String = runCatching {
    val locale = if (englishUi) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)

private fun historySourceLabel(source: String, englishUi: Boolean): String = when (source) {
    "baseline" -> if (englishUi) "Baseline" else "基线"
    "save" -> if (englishUi) "Save" else "保存"
    "restore" -> if (englishUi) "Restore" else "恢复"
    "external" -> if (englishUi) "External" else "外部变更"
    else -> source
}

private const val MAX_DIFF_MATRIX_CELLS = 500_000L
private const val MAX_DIFF_ROWS = 5_000
