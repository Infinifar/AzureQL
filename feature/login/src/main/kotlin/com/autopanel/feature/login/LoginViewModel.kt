package com.autopanel.feature.login

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.domain.LoginTwoFactorUseCase
import com.autopanel.core.domain.LoginUseCase
import com.autopanel.core.domain.SaveCredentialsUseCase
import com.autopanel.core.model.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val loginTwoFactorUseCase: LoginTwoFactorUseCase,
    private val saveCredentialsUseCase: SaveCredentialsUseCase,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _host = MutableStateFlow(sessionManager.host ?: "")
    val host = _host.asStateFlow()

    private val _username = MutableStateFlow(sessionManager.username ?: "")
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow(sessionManager.password ?: "")
    val password = _password.asStateFlow()

    private val _alias = MutableStateFlow(sessionManager.alias ?: "")
    val alias = _alias.asStateFlow()

    private val _rememberPassword = MutableStateFlow(sessionManager.rememberPassword)
    val rememberPassword = _rememberPassword.asStateFlow()

    private val _useClientIdMode = MutableStateFlow(false)
    val useClientIdMode = _useClientIdMode.asStateFlow()

    private val _clientId = MutableStateFlow("")
    val clientId = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow("")
    val clientSecret = _clientSecret.asStateFlow()

    private val _twoFactorCode = MutableStateFlow("")
    val twoFactorCode = _twoFactorCode.asStateFlow()

    private val _twoFactorError = MutableStateFlow<String?>(null)
    val twoFactorError = _twoFactorError.asStateFlow()

    // mTLS 证书
    private val _certPath = MutableStateFlow(sessionManager.certPath)
    val certPath = _certPath.asStateFlow()

    private val _certPassword = MutableStateFlow(sessionManager.certPassword ?: "")
    val certPassword = _certPassword.asStateFlow()

    private val _certFileName = MutableStateFlow(if (sessionManager.certPath != null) "已配置证书" else "")
    val certFileName = _certFileName.asStateFlow()

    val accounts: StateFlow<List<StoredAccount>> = sessionManager.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onHostChanged(value: String) { _host.value = value }
    fun onUsernameChanged(value: String) { _username.value = value }
    fun onPasswordChanged(value: String) { _password.value = value }
    fun onAliasChanged(value: String) { _alias.value = value }
    fun onRememberPasswordChanged(value: Boolean) { _rememberPassword.value = value }
    fun onUseClientIdModeChanged(value: Boolean) { _useClientIdMode.value = value }
    fun onClientIdChanged(value: String) { _clientId.value = value }
    fun onClientSecretChanged(value: String) { _clientSecret.value = value }
    fun onTwoFactorCodeChanged(value: String) {
        _twoFactorCode.value = value
        _twoFactorError.value = null
    }
    fun onCertPasswordChanged(value: String) { _certPassword.value = value }

    fun selectAccount(account: StoredAccount) {
        _host.value = account.host
        _username.value = account.username
        _alias.value = account.alias ?: ""
        _password.value = ""
        _useClientIdMode.value = false
    }

    fun canLogin(): Boolean {
        if (_uiState.value is LoginUiState.Loading) return false
        if (_host.value.isBlank()) return false
        if (_useClientIdMode.value) {
            return _clientId.value.isNotBlank() && _clientSecret.value.isNotBlank()
        }
        return _username.value.isNotBlank() && _password.value.isNotBlank()
    }

    fun login() {
        val host = _host.value.trim()
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            _uiState.update { LoginUiState.Error("服务器地址必须以 http:// 或 https:// 开头") }
            return
        }

        _uiState.update { LoginUiState.Loading }

        viewModelScope.launch {
            sessionManager.setHost(host)
            if (_useClientIdMode.value) loginByClientId(host)
            else loginByPassword(host)
        }
    }

    private suspend fun loginByPassword(host: String) {
        when (val result = loginUseCase(_username.value, _password.value)) {
            is LoginResult.Success -> onLoginSuccess(host, result)
            is LoginResult.NeedTwoFactor -> _uiState.update {
                LoginUiState.NeedTwoFactor(_username.value, _password.value)
            }
            is LoginResult.Error -> _uiState.update { LoginUiState.Error(result.message) }
        }
    }

    private suspend fun loginByClientId(host: String) {
        _uiState.update { LoginUiState.Error("Client ID 登录模式开发中") }
    }

    fun submitTwoFactor() {
        val code = _twoFactorCode.value.trim()
        if (code.isEmpty()) {
            _twoFactorError.value = "请输入验证码"
            return
        }
        val state = _uiState.value as? LoginUiState.NeedTwoFactor ?: return

        _uiState.update { LoginUiState.Loading }

        viewModelScope.launch {
            when (val result = loginTwoFactorUseCase(state.username, state.password, code)) {
                is LoginResult.Success -> onLoginSuccess(_host.value.trim(), result)
                is LoginResult.NeedTwoFactor -> {
                    _twoFactorError.value = "仍需验证"
                    _uiState.update { LoginUiState.NeedTwoFactor(state.username, state.password) }
                }
                is LoginResult.Error -> {
                    _twoFactorError.value = result.message
                    _uiState.update { LoginUiState.NeedTwoFactor(state.username, state.password) }
                }
            }
        }
    }

    private suspend fun onLoginSuccess(host: String, result: LoginResult.Success) {
        saveCredentialsUseCase(
            host = host,
            username = if (_useClientIdMode.value) _clientId.value else _username.value,
            password = if (_useClientIdMode.value) _clientSecret.value else _password.value,
            token = result.data.token ?: "",
            alias = _alias.value.ifBlank { null },
            remember = _rememberPassword.value
        )
        _uiState.update { LoginUiState.Success }
    }

    fun backToPasswordLogin() {
        _twoFactorCode.value = ""
        _twoFactorError.value = null
        _uiState.update { LoginUiState.Idle }
    }

    fun clearError() {
        _uiState.update { LoginUiState.Idle }
    }

    // ── mTLS 证书 ──

    fun saveCertificate(uri: Uri) {
        val password = _certPassword.value
        viewModelScope.launch {
            try {
                // 文件复制属于 IO 操作，放到 IO 线程避免主线程卡顿
                val path = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "cert")
                    dir.mkdirs()
                    val file = File(dir, "client_cert.p12")
                    val input = context.contentResolver.openInputStream(uri)
                    if (input == null) return@withContext null
                    input.use { ins -> file.outputStream().use { outs -> ins.copyTo(outs) } }
                    file.absolutePath
                }
                if (path == null) {
                    _uiState.update { LoginUiState.Error("无法读取证书文件") }
                } else {
                    sessionManager.saveCertificate(path, password)
                    _certPath.value = path
                    _certFileName.value = "已配置证书"
                    _uiState.update { LoginUiState.Idle }
                }
            } catch (e: Exception) {
                _uiState.update { LoginUiState.Error("证书保存失败: ${e.message}") }
            }
        }
    }

    fun clearCertificate() {
        viewModelScope.launch {
            sessionManager.saveCertificate(null, null)
            _certPath.value = null
            _certPassword.value = ""
            _certFileName.value = ""
        }
    }
}
