package com.autopanel.feature.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import com.autopanel.core.ui.i18n.localizedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val english by rememberUpdatedState(isEnglishUi())
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is NotificationSettingsEvent.Message -> snackbar.showSnackbar(
                    localizedMessage(event.text, english)
                )
            }
        }
    }
    NotificationSettingsContent(
        state = state,
        onBack = onBack,
        onProviderSelected = viewModel::selectProvider,
        onFieldChanged = viewModel::updateField,
        onSave = viewModel::testAndSave,
        onRetry = viewModel::load,
        snackbarHostState = snackbar,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onBack: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onFieldChanged: (String, String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(localizedText("通知设置", "Notification settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.loadError != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(localizedMessage(state.loadError, isEnglishUi()), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) { Text(localizedText("重试", "Retry")) }
            }
            else -> NotificationForm(
                state = state,
                onProviderSelected = onProviderSelected,
                onFieldChanged = onFieldChanged,
                onSave = onSave,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun NotificationForm(
    state: NotificationSettingsUiState,
    onProviderSelected: (String) -> Unit,
    onFieldChanged: (String, String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val english = isEnglishUi()
    val provider = NotificationProviderRegistry.find(state.selectedType)
    val revealed = remember(state.selectedType) { mutableStateMapOf<String, Boolean>() }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            localizedText(
                "保存前会由青龙服务端发送一条测试通知；测试失败时不会覆盖当前配置。",
                "QingLong sends a test notification before saving. A failed test does not replace the current configuration."
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ProviderSelector(
            selected = provider,
            unsupportedType = state.unsupportedType,
            english = english,
            onSelected = onProviderSelected
        )
        if (state.unsupportedType != null) {
            Text(
                localizedText(
                    "服务端返回了当前版本不认识的渠道“${state.unsupportedType}”。原配置保持只读；选择受支持渠道后才可替换。",
                    "The server returned an unsupported provider “${state.unsupportedType}”. It remains read-only until you choose a supported provider."
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            provider?.fields?.forEach { field ->
                when (field.kind) {
                    NotificationFieldKind.Choice -> ChoiceField(
                        field = field,
                        value = state.values[field.key].orEmpty(),
                        english = english,
                        isError = field.key in state.fieldErrors,
                        onChanged = { onFieldChanged(field.key, it) }
                    )
                    else -> OutlinedTextField(
                        value = state.values[field.key].orEmpty(),
                        onValueChange = { onFieldChanged(field.key, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (english) field.en else field.zh) },
                        supportingText = if (field.required) ({ Text(localizedText("必填", "Required")) }) else null,
                        isError = field.key in state.fieldErrors,
                        singleLine = field.kind != NotificationFieldKind.Multiline,
                        minLines = if (field.kind == NotificationFieldKind.Multiline) 3 else 1,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (field.kind) {
                                NotificationFieldKind.Number -> KeyboardType.Number
                                NotificationFieldKind.Secret -> KeyboardType.Password
                                else -> KeyboardType.Text
                            }
                        ),
                        visualTransformation = if (
                            field.kind == NotificationFieldKind.Secret && revealed[field.key] != true
                        ) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = if (field.kind == NotificationFieldKind.Secret) ({
                            IconButton(onClick = { revealed[field.key] = revealed[field.key] != true }) {
                                Icon(
                                    if (revealed[field.key] == true) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    localizedText("切换密钥可见性", "Toggle secret visibility")
                                )
                            }
                        }) else null
                    )
                }
            }
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().testTag("notification_test_save"),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).testTag("notification_save_progress"),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else Text(localizedText("发送测试通知并保存", "Send test notification and save"))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProviderSelector(
    selected: NotificationProviderSpec?,
    unsupportedType: String?,
    english: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                unsupportedType ?: selected?.let { if (english) it.en else it.zh }.orEmpty(),
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NotificationProviderRegistry.providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(if (english) provider.en else provider.zh) },
                    onClick = {
                        expanded = false
                        onSelected(provider.type)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChoiceField(
    field: NotificationFieldSpec,
    value: String,
    english: Boolean,
    isError: Boolean,
    onChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = field.choices.firstOrNull { it.value == value }
    Column {
        Text(if (english) field.en else field.zh, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { if (english) it.en else it.zh }.orEmpty(), Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                field.choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(if (english) choice.en else choice.zh) },
                        onClick = { expanded = false; onChanged(choice.value) }
                    )
                }
            }
        }
        if (isError) Text(localizedText("必填", "Required"), color = MaterialTheme.colorScheme.error)
    }
}
