package com.autopanel.feature.login

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.ui.theme.AutoPanelTheme
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val host by viewModel.host.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val alias by viewModel.alias.collectAsStateWithLifecycle()
    val rememberPassword by viewModel.rememberPassword.collectAsStateWithLifecycle()
    val useClientIdMode by viewModel.useClientIdMode.collectAsStateWithLifecycle()
    val clientId by viewModel.clientId.collectAsStateWithLifecycle()
    val clientSecret by viewModel.clientSecret.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val certFileName by viewModel.certFileName.collectAsStateWithLifecycle()
    val certPassword by viewModel.certPassword.collectAsStateWithLifecycle()
    val customCaFileName by viewModel.customCaFileName.collectAsStateWithLifecycle()
    val allowInsecureHttp by viewModel.allowInsecureHttp.collectAsStateWithLifecycle()
    val isImportingCertificate by viewModel.isImportingCertificate.collectAsStateWithLifecycle()
    val isImportingCustomCa by viewModel.isImportingCustomCa.collectAsStateWithLifecycle()
    val isSessionInitialized by viewModel.isSessionInitialized.collectAsStateWithLifecycle()
    val twoFactorCode by viewModel.twoFactorCode.collectAsStateWithLifecycle()
    val twoFactorError by viewModel.twoFactorError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val englishUi = isEnglishUi()

    val certLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.saveCertificate(it) }
    }

    val customCaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.saveCustomCa(it) }
    }

    val isLoginLoading = uiState is LoginUiState.Loading || !isSessionInitialized

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoginSuccess()
    }

    LaunchedEffect(uiState, englishUi) {
        val error = uiState as? LoginUiState.Error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(localizedMessage(error.message, englishUi))
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AzureQL", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (uiState is LoginUiState.NeedTwoFactor) {
            TwoFactorScreen(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                code = twoFactorCode,
                error = twoFactorError?.let { localizedMessage(it, englishUi) },
                isLoading = uiState is LoginUiState.Loading,
                onCodeChanged = viewModel::onTwoFactorCodeChanged,
                onSubmitClick = { focusManager.clearFocus(); viewModel.submitTwoFactor() },
                onBackClick = viewModel::backToPasswordLogin
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "AzureQL",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = localizedText("账号登录", "Sign in"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !useClientIdMode,
                        onClick = { viewModel.onUseClientIdModeChanged(false) },
                        label = { Text(localizedText("密码登录", "Password")) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = useClientIdMode,
                        onClick = { viewModel.onUseClientIdModeChanged(true) },
                        label = { Text("Client ID") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (accounts.isNotEmpty()) {
                    AccountHistoryDropdown(accounts = accounts, onSelect = viewModel::selectAccount)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (useClientIdMode) {
                    ClientIdLoginForm(
                            host = host, clientId = clientId, clientSecret = clientSecret,
                            alias = alias, rememberPassword = rememberPassword,
                            isLoading = isLoginLoading,
                            allowInsecureHttp = allowInsecureHttp,
                            onHostChanged = viewModel::onHostChanged,
                            onClientIdChanged = viewModel::onClientIdChanged,
                            onClientSecretChanged = viewModel::onClientSecretChanged,
                            onAliasChanged = viewModel::onAliasChanged,
                            onRememberPasswordChanged = viewModel::onRememberPasswordChanged,
                            onAllowInsecureHttpChanged = viewModel::onAllowInsecureHttpChanged,
                            onLoginClick = { focusManager.clearFocus(); viewModel.login() },
                            canLogin = viewModel.canLogin()
                        )
                    } else {
                        PasswordLoginForm(
                            host = host, username = username, password = password,
                            alias = alias, rememberPassword = rememberPassword,
                            isLoading = isLoginLoading,
                            allowInsecureHttp = allowInsecureHttp,
                            onHostChanged = viewModel::onHostChanged,
                            onUsernameChanged = viewModel::onUsernameChanged,
                            onPasswordChanged = viewModel::onPasswordChanged,
                            onAliasChanged = viewModel::onAliasChanged,
                            onRememberPasswordChanged = viewModel::onRememberPasswordChanged,
                            onAllowInsecureHttpChanged = viewModel::onAllowInsecureHttpChanged,
                            onLoginClick = { focusManager.clearFocus(); viewModel.login() },
                            canLogin = viewModel.canLogin()
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))
                CertConfigSection(
                    certFileName = certFileName,
                    certPassword = certPassword,
                    customCaFileName = customCaFileName,
                    isImportingCertificate = isImportingCertificate,
                    isImportingCustomCa = isImportingCustomCa,
                    onSelectCertificate = {
                        certLauncher.launch(
                            arrayOf("application/x-pkcs12", "application/x-pfx", "application/octet-stream")
                        )
                    },
                    onPasswordChanged = viewModel::onCertPasswordChanged,
                    onClear = viewModel::clearCertificate,
                    onSelectCustomCa = {
                        customCaLauncher.launch(
                            arrayOf(
                                "application/x-x509-ca-cert",
                                "application/pkix-cert",
                                "application/octet-stream",
                                "text/plain"
                            )
                        )
                    },
                    onClearCustomCa = viewModel::clearCustomCa
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "AzureQL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CertConfigSection(
    certFileName: String,
    certPassword: String,
    customCaFileName: String,
    isImportingCertificate: Boolean,
    isImportingCustomCa: Boolean,
    onSelectCertificate: () -> Unit,
    onPasswordChanged: (String) -> Unit,
    onClear: () -> Unit,
    onSelectCustomCa: () -> Unit,
    onClearCustomCa: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onSelectCertificate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isImportingCertificate
        ) {
            if (isImportingCertificate) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (certFileName.isEmpty()) {
                    localizedText("mTLS 证书（.p12/.pfx，可选）", "mTLS certificate (.p12/.pfx, optional)")
                } else certFileName
            )
        }

        if (certFileName.isNotEmpty() || certPassword.isNotEmpty()) {
            OutlinedTextField(
                value = certPassword, onValueChange = onPasswordChanged,
                label = { Text(localizedText("证书密码", "Certificate password")) },
                placeholder = { Text(localizedText("请输入证书密码", "Enter certificate password")) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(localizedText("清除证书", "Clear certificate"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedButton(
            onClick = onSelectCustomCa,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isImportingCustomCa
        ) {
            if (isImportingCustomCa) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (customCaFileName.isEmpty()) {
                    localizedText("私有 CA（PEM/CRT，可选）", "Private CA (PEM/CRT, optional)")
                } else customCaFileName
            )
        }

        if (customCaFileName.isNotEmpty()) {
            Text(
                localizedText(
                    "私有 CA 只用于验证服务端；系统证书和域名校验仍然启用。",
                    "The private CA only verifies the server. System certificates and hostname verification remain enabled."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onClearCustomCa, modifier = Modifier.fillMaxWidth()) {
                Text(localizedText("清除私有 CA", "Clear private CA"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AccountHistoryDropdown(
    accounts: List<StoredAccount>,
    onSelect: (StoredAccount) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(localizedText("历史账户 (${accounts.size})", "Saved accounts (${accounts.size})"), style = MaterialTheme.typography.labelLarge)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(account.host, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(account.alias ?: account.username, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { expanded = false; onSelect(account) }
                )
            }
        }
    }
}

@Composable
private fun PasswordLoginForm(
    host: String, username: String, password: String, alias: String,
    rememberPassword: Boolean, isLoading: Boolean, allowInsecureHttp: Boolean,
    onHostChanged: (String) -> Unit, onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit, onAliasChanged: (String) -> Unit,
    onRememberPasswordChanged: (Boolean) -> Unit,
    onAllowInsecureHttpChanged: (Boolean) -> Unit,
    onLoginClick: () -> Unit, canLogin: Boolean
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = host, onValueChange = onHostChanged,
            label = { Text(localizedText("服务器地址", "Server address")) }, placeholder = { Text("http://1.1.1.1:5700") },
            leadingIcon = { Icon(Icons.Default.Cloud, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        InsecureHttpConsent(host, allowInsecureHttp, onAllowInsecureHttpChanged, isLoading)

        OutlinedTextField(
            value = username, onValueChange = onUsernameChanged,
            label = { Text(localizedText("用户名", "Username")) },
            placeholder = { Text(localizedText("请输入用户名", "Enter username")) },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = password, onValueChange = onPasswordChanged,
            label = { Text(localizedText("密码", "Password")) },
            placeholder = { Text(localizedText("请输入密码", "Enter password")) },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) {
                            localizedText("隐藏密码", "Hide password")
                        } else {
                            localizedText("显示密码", "Show password")
                        }
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = alias, onValueChange = onAliasChanged,
            label = { Text(localizedText("别名（选填）", "Alias (optional)")) },
            placeholder = { Text(localizedText("仅用于展示", "Display only")) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
            enabled = !isLoading
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberPassword, onRememberPasswordChanged, enabled = !isLoading)
            Text(localizedText("记住密码", "Remember password"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = canLogin && !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(localizedText("登 录", "Sign in"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ClientIdLoginForm(
    host: String, clientId: String, clientSecret: String, alias: String,
    rememberPassword: Boolean, isLoading: Boolean, allowInsecureHttp: Boolean,
    onHostChanged: (String) -> Unit, onClientIdChanged: (String) -> Unit,
    onClientSecretChanged: (String) -> Unit, onAliasChanged: (String) -> Unit,
    onRememberPasswordChanged: (Boolean) -> Unit,
    onAllowInsecureHttpChanged: (Boolean) -> Unit,
    onLoginClick: () -> Unit, canLogin: Boolean
) {
    var secretVisible by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = host, onValueChange = onHostChanged,
            label = { Text(localizedText("服务器地址", "Server address")) }, placeholder = { Text("http://1.1.1.1:5700") },
            leadingIcon = { Icon(Icons.Default.Cloud, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        InsecureHttpConsent(host, allowInsecureHttp, onAllowInsecureHttpChanged, isLoading)

        OutlinedTextField(
            value = clientId, onValueChange = onClientIdChanged,
            label = { Text("Client ID") }, placeholder = { Text(localizedText("请输入 Client ID", "Enter Client ID")) },
            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = clientSecret, onValueChange = onClientSecretChanged,
            label = { Text("Client Secret") }, placeholder = { Text(localizedText("请输入 Client Secret", "Enter Client Secret")) },
            leadingIcon = { Icon(Icons.Default.Key, null) },
            trailingIcon = {
                IconButton(onClick = { secretVisible = !secretVisible }) {
                    Icon(
                        if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (secretVisible) localizedText("隐藏", "Hide") else localizedText("显示", "Show")
                    )
                }
            },
            visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = alias, onValueChange = onAliasChanged,
            label = { Text(localizedText("别名（选填）", "Alias (optional)")) },
            placeholder = { Text(localizedText("仅用于展示", "Display only")) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
            enabled = !isLoading
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberPassword, onRememberPasswordChanged, enabled = !isLoading)
            Text(localizedText("记住凭证", "Remember credentials"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = canLogin && !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(localizedText("登 录", "Sign in"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun InsecureHttpConsent(
    host: String,
    allowed: Boolean,
    onAllowedChanged: (Boolean) -> Unit,
    disabled: Boolean
) {
    if (!host.trim().startsWith("http://", ignoreCase = true)) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = allowed,
            onCheckedChange = onAllowedChanged,
            enabled = !disabled
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(localizedText("允许不安全 HTTP", "Allow insecure HTTP"), style = MaterialTheme.typography.bodyMedium)
            Text(
                localizedText(
                    "仅限可信局域网；密码、Token 和备份内容可能被窃听或篡改。",
                    "Use only on a trusted LAN. Passwords, tokens, and backups may be intercepted or modified."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TwoFactorScreen(
    modifier: Modifier = Modifier,
    code: String, error: String?, isLoading: Boolean,
    onCodeChanged: (String) -> Unit, onSubmitClick: () -> Unit, onBackClick: () -> Unit
) {
    Column(
        modifier = modifier.imePadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(localizedText("返回登录", "Back to sign in"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(32.dp))

        Text(localizedText("两步验证", "Two-factor authentication"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(8.dp))

        Text(
            localizedText("请输入验证码", "Enter the verification code"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onCodeChanged(it) },
            label = { Text(localizedText("验证码", "Verification code")) },
            placeholder = {
                Text(
                    localizedText("请输入 6 位数字验证码", "Enter the 6-digit code"),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            leadingIcon = { Icon(Icons.Default.Security, null) },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmitClick() }),
            enabled = !isLoading,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp)
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSubmitClick, modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = code.length >= 6 && !isLoading, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(localizedText("验 证", "Verify"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPassword() {
    AutoPanelTheme {
        PasswordLoginForm(
            host = "",
            username = "",
            password = "",
            alias = "",
            rememberPassword = false,
            isLoading = false,
            allowInsecureHttp = false,
            onHostChanged = {},
            onUsernameChanged = {},
            onPasswordChanged = {},
            onAliasChanged = {},
            onRememberPasswordChanged = {},
            onAllowInsecureHttpChanged = {},
            onLoginClick = {},
            canLogin = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewClientId() {
    AutoPanelTheme {
        ClientIdLoginForm(
            host = "",
            clientId = "",
            clientSecret = "",
            alias = "",
            rememberPassword = false,
            isLoading = false,
            allowInsecureHttp = false,
            onHostChanged = {},
            onClientIdChanged = {},
            onClientSecretChanged = {},
            onAliasChanged = {},
            onRememberPasswordChanged = {},
            onAllowInsecureHttpChanged = {},
            onLoginClick = {},
            canLogin = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewTwoFactor() {
    AutoPanelTheme { TwoFactorScreen(code = "123", error = null, isLoading = false, onCodeChanged = {}, onSubmitClick = {}, onBackClick = {}) }
}
