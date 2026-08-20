package com.autopanel.feature.env

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.autopanel.core.ui.i18n.localizedText

@Composable
fun EnvImportDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("快捷导入", "Quick import")) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    localizedText(
                        "粘贴 export 语句，每行一条：",
                        "Paste one export statement per line:"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "export KEY=\"value\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(localizedText("export 语句", "Export statements")) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onImport,
                enabled = text.isNotBlank()
            ) { Text(localizedText("导入", "Import")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("取消", "Cancel")) }
        }
    )
}
