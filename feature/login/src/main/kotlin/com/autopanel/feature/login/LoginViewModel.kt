package com.autopanel.feature.login

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.data.session.AuthMode
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.domain.LoginClientCredentialsUseCase
import com.autopanel.core.domain.LoginTwoFactorUseCase
import com.autopanel.core.domain.LoginUseCase
import com.autopanel.core.domain.SaveCredentialsUseCase
import com.autopanel.core.model.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val loginClientCredentialsUseCase: LoginClientCredentialsUseCase,
    private val saveCredentialsUseCase: SaveCredentialsUseCase,
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _host = MutableStateFlow("")
    val host = _host.asStateFlow()

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _alias = MutableStateFlow("")
    val alias = _alias.asStateFlow()

    private val _rememberPassword = MutableStateFlow(false)
    val rememberPassword = _rememberPassword.asStateFlow()

    private val _useClientIdMode = MutableStateFlow(false)
    val useClientIdMode = _useClientIdMode.asStateFlow()

    private val _clientId = MutableStateFlow("")
    val clientId = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow("")
    val clientSecret = _clientSecret.asStateFlow()

    private val _allowInsecureHttp = MutableStateFlow(false)
    val allowInsecureHttp = _allowInsecureHttp.asStateFlow()

    private val _twoFactorCode = MutableStateFlow("")
    val twoFactorCode = _twoFactorCode.asStateFlow()

    private val _twoFactorError = MutableStateFlow<String?>(null)
    val twoFactorError = _twoFactorError.asStateFlow()

    private val _certPath = MutableStateFlow<String?>(null)
    val certPath = _certPath.asStateFlow()

    private val _certPassword = MutableStateFlow("")
    val certPassword = _certPassword.asStateFlow()

    private val _certFileName = MutableStateFlow("")
    val certFileName = _certFileName.asStateFlow()

    private val _customCaPath = MutableStateFlow<String?>(null)
    val customCaPath = _customCaPath.asStateFlow()

    private val _customCaFileName = MutableStateFlow("")
    val customCaFileName = _customCaFileName.asStateFlow()

    private val _isImportingCertificate = MutableStateFlow(false)
    val isImportingCertificate = _isImportingCertificate.asStateFlow()

    private val _isImportingCustomCa = MutableStateFlow(false)
    val isImportingCustomCa = _isImportingCustomCa.asStateFlow()

    private val _isSessionInitialized = MutableStateFlow(false)
    val isSessionInitialized = _isSessionInitialized.asStateFlow()

    private var formTouched = false
    private var certificateImportJob: Job? = null
    private var customCaImportJob: Job? = null

    val accounts: StateFlow<List<StoredAccount>> = sessionManager.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            try {
                val session = sessionManager.getSession()
                if (!formTouched) {
                    _host.value = session.host.orEmpty()
                    _alias.value = session.alias.orEmpty()
                    _rememberPassword.value = session.rememberPassword
                    _allowInsecureHttp.value = session.allowInsecureHttp
                    _certPath.value = session.certPath
                    _certPassword.value = session.certPassword.orEmpty()
                    _certFileName.value = if (session.certPath == null) "" else "已配置客户端证书"
                    _customCaPath.value = session.customCaPath
                    _customCaFileName.value = if (session.customCaPath == null) "" else "已配置私有 CA"
                    _useClientIdMode.value = session.authMode == AuthMode.CLIENT_CREDENTIALS
                    if (_useClientIdMode.value) {
                        _clientId.value = session.username.orEmpty()
                        _clientSecret.value = session.password.orEmpty()
                    } else {
                        _username.value = session.username.orEmpty()
                        _password.value = session.password.orEmpty()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = LoginUiState.Error("无法读取安全登录信息，请重新输入")
            } finally {
                _isSessionInitialized.value = true
            }
        }
    }

    fun onHostChanged(value: String) {
        formTouched = true
        _host.value = value
        if (!value.trim().startsWith("http://", ignoreCase = true)) {
            _allowInsecureHttp.value = false
        }
    }

    fun onUsernameChanged(value: String) { formTouched = true; _username.value = value }
    fun onPasswordChanged(value: String) { formTouched = true; _password.value = value }
    fun onAliasChanged(value: String) { formTouched = true; _alias.value = value }
    fun onRememberPasswordChanged(value: Boolean) { formTouched = true; _rememberPassword.value = value }
    fun onAllowInsecureHttpChanged(value: Boolean) { formTouched = true; _allowInsecureHttp.value = value }
    fun onUseClientIdModeChanged(value: Boolean) { formTouched = true; _useClientIdMode.value = value }
    fun onClientIdChanged(value: String) { formTouched = true; _clientId.value = value }
    fun onClientSecretChanged(value: String) { formTouched = true; _clientSecret.value = value }
    fun onTwoFactorCodeChanged(value: String) {
        _twoFactorCode.value = value
        _twoFactorError.value = null
    }
    fun onCertPasswordChanged(value: String) { formTouched = true; _certPassword.value = value }

    fun selectAccount(account: StoredAccount) {
        formTouched = true
        _host.value = account.host
        _alias.value = account.alias.orEmpty()
        _allowInsecureHttp.value = account.allowInsecureHttp
        _useClientIdMode.value = account.authMode == AuthMode.CLIENT_CREDENTIALS
        _password.value = ""
        _clientSecret.value = ""
        if (_useClientIdMode.value) {
            _clientId.value = account.username
        } else {
            _username.value = account.username
        }
    }

    fun canLogin(): Boolean {
        if (!_isSessionInitialized.value) return false
        if (_uiState.value is LoginUiState.Loading) return false
        if (_isImportingCertificate.value || _isImportingCustomCa.value) return false
        val host = _host.value.trim()
        if (host.isBlank()) return false
        if (host.startsWith("http://", ignoreCase = true) && !_allowInsecureHttp.value) return false
        return if (_useClientIdMode.value) {
            _clientId.value.isNotBlank() && _clientSecret.value.isNotBlank()
        } else {
            _username.value.isNotBlank() && _password.value.isNotBlank()
        }
    }

    fun login() {
        val host = _host.value.trim().trimEnd('/')
        if (!host.startsWith("http://", true) && !host.startsWith("https://", true)) {
            _uiState.value = LoginUiState.Error("服务器地址必须以 http:// 或 https:// 开头")
            return
        }
        if (host.startsWith("http://", true) && !_allowInsecureHttp.value) {
            _uiState.value = LoginUiState.Error("HTTP 会明文传输凭据，请先明确允许不安全 HTTP")
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                certificateImportJob?.join()
                customCaImportJob?.join()
                sessionManager.configureConnection(
                    host = host,
                    certPath = _certPath.value,
                    certPassword = _certPassword.value.takeIf { _certPath.value != null },
                    customCaPath = _customCaPath.value,
                    allowInsecureHttp = _allowInsecureHttp.value
                )
                if (_useClientIdMode.value) loginByClientId(host) else loginByPassword(host)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = LoginUiState.Error(e.message ?: "登录配置失败")
            }
        }
    }

    private suspend fun loginByPassword(host: String) {
        when (val result = loginUseCase(_username.value, _password.value)) {
            is LoginResult.Success -> onLoginSuccess(host, result)
            is LoginResult.NeedTwoFactor -> {
                _uiState.value = LoginUiState.NeedTwoFactor(_username.value, _password.value)
            }
            is LoginResult.Error -> _uiState.value = LoginUiState.Error(result.message)
        }
    }

    private suspend fun loginByClientId(host: String) {
        when (val result = loginClientCredentialsUseCase(_clientId.value, _clientSecret.value)) {
            is LoginResult.Success -> onLoginSuccess(host, result)
            is LoginResult.NeedTwoFactor -> {
                _uiState.value = LoginUiState.Error("Client ID 登录不支持两步验证")
            }
            is LoginResult.Error -> _uiState.value = LoginUiState.Error(result.message)
        }
    }

    fun submitTwoFactor() {
        val code = _twoFactorCode.value.trim()
        if (code.isEmpty()) {
            _twoFactorError.value = "请输入验证码"
            return
        }
        val state = _uiState.value as? LoginUiState.NeedTwoFactor ?: return
        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            when (val result = loginTwoFactorUseCase(state.username, state.password, code)) {
                is LoginResult.Success -> onLoginSuccess(_host.value.trim().trimEnd('/'), result)
                is LoginResult.NeedTwoFactor -> {
                    _twoFactorError.value = "仍需验证"
                    _uiState.value = LoginUiState.NeedTwoFactor(state.username, state.password)
                }
                is LoginResult.Error -> {
                    _twoFactorError.value = result.message
                    _uiState.value = LoginUiState.NeedTwoFactor(state.username, state.password)
                }
            }
        }
    }

    private suspend fun onLoginSuccess(host: String, result: LoginResult.Success) {
        val isClientCredentials = _useClientIdMode.value
        saveCredentialsUseCase(
            host = host,
            username = if (isClientCredentials) _clientId.value else _username.value,
            password = if (isClientCredentials) _clientSecret.value else _password.value,
            token = result.data.token.orEmpty(),
            alias = _alias.value.ifBlank { null },
            remember = _rememberPassword.value,
            allowInsecureHttp = _allowInsecureHttp.value,
            isClientCredentials = isClientCredentials
        )
        _uiState.value = LoginUiState.Success
    }

    fun backToPasswordLogin() {
        _twoFactorCode.value = ""
        _twoFactorError.value = null
        _uiState.value = LoginUiState.Idle
    }

    fun clearError() {
        _uiState.update { if (it is LoginUiState.Error) LoginUiState.Idle else it }
    }

    fun saveCertificate(uri: Uri) {
        certificateImportJob?.cancel()
        certificateImportJob = viewModelScope.launch {
            _isImportingCertificate.value = true
            try {
                val path = copyPrivateFile(uri, "cert/client_identity.p12")
                    ?: throw IllegalArgumentException("无法读取证书文件")
                sessionManager.saveCertificate(path, _certPassword.value)
                _certPath.value = path
                _certFileName.value = "已配置客户端证书"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = LoginUiState.Error("证书保存失败，请检查文件权限和格式")
            } finally {
                _isImportingCertificate.value = false
            }
        }
    }

    fun clearCertificate() {
        certificateImportJob?.cancel()
        viewModelScope.launch {
            sessionManager.saveCertificate(null, null)
            _certPath.value = null
            _certPassword.value = ""
            _certFileName.value = ""
            deletePrivateFile("cert/client_identity.p12")
        }
    }

    fun saveCustomCa(uri: Uri) {
        customCaImportJob?.cancel()
        customCaImportJob = viewModelScope.launch {
            _isImportingCustomCa.value = true
            try {
                val path = copyPrivateFile(uri, "cert/server_ca.pem")
                    ?: throw IllegalArgumentException("无法读取 CA 文件")
                sessionManager.saveCustomCa(path)
                _customCaPath.value = path
                _customCaFileName.value = "已配置私有 CA"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = LoginUiState.Error("私有 CA 保存失败，请检查文件权限和格式")
            } finally {
                _isImportingCustomCa.value = false
            }
        }
    }

    fun clearCustomCa() {
        customCaImportJob?.cancel()
        viewModelScope.launch {
            sessionManager.saveCustomCa(null)
            _customCaPath.value = null
            _customCaFileName.value = ""
            deletePrivateFile("cert/server_ca.pem")
        }
    }

    private suspend fun copyPrivateFile(uri: Uri, relativePath: String): String? =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, relativePath)
            file.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source -> file.outputStream().use { output -> source.copyTo(output) } }
            file.absolutePath
        }

    private suspend fun deletePrivateFile(relativePath: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, relativePath).delete()
    }
}
