package com.autopanel.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.data.session.AuthMode
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage
import java.net.URI
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountManagementScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onAccountActivated: () -> Unit,
    onSignInRequired: () -> Unit,
    viewModel: AccountManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val english by rememberUpdatedState(isEnglishUi())
    val clientCertificateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::replaceClientCertificate)
    }
    val customCaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::replaceCustomCa)
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                AccountManagementEvent.AccountActivated -> onAccountActivated()
                AccountManagementEvent.SignInRequired -> onSignInRequired()
                is AccountManagementEvent.Message -> snackbar.showSnackbar(
                    localizedMessage(event.text, english)
                )
            }
        }
    }

    AccountManagementContent(
        state = state,
        snackbarHostState = snackbar,
        onBack = onBack,
        onAddAccount = onAddAccount,
        onSwitch = viewModel::switchAccount,
        onEdit = viewModel::editAccount,
        onMove = viewModel::moveAccount,
        onMoveToTop = viewModel::moveAccountToTop,
        onDelete = viewModel::requestDelete,
        onUpdateEdit = viewModel::updateEdit,
        onSelectClientCertificate = {
            clientCertificateLauncher.launch(
                arrayOf("application/x-pkcs12", "application/x-pfx", "application/octet-stream")
            )
        },
        onClearClientCertificate = viewModel::clearClientCertificate,
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
        onClearCustomCa = viewModel::clearCustomCa,
        onSaveEdit = viewModel::saveEdit,
        onDismissEdit = viewModel::dismissEdit,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onTwoFactorCodeChanged = viewModel::onTwoFactorCodeChanged,
        onSubmitTwoFactor = viewModel::submitTwoFactor,
        onDismissTwoFactor = viewModel::dismissTwoFactor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountManagementContent(
    state: AccountManagementUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitch: (String) -> Unit,
    onEdit: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onMoveToTop: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateEdit: ((AccountEditUi) -> AccountEditUi) -> Unit,
    onSelectClientCertificate: () -> Unit,
    onClearClientCertificate: () -> Unit,
    onSelectCustomCa: () -> Unit,
    onClearCustomCa: () -> Unit,
    onSaveEdit: () -> Unit,
    onDismissEdit: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onTwoFactorCodeChanged: (String) -> Unit,
    onSubmitTwoFactor: () -> Unit,
    onDismissTwoFactor: () -> Unit
) {
    val english = LocalConfiguration.current.locales[0].language == "en"
    fun text(zh: String, en: String) = if (english) en else zh

    state.editing?.let { edit ->
        AlertDialog(
            onDismissRequest = onDismissEdit,
            title = { Text(text("编辑账户", "Edit account")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = edit.alias,
                        onValueChange = { value -> onUpdateEdit { it.copy(alias = value) } },
                        label = { Text(text("别名", "Alias")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = edit.host,
                        onValueChange = { value -> onUpdateEdit { it.copy(host = value) } },
                        label = { Text(text("服务器地址", "Server address")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = edit.username,
                        onValueChange = { value -> onUpdateEdit { it.copy(username = value) } },
                        label = {
                            Text(
                                if (edit.authMode == AuthMode.CLIENT_CREDENTIALS) {
                                    "Client ID"
                                } else {
                                    text("用户名", "Username")
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text("允许不安全 HTTP", "Allow insecure HTTP"))
                            Text(
                                text("仅应在可信网络中启用", "Use only on a trusted network"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = edit.allowInsecureHttp,
                            onCheckedChange = { checked ->
                                onUpdateEdit { it.copy(allowInsecureHttp = checked) }
                            }
                        )
                    }
                    Text(
                        buildString {
                            append(text("TLS：", "TLS: "))
                            append(
                                when {
                                    edit.hasClientCertificate && edit.hasCustomCa ->
                                        text("客户端证书 + 私有 CA", "Client certificate + private CA")
                                    edit.hasClientCertificate -> text("客户端证书", "Client certificate")
                                    edit.hasCustomCa -> text("私有 CA", "Private CA")
                                    else -> text("系统信任链", "System trust store")
                                }
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onSelectClientCertificate,
                        enabled = !edit.isImportingCertificate && !edit.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (edit.isImportingCertificate) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Key, contentDescription = null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (edit.hasClientCertificate) {
                                text("替换客户端证书", "Replace client certificate")
                            } else {
                                text("添加客户端证书", "Add client certificate")
                            }
                        )
                    }
                    if (edit.isClientCertificateStaged) {
                        OutlinedTextField(
                            value = edit.certificatePassword,
                            onValueChange = { value ->
                                onUpdateEdit { it.copy(certificatePassword = value) }
                            },
                            label = { Text(text("新证书密码", "New certificate password")) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !edit.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (edit.hasClientCertificate) {
                        TextButton(
                            onClick = onClearClientCertificate,
                            enabled = !edit.isImportingCertificate && !edit.isSaving
                        ) {
                            Text(
                                text("移除客户端证书", "Remove client certificate"),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSelectCustomCa,
                        enabled = !edit.isImportingCustomCa && !edit.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (edit.isImportingCustomCa) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (edit.hasCustomCa) {
                                text("替换私有 CA", "Replace private CA")
                            } else {
                                text("添加私有 CA", "Add private CA")
                            }
                        )
                    }
                    if (edit.hasCustomCa) {
                        TextButton(
                            onClick = onClearCustomCa,
                            enabled = !edit.isImportingCustomCa && !edit.isSaving
                        ) {
                            Text(
                                text("移除私有 CA", "Remove private CA"),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (edit.isClientCertificateChanged || edit.isCustomCaChanged) {
                        Text(
                            text(
                                "保存后将使用新的 TLS 配置重新认证；失败时自动恢复原证书。",
                                "Saving re-authenticates with the new TLS configuration. The previous certificate is restored if it fails."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSaveEdit,
                    enabled = edit.host.isNotBlank() &&
                        edit.username.isNotBlank() &&
                        !edit.isImportingCertificate &&
                        !edit.isImportingCustomCa &&
                        !edit.isSaving
                ) {
                    if (edit.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text("保存", "Save"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEdit, enabled = !edit.isSaving) {
                    Text(text("取消", "Cancel"))
                }
            }
        )
    }

    state.deleteCandidateId?.let { accountId ->
        val account = state.accounts.firstOrNull { it.id == accountId }
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(text("删除账户", "Delete account")) },
            text = {
                Text(
                    if (account?.isCurrent == true) {
                        text(
                            "这是当前账户。删除前会先安全切换到列表中的下一个账户；没有其他账户时将返回登录页。",
                            "This is the active account. AzureQL will switch safely to the next saved account first, or return to sign in when none remains."
                        )
                    } else {
                        text(
                            "将删除该账户的已保存凭据。此操作不可撤销。",
                            "Saved credentials for this account will be removed. This cannot be undone."
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(text("删除", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text(text("取消", "Cancel")) }
            }
        )
    }

    state.twoFactorAccountId?.let {
        AlertDialog(
            onDismissRequest = onDismissTwoFactor,
            title = { Text(text("两步验证", "Two-factor authentication")) },
            text = {
                OutlinedTextField(
                    value = state.twoFactorCode,
                    onValueChange = onTwoFactorCodeChanged,
                    label = { Text(text("验证码", "Verification code")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onSubmitTwoFactor,
                    enabled = state.twoFactorCode.isNotBlank() && !state.isSubmittingTwoFactor
                ) {
                    if (state.isSubmittingTwoFactor) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text("验证并切换", "Verify and switch"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissTwoFactor) { Text(text("取消", "Cancel")) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text("账户与服务器", "Accounts and servers")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, text("返回", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = onAddAccount) {
                        Icon(Icons.Default.Add, text("新增账户", "Add account"))
                    }
                }
            )
        }
    ) { padding ->
        if (state.accounts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text("没有已保存账户", "No saved accounts"), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAddAccount) { Text(text("新增账户", "Add account")) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text(
                            "使用“切换”可直接重新认证其他账户；新增账户仍使用完整登录页。",
                            "Use Switch to re-authenticate another account. New accounts continue through the full sign-in screen."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Text(
                        text("已保存账户 (${state.accounts.size})", "Saved accounts (${state.accounts.size})"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                itemsIndexed(state.accounts, key = { _, item -> item.id }) { index, account ->
                    SavedAccountCard(
                        account = account,
                        index = index,
                        count = state.accounts.size,
                        switching = state.switchingAccountId == account.id,
                        modifier = Modifier.fillMaxWidth(),
                        text = ::text,
                        onSwitch = { onSwitch(account.id) },
                        onEdit = { onEdit(account.id) },
                        onMoveUp = { onMove(account.id, -1) },
                        onMoveDown = { onMove(account.id, 1) },
                        onMoveToTop = { onMoveToTop(account.id) },
                        onDelete = { onDelete(account.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedAccountCard(
    account: SavedAccountUi,
    index: Int,
    count: Int,
    switching: Boolean,
    modifier: Modifier = Modifier,
    text: (String, String) -> String,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (account.isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            1.dp,
            if (account.isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    Icons.Default.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (account.isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.alias?.takeIf(String::isNotBlank) ?: account.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        maskServerAddress(account.host),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                when {
                    switching -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    account.isCurrent -> AssistChip(
                        onClick = {},
                        label = { Text(text("当前", "Current")) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${if (account.authMode == AuthMode.CLIENT_CREDENTIALS) "Client ID" else text("用户名", "Username")}: ${account.username}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(
                        if (account.authMode == AuthMode.CLIENT_CREDENTIALS) {
                            "Client credentials"
                        } else {
                            text("密码认证", "Password")
                        }
                    )
                    append(" · ")
                    append(
                        when {
                            account.hasClientCertificate -> "mTLS"
                            account.hasCustomCa -> text("私有 CA", "Private CA")
                            account.allowInsecureHttp -> "HTTP"
                            else -> "TLS"
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (account.lastUsedAtEpochMs > 0L) {
                Text(
                    text("最近使用：", "Last used: ") +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(account.lastUsedAtEpochMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (!account.isCurrent) {
                    FilledTonalButton(onClick = onSwitch, enabled = !switching) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text("切换", "Switch"))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit, enabled = !switching) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text("编辑", "Edit"))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = !switching) {
                        Icon(Icons.Default.MoreVert, text("更多操作", "More actions"))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text("置顶", "Pin to top")) },
                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                            enabled = index > 0,
                            onClick = {
                                menuExpanded = false
                                onMoveToTop()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text("上移", "Move up")) },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                            enabled = index > 0,
                            onClick = {
                                menuExpanded = false
                                onMoveUp()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text("下移", "Move down")) },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                            enabled = index < count - 1,
                            onClick = {
                                menuExpanded = false
                                onMoveDown()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text("删除账户", "Delete account"),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun maskServerAddress(raw: String): String = runCatching {
    val uri = URI(raw.trim())
    val host = uri.host ?: return@runCatching raw.take(12) + "…"
    val maskedHost = if (host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) {
        host.split('.').let { "${it[0]}.${it[1]}.*.*" }
    } else {
        val labels = host.split('.')
        if (labels.size >= 2) {
            labels.first().take(1) + "***." + labels.drop(1).joinToString(".")
        } else {
            host.take(1) + "***"
        }
    }
    buildString {
        append(uri.scheme)
        append("://")
        append(maskedHost)
        if (uri.port >= 0) append(":${uri.port}")
    }
}.getOrElse { raw.take(12) + if (raw.length > 12) "…" else "" }
