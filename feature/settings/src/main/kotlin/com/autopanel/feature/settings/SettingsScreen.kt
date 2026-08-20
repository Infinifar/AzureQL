package com.autopanel.feature.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.AppInfo
import com.autopanel.core.model.AppScopes
import com.autopanel.core.data.preferences.LocalAppPreferences
import com.autopanel.core.ui.security.AuthenticationResult
import com.autopanel.core.ui.security.DeviceAuthenticator
import com.autopanel.core.ui.theme.ThemePresetColors
import com.autopanel.core.ui.theme.parseSeedColor
import com.autopanel.core.ui.i18n.localizedMessage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch

private const val PROJECT_URL = "https://github.com/yisilan83/AzureQL"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenDependencies: () -> Unit,
    onOpenLogs: () -> Unit,
    clientVersion: String,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val themeColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val isEnglishUi = LocalConfiguration.current.locales[0].language == "en"
    val currentEnglishUi by rememberUpdatedState(isEnglishUi)
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeColorDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showSwitchAccountConfirm by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, value: String?) {
        val v = value ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, v))
        scope.launch {
            snackbarHostState.showSnackbar(if (isEnglishUi) "$label copied" else "$label 已复制")
        }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.text, currentEnglishUi)
                )
            }
        }
    }

    if (state.showPasswordDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPasswordDialog,
            title = { Text(settingsText("修改密码", "Change password")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.accountUsername,
                        onValueChange = viewModel::onAccountUsernameChanged,
                        label = { Text(settingsText("当前用户名", "Current username")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.newPassword, onValueChange = viewModel::onNewPasswordChanged,
                        label = { Text(settingsText("新密码", "New password")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::changePassword,
                    enabled = state.accountUsername.isNotEmpty() && state.newPassword.isNotEmpty()
                ) { Text(settingsText("确定", "Confirm")) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPasswordDialog) { Text(settingsText("取消", "Cancel")) } }
        )
    }

    if (state.showAppDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAppDialog,
            title = {
                Text(
                    if (state.editingApp == null) settingsText("新建应用", "New application")
                    else settingsText("编辑应用", "Edit application")
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = state.editAppName, onValueChange = viewModel::onAppNameChanged,
                        label = { Text(settingsText("名称", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(settingsText("权限", "Scopes"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppScopes.ALL.forEach { scope ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = scope in state.editAppScopes,
                                role = Role.Checkbox,
                                onValueChange = { viewModel.toggleAppScope(scope) }
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = scope in state.editAppScopes,
                                onCheckedChange = null
                            )
                            Text(appScopeLabel(scope), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveApp, enabled = state.editAppName.isNotBlank()) { Text(settingsText("保存", "Save")) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissAppDialog) { Text(settingsText("取消", "Cancel")) } }
        )
    }

    state.confirmDeleteApp?.let { app ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text(settingsText("删除应用", "Delete application")) },
            text = {
                Text(
                    if (isEnglishUi) "Delete “${app.name ?: "--"}”? This cannot be undone."
                    else "确定要删除应用「${app.name ?: "--"}」吗？此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDeleteApp,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(settingsText("删除", "Delete")) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteConfirm) { Text(settingsText("取消", "Cancel")) } }
        )
    }

    state.confirmResetApp?.let { app ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResetConfirm,
            title = { Text(settingsText("重置密钥", "Reset secret")) },
            text = {
                Text(
                    if (isEnglishUi) {
                        "Reset the client secret for “${app.name ?: "--"}”? The old secret will stop working immediately."
                    } else {
                        "确定要重置应用「${app.name ?: "--"}」的 Client Secret 吗？重置后旧密钥将立即失效。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmResetApp,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(settingsText("重置", "Reset")) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissResetConfirm) { Text(settingsText("取消", "Cancel")) } }
        )
    }

    fun requestBiometricChange(enabled: Boolean) {
        val activity = context.findActivity()
        if (activity == null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (isEnglishUi) "Unable to start authentication" else "无法启动身份验证"
                )
            }
            return
        }
        DeviceAuthenticator.authenticate(
            activity = activity,
            title = if (isEnglishUi) {
                if (enabled) "Enable biometric app lock" else "Disable biometric app lock"
            } else {
                if (enabled) "启用生物识别验证" else "关闭生物识别验证"
            },
            subtitle = if (isEnglishUi) "Verify your identity" else "请验证本人身份"
        ) { result ->
            when (result) {
                AuthenticationResult.Success -> viewModel.setBiometricEnabled(enabled)
                AuthenticationResult.Failed -> Unit
                is AuthenticationResult.Error -> scope.launch {
                    snackbarHostState.showSnackbar(
                        result.message.ifBlank {
                            if (isEnglishUi) "Authentication was not completed" else "身份验证未完成"
                        }
                    )
                }
            }
        }
    }

    if (state.showTwoFactorSetup) {
        val qrCode = remember(state.twoFactorUrl) {
            state.twoFactorUrl.takeIf(String::isNotBlank)?.let { url ->
                runCatching { createQrCode(url) }.getOrNull()
            }
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissTwoFactorSetup,
            title = { Text(settingsText("启用两步验证", "Enable two-factor authentication")) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(settingsText("使用验证器扫描二维码，或手动添加下面的密钥，然后输入生成的验证码。", "Scan the QR code with your authenticator, or add the secret manually, then enter the generated code."))
                    Spacer(Modifier.height(12.dp))
                    if (qrCode != null) {
                        Image(
                            bitmap = qrCode,
                            contentDescription = settingsText("两步验证二维码", "Two-factor QR code"),
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        state.twoFactorSecret,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(
                        onClick = {
                            copyToClipboard("两步验证密钥", state.twoFactorSecret)
                        }
                    ) { Text(settingsText("复制密钥", "Copy secret")) }
                    OutlinedTextField(
                        value = state.twoFactorCode,
                        onValueChange = viewModel::onTwoFactorCodeChanged,
                        label = { Text(settingsText("验证码", "Verification code")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::activateTwoFactor,
                    enabled = state.twoFactorCode.isNotBlank() && !state.isLoadingSecurity
                ) { Text(settingsText("启用", "Enable")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTwoFactorSetup) { Text(settingsText("取消", "Cancel")) }
            }
        )
    }

    if (state.confirmDeactivateTwoFactor) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeactivateTwoFactor,
            title = { Text(settingsText("关闭两步验证", "Disable two-factor authentication")) },
            text = { Text(settingsText("关闭后登录将不再要求一次性验证码。确定继续吗？", "Sign-in will no longer require a one-time code. Continue?")) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deactivateTwoFactor,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(settingsText("关闭", "Disable")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeactivateTwoFactor) { Text(settingsText("取消", "Cancel")) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(settingsText("语言", "Language")) },
            text = {
                Column {
                    RadioOption(
                        label = settingsText("跟随系统", "Follow system"),
                        value = LocalAppPreferences.LANGUAGE_SYSTEM,
                        selected = state.languageTag,
                        onSelect = { languageTag ->
                            viewModel.setLanguage(languageTag)
                            showLanguageDialog = false
                            context.findActivity()?.recreate()
                        }
                    )
                    RadioOption(
                        label = "简体中文",
                        value = LocalAppPreferences.LANGUAGE_CHINESE,
                        selected = state.languageTag,
                        onSelect = { languageTag ->
                            viewModel.setLanguage(languageTag)
                            showLanguageDialog = false
                            context.findActivity()?.recreate()
                        }
                    )
                    RadioOption(
                        label = "English",
                        value = LocalAppPreferences.LANGUAGE_ENGLISH,
                        selected = state.languageTag,
                        onSelect = { languageTag ->
                            viewModel.setLanguage(languageTag)
                            showLanguageDialog = false
                            context.findActivity()?.recreate()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(settingsText("取消", "Cancel"))
                }
            }
        )
    }

    if (showThemeColorDialog) {
        AlertDialog(
            onDismissRequest = { showThemeColorDialog = false },
            title = { Text(settingsText("主题颜色", "Theme color")) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setDynamicColor(true)
                                showThemeColorDialog = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = dynamicColor, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(settingsText("跟随系统壁纸（动态取色）", "Use system wallpaper (dynamic color)"))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        settingsText("手动选择", "Pick a color"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ThemeColorGrid(
                        selectedColor = if (dynamicColor) null else parseSeedColor(themeColor),
                        onSelect = { color ->
                            viewModel.setDynamicColor(false)
                            viewModel.setThemeColor(color)
                            showThemeColorDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeColorDialog = false }) {
                    Text(settingsText("取消", "Cancel"))
                }
            }
        )
    }

    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text(settingsText("显示模式", "Display mode")) },
            text = {
                Column {
                    RadioOption(settingsText("跟随系统", "System"), "system", darkMode) { mode ->
                        viewModel.setDarkMode(mode)
                        showDarkModeDialog = false
                    }
                    RadioOption(settingsText("浅色", "Light"), "light", darkMode) { mode ->
                        viewModel.setDarkMode(mode)
                        showDarkModeDialog = false
                    }
                    RadioOption(settingsText("深色", "Dark"), "dark", darkMode) { mode ->
                        viewModel.setDarkMode(mode)
                        showDarkModeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDarkModeDialog = false }) {
                    Text(settingsText("取消", "Cancel"))
                }
            }
        )
    }

    if (showSwitchAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountConfirm = false },
            title = { Text(settingsText("切换账户", "Switch account")) },
            text = {
                Text(
                    settingsText(
                        "确定要返回登录页并切换账户吗？当前服务器会保留在已保存列表中。",
                        "Return to sign in and switch accounts? The current server will remain saved."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSwitchAccountConfirm = false
                        onLogout()
                    }
                ) { Text(settingsText("切换", "Switch")) }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountConfirm = false }) {
                    Text(settingsText("取消", "Cancel"))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(settingsText("设置", "Settings")) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, settingsText("退出登录", "Sign out"))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                settingsText("客户端设置", "Client settings"),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            ClientSettingsRow(
                icon = Icons.Default.Info,
                title = settingsText("客户端版本", "Client version"),
                trailingContent = {
                    Text(clientVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            )
            ClientSettingsRow(
                icon = Icons.Default.Language,
                title = settingsText("应用语言", "App language"),
                description = languageLabel(state.languageTag),
                onClick = { showLanguageDialog = true },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            )
            ClientSettingsRow(
                icon = Icons.Default.DarkMode,
                title = settingsText("显示模式", "Display mode"),
                description = darkModeLabel(darkMode),
                onClick = { showDarkModeDialog = true },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            )
            ClientSettingsRow(
                icon = Icons.Default.ColorLens,
                title = settingsText("主题颜色", "Theme color"),
                description = themeColorLabel(dynamicColor),
                onClick = { showThemeColorDialog = true },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!dynamicColor) {
                            ColorSwatch(
                                color = parseSeedColor(themeColor),
                                selected = false,
                                onClick = null,
                                size = 28.dp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            )
            ClientSettingsRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = settingsText("项目主页", "Project homepage"),
                description = settingsText("在 GitHub 查看 AzureQL", "View AzureQL on GitHub"),
                onClick = { uriHandler.openUri(PROJECT_URL) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                settingsText("服务器管理", "Server management"),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            ClientSettingsRow(
                icon = Icons.Default.Dns,
                title = settingsText("服务端版本", "Server version"),
                trailingContent = {
                    Text(
                        state.serverVersion ?: settingsText("未知", "Unknown"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            )
            ServerManagementRow(
                title = settingsText("数据备份与恢复", "Backup & restore"),
                description = settingsText("导出或恢复青龙官方备份", "Export or restore an official QingLong backup"),
                icon = Icons.Default.SettingsBackupRestore,
                onClick = onOpenBackup
            )
            ServerManagementRow(
                title = settingsText("依赖管理", "Dependencies"),
                description = settingsText("依赖列表、镜像、代理与缓存", "Packages, mirrors, proxies and caches"),
                icon = Icons.Default.Extension,
                onClick = onOpenDependencies
            )
            ServerManagementRow(
                title = settingsText("任务日志", "Task logs"),
                description = settingsText("查看青龙任务日志文件", "Browse QingLong task log files"),
                icon = Icons.Default.Description,
                onClick = onOpenLogs
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(
                title = settingsText("系统配置", "System configuration"),
                description = settingsText("日志保留周期与任务并发数", "Log retention and task concurrency"),
                icon = Icons.Default.Tune,
                expanded = state.configExpanded,
                onClick = viewModel::toggleConfigExpanded,
                action = {
                    IconButton(onClick = viewModel::loadSystemConfig) {
                        Icon(Icons.Default.Refresh, settingsText("刷新", "Refresh"))
                    }
                }
            )
            AnimatedVisibility(state.configExpanded) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (state.isLoadingConfig) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    else {
                        OutlinedTextField(
                            value = state.editLogFrequency, onValueChange = viewModel::onLogFrequencyChanged,
                            label = { Text(settingsText("日志删除频率 (天)", "Log retention (days)")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.editConcurrency, onValueChange = viewModel::onConcurrencyChanged,
                            label = { Text(settingsText("并发数", "Concurrency")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::saveSystemConfig, modifier = Modifier.fillMaxWidth()) {
                            Text(settingsText("保存配置", "Save configuration"))
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(
                title = settingsText("应用设置", "Application access"),
                description = settingsText("管理 Client ID、密钥与 API 权限", "Manage client IDs, secrets and API scopes"),
                icon = Icons.Default.Apps,
                expanded = state.appsExpanded,
                onClick = viewModel::toggleAppsExpanded,
                action = {
                    IconButton(onClick = viewModel::loadApps) {
                        Icon(Icons.Default.Refresh, settingsText("刷新", "Refresh"))
                    }
                }
            )
            AnimatedVisibility(state.appsExpanded) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    when {
                        state.isLoadingApps -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                        state.apps.isEmpty() -> Text(settingsText("暂无应用", "No applications"), Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> {
                            state.apps.forEach { app ->
                                AppCard(
                                    app = app,
                                    onEdit = { viewModel.showEditApp(app) },
                                    onResetSecret = { viewModel.requestResetSecret(app) },
                                    onDelete = { viewModel.requestDeleteApp(app) },
                                    onCopyClientId = { copyToClipboard("Client ID", app.clientId) },
                                    onCopyClientSecret = { copyToClipboard("Client Secret", app.clientSecret) }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::showCreateApp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text(settingsText("新建应用", "New application"))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(
                title = settingsText("登录日志", "Login history"),
                description = settingsText("查看最近的登录地址、IP 与结果", "Recent addresses, IPs and outcomes"),
                icon = Icons.Default.History,
                expanded = state.logsExpanded,
                onClick = viewModel::toggleLogsExpanded
            )
            AnimatedVisibility(state.logsExpanded) {
                Column {
                    if (state.isLoadingLogs) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    else if (state.loginLogs.isEmpty()) Text(settingsText("暂无记录", "No records"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        state.loginLogs.take(20).forEach { log ->
                            Card(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    val addr = log.address
                                    if (!addr.isNullOrBlank()) Text(addr, style = MaterialTheme.typography.bodyMedium)
                                    val ip = log.ip
                                    if (!ip.isNullOrBlank()) {
                                        Text(
                                            ip,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val t = log.time
                                    if (!t.isNullOrBlank()) Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (log.status == 1) settingsText("失败", "Failed")
                                        else settingsText("成功", "Succeeded"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (log.status == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(
                title = settingsText("安全设置", "Security"),
                description = settingsText(
                    "修改账户密码、两步验证与本地应用锁",
                    "Password, two-factor authentication and app lock"
                ),
                icon = Icons.Default.Security,
                expanded = state.securityExpanded,
                onClick = viewModel::toggleSecurityExpanded
            )
            AnimatedVisibility(state.securityExpanded) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (state.isLoadingSecurity) {
                        CircularProgressIndicator(
                            Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = viewModel::showPasswordDialog,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(settingsText("修改账户密码", "Change account password")) }
                        SettingsNavigationRow(
                            headlineContent = { Text(settingsText("两步验证", "Two-factor authentication")) },
                            supportingContent = {
                                Text(
                                    if (state.twoFactorActivated) settingsText("已启用", "Enabled")
                                    else settingsText("未启用", "Disabled")
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.twoFactorActivated,
                                    onCheckedChange = { enabled ->
                                        if (enabled) viewModel.startTwoFactorSetup()
                                        else viewModel.requestDeactivateTwoFactor()
                                    }
                                )
                            },
                            onClick = {
                                if (state.twoFactorActivated) {
                                    viewModel.requestDeactivateTwoFactor()
                                } else {
                                    viewModel.startTwoFactorSetup()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        SettingsNavigationRow(
                            headlineContent = { Text(settingsText("生物识别验证", "Biometric app lock")) },
                            supportingContent = {
                                Text(
                                    if (state.biometricEnabled) {
                                        settingsText("已启用；返回应用时验证", "Enabled; verify when returning")
                                    } else {
                                        settingsText("未启用", "Disabled")
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.biometricEnabled,
                                    onCheckedChange = ::requestBiometricChange
                                )
                            },
                            onClick = { requestBiometricChange(!state.biometricEnabled) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            ServerManagementRow(
                title = settingsText("切换账户", "Switch account"),
                description = settingsText(
                    "返回登录页并选择已保存的服务器",
                    "Return to sign in and choose a saved server"
                ),
                icon = Icons.Default.ManageAccounts,
                onClick = { showSwitchAccountConfirm = true }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AppCard(
    app: AppInfo,
    onEdit: () -> Unit,
    onResetSecret: () -> Unit,
    onDelete: () -> Unit,
    onCopyClientId: () -> Unit,
    onCopyClientSecret: () -> Unit
) {
    val isEnglish = LocalConfiguration.current.locales[0].language == "en"
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.name ?: "--", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, settingsText("编辑", "Edit")) }
                IconButton(onClick = onResetSecret) { Icon(Icons.Default.Key, settingsText("重置密钥", "Reset secret")) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, settingsText("删除", "Delete"), tint = MaterialTheme.colorScheme.error) }
            }
            SecretRow("Client ID", app.clientId, onCopy = onCopyClientId, isSecret = false)
            SecretRow("Client Secret", app.clientSecret, onCopy = onCopyClientSecret, isSecret = true)
            Text(
                settingsText("权限：", "Scopes: ") +
                    (app.scopes?.joinToString(settingsListSeparator()) {
                        appScopeLabel(it, isEnglish)
                    } ?: "--"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SecretRow(
    label: String,
    value: String?,
    onCopy: () -> Unit,
    isSecret: Boolean
) {
    var visible by remember { mutableStateOf(false) }
    val displayValue = when {
        value == null -> "--"
        isSecret && !visible -> "••••••••••••"
        else -> value
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onCopy),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(displayValue, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        if (isSecret) {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if (visible) settingsText("隐藏", "Hide") else settingsText("显示", "Show"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, settingsText("复制", "Copy"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClientSettingsRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun ServerManagementRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsNavigationRow(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionHeader(
    title: String,
    description: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SettingsNavigationRow(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                action?.invoke()
                Icon(
                    if (!expanded) Icons.Default.ChevronRight
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) settingsText("收起", "Collapse") else settingsText("展开", "Expand"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsNavigationRow(
    headlineContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.let {
            it()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            headlineContent()
            supportingContent?.invoke()
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: (() -> Unit)?,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

@Composable
private fun ThemeColorGrid(
    selectedColor: Color?,
    onSelect: (Color) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePresetColors.chunked(7).forEach { rowColors ->
            Row(Modifier.fillMaxWidth()) {
                rowColors.forEach { color ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ColorSwatch(
                            color = color,
                            selected = color == selectedColor,
                            onClick = { onSelect(color) },
                            size = 32.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioOption(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun languageLabel(languageTag: String): String = when (languageTag) {
    LocalAppPreferences.LANGUAGE_CHINESE -> "简体中文"
    LocalAppPreferences.LANGUAGE_ENGLISH -> "English"
    else -> settingsText("跟随系统", "Follow system")
}

@Composable
private fun darkModeLabel(mode: String): String = when (mode) {
    "light" -> settingsText("浅色", "Light")
    "dark" -> settingsText("深色", "Dark")
    else -> settingsText("跟随系统", "System")
}

@Composable
private fun themeColorLabel(dynamicColor: Boolean): String = when {
    dynamicColor -> settingsText("跟随系统壁纸（动态取色）", "Use system wallpaper (dynamic color)")
    else -> settingsText("选择你的配色方案", "Choose your color scheme")
}

@Composable
private fun settingsText(chinese: String, english: String): String =
    if (LocalConfiguration.current.locales[0].language == "en") english else chinese

@Composable
private fun settingsListSeparator(): String =
    if (LocalConfiguration.current.locales[0].language == "en") ", " else "、"

private fun createQrCode(content: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val rowOffset = y * size
        for (x in 0 until size) {
            pixels[rowOffset + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return android.graphics.Bitmap.createBitmap(
        pixels,
        size,
        size,
        android.graphics.Bitmap.Config.ARGB_8888
    ).asImageBitmap()
}

@Composable
private fun appScopeLabel(scope: String): String = appScopeLabel(
    scope = scope,
    isEnglish = LocalConfiguration.current.locales[0].language == "en"
)

private fun appScopeLabel(scope: String, isEnglish: Boolean): String = when (scope) {
    AppScopes.ENVS -> if (isEnglish) "Environment variables" else "环境变量"
    AppScopes.CRONS -> if (isEnglish) "Scheduled tasks" else "定时任务"
    AppScopes.CONFIGS -> if (isEnglish) "Configuration files" else "配置文件"
    AppScopes.SCRIPTS -> if (isEnglish) "Scripts" else "脚本管理"
    AppScopes.LOGS -> if (isEnglish) "Task logs" else "任务日志"
    AppScopes.SYSTEM -> if (isEnglish) "System" else "系统管理"
    AppScopes.DASHBOARD -> if (isEnglish) "Dashboard" else "仪表盘"
    AppScopes.SUBSCRIPTIONS -> if (isEnglish) "Subscriptions" else "订阅管理"
    AppScopes.DEPENDENCIES -> if (isEnglish) "Dependencies" else "依赖管理"
    else -> scope
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
