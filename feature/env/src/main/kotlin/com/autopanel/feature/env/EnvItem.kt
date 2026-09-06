package com.autopanel.feature.env

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.model.EnvStatus
import com.autopanel.core.ui.i18n.localizedText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnvItem(
    env: EnvInfo,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onToggleStatus: () -> Unit,
    onTogglePin: () -> Unit,
    onLongPress: () -> Unit
) {
    val isEnabled = env.status == EnvStatus.ENABLED
    val enabledColor = Color(0xFF2E7D32)
    val disabledColor = Color(0xFFC62828)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isBatchMode) onToggleSelection() },
                onLongClick = { if (!isBatchMode) onLongPress() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBatchMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                Spacer(Modifier.width(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    env.name ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val remarks = env.remarks
                if (!remarks.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        remarks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isBatchMode) {
                Spacer(Modifier.width(8.dp))
                // 批量模式下展示状态徽章（勾选框负责选择，状态切换走批量启用/禁用）
                Text(
                    if (isEnabled) localizedText("已启用", "Enabled")
                    else localizedText("已禁用", "Disabled"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isEnabled) enabledColor else disabledColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            } else {
                IconButton(onClick = onTogglePin) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = if (env.pinned) {
                            localizedText("取消置顶", "Unpin")
                        } else {
                            localizedText("置顶", "Pin")
                        },
                        tint = if (env.pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                val toggleDescription = if (isEnabled) {
                    localizedText("禁用变量", "Disable variable")
                } else {
                    localizedText("启用变量", "Enable variable")
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleStatus() },
                    modifier = Modifier
                        .scale(0.78f)
                        .semantics { contentDescription = toggleDescription }
                )
            }
        }
    }
}
