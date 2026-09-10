package com.autopanel.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import com.autopanel.core.ui.i18n.localizedText

@Composable
fun NetworkStorageSettingsScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentEnglishUi by rememberUpdatedState(isEnglishUi())

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is BackupEvent.Message) {
                snackbarHostState.showSnackbar(localizedMessage(event.value, currentEnglishUi))
            }
        }
    }

    NetworkStorageSettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onWebDavUrlChanged = viewModel::onWebDavUrlChanged,
        onWebDavUsernameChanged = viewModel::onWebDavUsernameChanged,
        onWebDavPasswordChanged = viewModel::onWebDavPasswordChanged,
        onWebDavRemoteDirectoryChanged = viewModel::onWebDavRemoteDirectoryChanged,
        onSaveAndTestWebDav = viewModel::saveAndTestWebDav,
        onS3EndpointChanged = viewModel::onS3EndpointChanged,
        onS3AccessKeyIdChanged = viewModel::onS3AccessKeyIdChanged,
        onS3SecretAccessKeyChanged = viewModel::onS3SecretAccessKeyChanged,
        onS3BucketChanged = viewModel::onS3BucketChanged,
        onS3RegionChanged = viewModel::onS3RegionChanged,
        onS3PathStyleChanged = viewModel::onS3PathStyleChanged,
        onS3RemoteDirectoryChanged = viewModel::onS3RemoteDirectoryChanged,
        onSaveAndTestS3 = viewModel::saveAndTestS3
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkStorageSettingsContent(
    state: BackupUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onWebDavUrlChanged: (String) -> Unit = {},
    onWebDavUsernameChanged: (String) -> Unit = {},
    onWebDavPasswordChanged: (String) -> Unit = {},
    onWebDavRemoteDirectoryChanged: (String) -> Unit = {},
    onSaveAndTestWebDav: () -> Unit = {},
    onS3EndpointChanged: (String) -> Unit = {},
    onS3AccessKeyIdChanged: (String) -> Unit = {},
    onS3SecretAccessKeyChanged: (String) -> Unit = {},
    onS3BucketChanged: (String) -> Unit = {},
    onS3RegionChanged: (String) -> Unit = {},
    onS3PathStyleChanged: (Boolean) -> Unit = {},
    onS3RemoteDirectoryChanged: (String) -> Unit = {},
    onSaveAndTestS3: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedProvider by remember { mutableStateOf(NetworkStorageProvider.WEBDAV) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(localizedText("网络存储设置", "Network storage settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                localizedText(
                    "连接信息仅保存在本机，凭据由 Android 系统密钥库加密。保存前会实际测试连接。",
                    "Connection details stay on this device. Credentials are encrypted by Android Keystore and tested before use."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedProvider == NetworkStorageProvider.WEBDAV,
                    onClick = { selectedProvider = NetworkStorageProvider.WEBDAV },
                    label = { Text("WebDAV") },
                    leadingIcon = { Icon(Icons.Default.Storage, null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedProvider == NetworkStorageProvider.S3,
                    onClick = { selectedProvider = NetworkStorageProvider.S3 },
                    label = { Text("S3") },
                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                    modifier = Modifier.weight(1f)
                )
            }

            when (selectedProvider) {
                NetworkStorageProvider.WEBDAV -> if (!state.webDavSettingsLoaded) {
                    NetworkSettingsLoading()
                } else WebDavSettingsForm(
                    state = state,
                    onUrlChanged = onWebDavUrlChanged,
                    onUsernameChanged = onWebDavUsernameChanged,
                    onPasswordChanged = onWebDavPasswordChanged,
                    onRemoteDirectoryChanged = onWebDavRemoteDirectoryChanged,
                    onSaveAndTest = onSaveAndTestWebDav
                )

                NetworkStorageProvider.S3 -> if (!state.s3SettingsLoaded) {
                    NetworkSettingsLoading()
                } else S3SettingsForm(
                    state = state,
                    onEndpointChanged = onS3EndpointChanged,
                    onAccessKeyIdChanged = onS3AccessKeyIdChanged,
                    onSecretAccessKeyChanged = onS3SecretAccessKeyChanged,
                    onBucketChanged = onS3BucketChanged,
                    onRegionChanged = onS3RegionChanged,
                    onPathStyleChanged = onS3PathStyleChanged,
                    onRemoteDirectoryChanged = onS3RemoteDirectoryChanged,
                    onSaveAndTest = onSaveAndTestS3
                )
            }
        }
    }
}

@Composable
private fun NetworkSettingsLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            localizedText("正在加载当前账户设置…", "Loading settings for this account…"),
            Modifier.padding(start = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WebDavSettingsForm(
    state: BackupUiState,
    onUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRemoteDirectoryChanged: (String) -> Unit,
    onSaveAndTest: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val enabled = !state.isBusy && !state.isTestingWebDav && !state.isTestingS3
    Text("WebDAV", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.webDavUrl,
        onValueChange = onUrlChanged,
        label = { Text(localizedText("服务器地址", "Server URL")) },
        placeholder = { Text("https://example.com/dav") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.webDavUsername,
        onValueChange = onUsernameChanged,
        label = { Text(localizedText("用户名", "Username")) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    SecretField(
        value = state.webDavPassword,
        onValueChange = onPasswordChanged,
        label = localizedText("密码", "Password"),
        hasSavedValue = state.webDavHasSavedPassword,
        visible = passwordVisible,
        onToggleVisibility = { passwordVisible = !passwordVisible },
        enabled = enabled
    )
    OutlinedTextField(
        value = state.webDavRemoteDirectory,
        onValueChange = onRemoteDirectoryChanged,
        label = { Text(localizedText("远程目录", "Remote directory")) },
        supportingText = {
            Text(localizedText("默认 AzureQL，可使用多级目录", "Defaults to AzureQL; nested directories are supported"))
        },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    TestConnectionButton(
        isTesting = state.isTestingWebDav,
        enabled = enabled,
        icon = { Icon(Icons.Default.Storage, null) },
        onClick = onSaveAndTest
    )
}

@Composable
private fun S3SettingsForm(
    state: BackupUiState,
    onEndpointChanged: (String) -> Unit,
    onAccessKeyIdChanged: (String) -> Unit,
    onSecretAccessKeyChanged: (String) -> Unit,
    onBucketChanged: (String) -> Unit,
    onRegionChanged: (String) -> Unit,
    onPathStyleChanged: (Boolean) -> Unit,
    onRemoteDirectoryChanged: (String) -> Unit,
    onSaveAndTest: () -> Unit
) {
    var accessKeyVisible by remember { mutableStateOf(false) }
    var secretKeyVisible by remember { mutableStateOf(false) }
    val enabled = !state.isBusy && !state.isTestingWebDav && !state.isTestingS3
    Text("S3", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.s3Endpoint,
        onValueChange = onEndpointChanged,
        label = { Text(localizedText("端点", "Endpoint")) },
        placeholder = { Text("https://s3.example.com") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    SecretField(
        value = state.s3AccessKeyId,
        onValueChange = onAccessKeyIdChanged,
        label = "Access Key ID",
        hasSavedValue = state.s3HasSavedAccessKey,
        visible = accessKeyVisible,
        onToggleVisibility = { accessKeyVisible = !accessKeyVisible },
        enabled = enabled
    )
    SecretField(
        value = state.s3SecretAccessKey,
        onValueChange = onSecretAccessKeyChanged,
        label = "Secret Access Key",
        hasSavedValue = state.s3HasSavedSecretKey,
        visible = secretKeyVisible,
        onToggleVisibility = { secretKeyVisible = !secretKeyVisible },
        enabled = enabled
    )
    OutlinedTextField(
        value = state.s3Bucket,
        onValueChange = onBucketChanged,
        label = { Text(localizedText("存储桶", "Bucket")) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.s3Region,
        onValueChange = onRegionChanged,
        label = { Text(localizedText("区域", "Region")) },
        supportingText = {
            Text(localizedText("默认 auto，会根据服务端响应自动识别", "Defaults to auto and follows the server region response"))
        },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(localizedText("路径样式", "Path-style access"))
            Text(
                localizedText(
                    "兼容 MinIO、R2 等 S3 服务；AWS 虚拟主机样式可关闭",
                    "Compatible with MinIO, R2, and similar services; disable for AWS virtual-host style"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = state.s3PathStyle, onCheckedChange = onPathStyleChanged, enabled = enabled)
    }
    OutlinedTextField(
        value = state.s3RemoteDirectory,
        onValueChange = onRemoteDirectoryChanged,
        label = { Text(localizedText("远程目录", "Remote directory")) },
        supportingText = {
            Text(localizedText("默认 AzureQL，可使用多级目录", "Defaults to AzureQL; nested directories are supported"))
        },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
    TestConnectionButton(
        isTesting = state.isTestingS3,
        enabled = enabled,
        icon = { Icon(Icons.Default.Cloud, null) },
        onClick = onSaveAndTest
    )
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hasSavedValue: Boolean,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            if (hasSavedValue) Text(localizedText("已保存；留空则保持不变", "Saved; leave blank to keep it"))
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    localizedText("显示或隐藏凭据", "Show or hide credential")
                )
            }
        },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TestConnectionButton(
    isTesting: Boolean,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        if (isTesting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else icon()
        Text(
            localizedText("保存并测试连接", "Save and test connection"),
            Modifier.padding(start = 8.dp)
        )
    }
}
