package com.qinglong.feature.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qinglong.core.data.session.StoredAccount
import com.qinglong.core.ui.theme.QingLongTheme

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

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoginSuccess()
    }

    LaunchedEffect(uiState) {
        val error = uiState as? LoginUiState.Error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error.message)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AutoPanel", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
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
                contentDescription = "AutoPanel",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "账号登录",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !useClientIdMode,
                    onClick = { viewModel.onUseClientIdModeChanged(false) },
                    label = { Text("密码登录") },
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

            AnimatedVisibility(
                visible = uiState !is LoginUiState.NeedTwoFactor,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (useClientIdMode) {
                    ClientIdLoginForm(
                        host = host, clientId = clientId, clientSecret = clientSecret,
                        alias = alias, rememberPassword = rememberPassword,
                        isLoading = uiState is LoginUiState.Loading,
                        onHostChanged = viewModel::onHostChanged,
                        onClientIdChanged = viewModel::onClientIdChanged,
                        onClientSecretChanged = viewModel::onClientSecretChanged,
                        onAliasChanged = viewModel::onAliasChanged,
                        onRememberPasswordChanged = viewModel::onRememberPasswordChanged,
                        onLoginClick = { focusManager.clearFocus(); viewModel.login() },
                        canLogin = viewModel.canLogin()
                    )
                } else {
                    PasswordLoginForm(
                        host = host, username = username, password = password,
                        alias = alias, rememberPassword = rememberPassword,
                        isLoading = uiState is LoginUiState.Loading,
                        onHostChanged = viewModel::onHostChanged,
                        onUsernameChanged = viewModel::onUsernameChanged,
                        onPasswordChanged = viewModel::onPasswordChanged,
                        onAliasChanged = viewModel::onAliasChanged,
                        onRememberPasswordChanged = viewModel::onRememberPasswordChanged,
                        onLoginClick = { focusManager.clearFocus(); viewModel.login() },
                        canLogin = viewModel.canLogin()
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState is LoginUiState.NeedTwoFactor,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                (uiState as? LoginUiState.NeedTwoFactor)?.let { state ->
                    TwoFactorForm(
                        code = viewModel.twoFactorCode.collectAsStateWithLifecycle().value,
                        error = viewModel.twoFactorError.collectAsStateWithLifecycle().value,
                        isLoading = uiState is LoginUiState.Loading,
                        onCodeChanged = viewModel::onTwoFactorCodeChanged,
                        onSubmitClick = { focusManager.clearFocus(); viewModel.submitTwoFactor() },
                        onBackClick = viewModel::backToPasswordLogin
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "AutoPanel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))
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
            Text("历史账户 (${accounts.size})", style = MaterialTheme.typography.labelLarge)
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
    rememberPassword: Boolean, isLoading: Boolean,
    onHostChanged: (String) -> Unit, onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit, onAliasChanged: (String) -> Unit,
    onRememberPasswordChanged: (Boolean) -> Unit,
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
            label = { Text("服务器地址") }, placeholder = { Text("http://1.1.1.1:5700") },
            leadingIcon = { Icon(Icons.Default.Cloud, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = username, onValueChange = onUsernameChanged,
            label = { Text("用户名") }, placeholder = { Text("请输入用户名") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = password, onValueChange = onPasswordChanged,
            label = { Text("密码") }, placeholder = { Text("请输入密码") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
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
            label = { Text("别名（选填）") }, placeholder = { Text("仅用于展示") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
            enabled = !isLoading
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberPassword, onRememberPasswordChanged, enabled = !isLoading)
            Text("记住密码", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            else Text("登 录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ClientIdLoginForm(
    host: String, clientId: String, clientSecret: String, alias: String,
    rememberPassword: Boolean, isLoading: Boolean,
    onHostChanged: (String) -> Unit, onClientIdChanged: (String) -> Unit,
    onClientSecretChanged: (String) -> Unit, onAliasChanged: (String) -> Unit,
    onRememberPasswordChanged: (Boolean) -> Unit,
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
            label = { Text("服务器地址") }, placeholder = { Text("http://1.1.1.1:5700") },
            leadingIcon = { Icon(Icons.Default.Cloud, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = clientId, onValueChange = onClientIdChanged,
            label = { Text("Client ID") }, placeholder = { Text("请输入 Client ID") },
            leadingIcon = { Icon(Icons.Default.VpnKey, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            enabled = !isLoading
        )

        OutlinedTextField(
            value = clientSecret, onValueChange = onClientSecretChanged,
            label = { Text("Client Secret") }, placeholder = { Text("请输入 Client Secret") },
            leadingIcon = { Icon(Icons.Default.Key, null) },
            trailingIcon = {
                IconButton(onClick = { secretVisible = !secretVisible }) {
                    Icon(
                        if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (secretVisible) "隐藏" else "显示"
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
            label = { Text("别名（选填）") }, placeholder = { Text("仅用于展示") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
            enabled = !isLoading
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberPassword, onRememberPasswordChanged, enabled = !isLoading)
            Text("记住凭证", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            else Text("登 录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun TwoFactorForm(
    code: String, error: String?, isLoading: Boolean,
    onCodeChanged: (String) -> Unit, onSubmitClick: () -> Unit, onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("返回登录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))
            ), contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Security, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }

        Text("两步验证", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("您的账户已开启两步验证，请输入验证码", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onCodeChanged(it) },
            label = { Text("验证码") }, placeholder = { Text("请输入6位验证码") },
            leadingIcon = { Icon(Icons.Default.Security, null) },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmitClick() }),
            enabled = !isLoading,
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp)
        )

        Button(
            onClick = onSubmitClick, modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = code.length >= 6 && !isLoading, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("验 证", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewPassword() {
    QingLongTheme { PasswordLoginForm("", "", "", "", false, false, {}, {}, {}, {}, {}, {}, false) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewClientId() {
    QingLongTheme { ClientIdLoginForm("", "", "", "", false, false, {}, {}, {}, {}, {}, {}, false) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewTwoFactor() {
    QingLongTheme { TwoFactorForm("123", null, false, {}, {}, {}) }
}
