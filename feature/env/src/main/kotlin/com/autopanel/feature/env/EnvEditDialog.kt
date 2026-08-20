package com.autopanel.feature.env

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.ui.i18n.localizedText

private val envNameRegex = Regex("^[a-zA-Z_][a-zA-Z0-9_]*\$")

@Composable
fun EnvEditDialog(
    env: EnvInfo?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, value: String, remarks: String?) -> Unit
) {
    var name by remember(env) { mutableStateOf(env?.name ?: "") }
    var value by remember(env) { mutableStateOf(env?.value ?: "") }
    var remarks by remember(env) { mutableStateOf(env?.remarks ?: "") }

    val isNameValid = name.isBlank() || envNameRegex.matches(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (env != null) localizedText("编辑变量", "Edit variable")
                else localizedText("新建变量", "New variable")
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(localizedText("名称", "Name")) },
                    singleLine = true,
                    isError = !isNameValid,
                    supportingText = if (!isNameValid) {
                        {
                            Text(
                                localizedText(
                                    "名称只能包含字母、数字和下划线，且不能以数字开头",
                                    "Use letters, numbers, and underscores; the name cannot start with a number."
                                )
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text(localizedText("值", "Value")) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = remarks, onValueChange = { remarks = it },
                    label = { Text(localizedText("备注（可选）", "Notes (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), value.trim(), remarks.trim().ifEmpty { null }) },
                enabled = name.isNotBlank() && value.isNotBlank() && isNameValid
            ) { Text(localizedText("确定", "Confirm")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedText("取消", "Cancel")) }
        }
    )
}
