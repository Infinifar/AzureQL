package com.autopanel.feature.settings

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.session.AuthMode
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.AppCreateRequest
import com.autopanel.core.model.AppInfo
import com.autopanel.core.model.AppUpdateRequest
import com.autopanel.core.ui.theme.colorToHex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepo: ConfigRepository,
    private val logRepo: LogRepository,
    private val apiProvider: Provider<AutoPanelApiService>,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val darkMode: StateFlow<String> = sessionManager.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val themeColor: StateFlow<String?> = sessionManager.themeColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dynamicColor: StateFlow<Boolean> = sessionManager.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDarkMode(mode: String) {
        viewModelScope.launch { sessionManager.setDarkMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setDynamicColor(enabled) }
    }

    fun setThemeColor(color: Color) {
        val hex = colorToHex(color)
        viewModelScope.launch { sessionManager.setThemeColor(hex) }
    }

    init {
        loadServerVersion()
    }

    fun loadSystemConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingConfig = true) }
            configRepo.getSystemConfig()
                .onSuccess { cfg ->
                    _uiState.update {
                        it.copy(
                            systemConfig = cfg,
                            isLoadingConfig = false,
                            hasLoadedConfig = true,
                            editLogFrequency = cfg.logRemoveFrequency?.toString() ?: "",
                            editConcurrency = cfg.cronConcurrency?.toString() ?: ""
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isLoadingConfig = false, hasLoadedConfig = true) }
                    showMessage(e.message ?: "获取系统配置失败")
                }
        }
    }

    fun loadServerVersion() {
        viewModelScope.launch {
            runCatching { api.getSystemInfo() }
                .onSuccess { res ->
                    if (res.code == 200) {
                        _uiState.update { it.copy(serverVersion = res.data?.version) }
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.w("Settings", "getSystemInfo 失败: ${e.message}")
                }
        }
    }

    fun toggleConfigExpanded() {
        val shouldLoad = !_uiState.value.configExpanded && !_uiState.value.hasLoadedConfig
        _uiState.update { it.copy(configExpanded = !it.configExpanded) }
        if (shouldLoad) loadSystemConfig()
    }

    fun onLogFrequencyChanged(v: String) { _uiState.update { it.copy(editLogFrequency = v) } }
    fun onConcurrencyChanged(v: String) { _uiState.update { it.copy(editConcurrency = v) } }

    fun saveSystemConfig() {
        val s = _uiState.value
        val cfg = s.systemConfig ?: return
        val newCfg = cfg.copy(
            logRemoveFrequency = s.editLogFrequency.toIntOrNull() ?: cfg.logRemoveFrequency,
            cronConcurrency = s.editConcurrency.toIntOrNull() ?: cfg.cronConcurrency
        )
        viewModelScope.launch {
            configRepo.updateSystemConfig(newCfg)
                .onSuccess {
                    _uiState.update { it.copy(systemConfig = newCfg) }
                    showMessage("配置已保存")
                    loadSystemConfig()
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    showMessage(e.message ?: "保存配置失败")
                }
        }
    }

    fun loadLoginLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLogs = true) }
            logRepo.getLoginLogs()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(loginLogs = list, isLoadingLogs = false, hasLoadedLogs = true)
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isLoadingLogs = false, hasLoadedLogs = true) }
                    showMessage(e.message ?: "获取登录日志失败")
                }
        }
    }

    fun toggleLogsExpanded() {
        val shouldLoad = !_uiState.value.logsExpanded && !_uiState.value.hasLoadedLogs
        _uiState.update { it.copy(logsExpanded = !it.logsExpanded) }
        if (shouldLoad) loadLoginLogs()
    }

    // ── 修改密码 ──

    fun showPasswordDialog() {
        _uiState.update {
            it.copy(showPasswordDialog = true, newPassword = "")
        }
    }

    fun dismissPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialog = false, newPassword = "") }
    }

    fun onAccountUsernameChanged(v: String) { _uiState.update { it.copy(accountUsername = v) } }
    fun onNewPasswordChanged(v: String) { _uiState.update { it.copy(newPassword = v) } }

    fun changePassword() {
        val s = _uiState.value
        if (s.accountUsername.isEmpty() || s.newPassword.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPassword = true) }
            try {
                val res = api.updateAccount(
                    mapOf("password" to s.newPassword, "username" to s.accountUsername)
                )
                if (res.code == 200) {
                    val session = sessionManager.getSession()
                    val host = session.host
                    val token = session.token
                    if (
                        session.authMode == AuthMode.PASSWORD &&
                        host != null &&
                        token != null
                    ) {
                        sessionManager.saveSession(
                            host = host,
                            username = s.accountUsername,
                            password = s.newPassword,
                            token = token,
                            alias = session.alias,
                            remember = session.rememberPassword,
                            allowInsecureHttp = session.allowInsecureHttp,
                            authMode = session.authMode
                        )
                    }
                    _uiState.update {
                        it.copy(
                            showPasswordDialog = false, isLoadingPassword = false
                        )
                    }
                    showMessage("密码已修改")
                } else {
                    _uiState.update { it.copy(isLoadingPassword = false) }
                    showMessage(res.message ?: "修改失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingPassword = false) }
                showMessage(e.message ?: "修改失败")
            }
        }
    }

    // ── 安全设置 / 两步验证 ──

    fun toggleSecurityExpanded() {
        val shouldLoad = !_uiState.value.securityExpanded &&
            !_uiState.value.hasLoadedSecurity
        _uiState.update { it.copy(securityExpanded = !it.securityExpanded) }
        if (shouldLoad) loadSecurity()
    }

    fun loadSecurity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSecurity = true) }
            try {
                val response = api.getUserInfo()
                val user = response.data
                if (response.code == 200 && user != null) {
                    _uiState.update {
                        it.copy(
                            accountUsername = user.username,
                            twoFactorActivated = user.twoFactorActivated,
                            isLoadingSecurity = false,
                            hasLoadedSecurity = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoadingSecurity = false, hasLoadedSecurity = true)
                    }
                    showMessage(response.message ?: "获取安全设置失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoadingSecurity = false, hasLoadedSecurity = true)
                }
                showMessage(e.message ?: "获取安全设置失败")
            }
        }
    }

    fun startTwoFactorSetup() {
        if (_uiState.value.isLoadingSecurity) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSecurity = true) }
            try {
                val response = api.initializeTwoFactor()
                val setup = response.data
                if (response.code == 200 && setup != null) {
                    _uiState.update {
                        it.copy(
                            isLoadingSecurity = false,
                            showTwoFactorSetup = true,
                            twoFactorSecret = setup.secret,
                            twoFactorUrl = setup.url,
                            twoFactorCode = ""
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingSecurity = false) }
                    showMessage(response.message ?: "初始化两步验证失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingSecurity = false) }
                showMessage(e.message ?: "初始化两步验证失败")
            }
        }
    }

    fun onTwoFactorCodeChanged(value: String) {
        if (value.length <= 8 && value.all { it.isDigit() }) {
            _uiState.update { it.copy(twoFactorCode = value) }
        }
    }

    fun dismissTwoFactorSetup() {
        _uiState.update {
            it.copy(
                showTwoFactorSetup = false,
                twoFactorSecret = "",
                twoFactorUrl = "",
                twoFactorCode = ""
            )
        }
    }

    fun activateTwoFactor() {
        val code = _uiState.value.twoFactorCode
        if (code.isBlank() || _uiState.value.isLoadingSecurity) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSecurity = true) }
            try {
                val response = api.activateTwoFactor(mapOf("code" to code))
                if (response.code == 200 && response.data == true) {
                    _uiState.update {
                        it.copy(
                            isLoadingSecurity = false,
                            twoFactorActivated = true,
                            showTwoFactorSetup = false,
                            twoFactorSecret = "",
                            twoFactorUrl = "",
                            twoFactorCode = ""
                        )
                    }
                    showMessage("两步验证已启用")
                } else {
                    _uiState.update { it.copy(isLoadingSecurity = false) }
                    showMessage(response.message ?: "验证码不正确")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingSecurity = false) }
                showMessage(e.message ?: "启用两步验证失败")
            }
        }
    }

    fun requestDeactivateTwoFactor() {
        _uiState.update { it.copy(confirmDeactivateTwoFactor = true) }
    }

    fun dismissDeactivateTwoFactor() {
        _uiState.update { it.copy(confirmDeactivateTwoFactor = false) }
    }

    fun deactivateTwoFactor() {
        _uiState.update { it.copy(confirmDeactivateTwoFactor = false) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSecurity = true) }
            try {
                val response = api.deactivateTwoFactor()
                if (response.code == 200 && response.data == true) {
                    _uiState.update {
                        it.copy(isLoadingSecurity = false, twoFactorActivated = false)
                    }
                    showMessage("两步验证已关闭")
                } else {
                    _uiState.update { it.copy(isLoadingSecurity = false) }
                    showMessage(response.message ?: "关闭两步验证失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingSecurity = false) }
                showMessage(e.message ?: "关闭两步验证失败")
            }
        }
    }

    // ── 应用设置 ──

    fun toggleAppsExpanded() {
        val shouldLoad = !_uiState.value.appsExpanded && !_uiState.value.hasLoadedApps
        _uiState.update { it.copy(appsExpanded = !it.appsExpanded) }
        if (shouldLoad) loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            runCatching { api.getApps() }
                .onSuccess { res ->
                    if (res.code == 200) {
                        _uiState.update {
                            it.copy(
                                apps = res.data ?: emptyList(),
                                isLoadingApps = false,
                                hasLoadedApps = true
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoadingApps = false, hasLoadedApps = true) }
                        showMessage(res.message ?: "获取应用失败")
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isLoadingApps = false, hasLoadedApps = true) }
                    showMessage(e.message ?: "获取应用失败")
                }
        }
    }

    fun showCreateApp() {
        _uiState.update {
            it.copy(
                showAppDialog = true,
                editingApp = null,
                editAppName = "",
                editAppScopes = emptySet()
            )
        }
    }

    fun showEditApp(app: AppInfo) {
        _uiState.update {
            it.copy(
                showAppDialog = true,
                editingApp = app,
                editAppName = app.name ?: "",
                editAppScopes = app.scopes?.toSet() ?: emptySet()
            )
        }
    }

    fun dismissAppDialog() {
        _uiState.update { it.copy(showAppDialog = false, editingApp = null, editAppName = "", editAppScopes = emptySet()) }
    }

    fun onAppNameChanged(v: String) { _uiState.update { it.copy(editAppName = v) } }

    fun toggleAppScope(scope: String) {
        _uiState.update {
            val scopes = it.editAppScopes.toMutableSet()
            if (!scopes.add(scope)) scopes.remove(scope)
            it.copy(editAppScopes = scopes)
        }
    }

    fun saveApp() {
        val s = _uiState.value
        if (s.editAppName.isBlank()) return
        val scopes = s.editAppScopes.toList()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            try {
                val res = if (s.editingApp == null) {
                    api.createApp(AppCreateRequest(name = s.editAppName.trim(), scopes = scopes))
                } else {
                    api.updateApp(AppUpdateRequest(id = s.editingApp.id ?: 0, name = s.editAppName.trim(), scopes = scopes))
                }
                if (res.code == 200) {
                    _uiState.update {
                        it.copy(
                            showAppDialog = false, isLoadingApps = false,
                            editingApp = null, editAppName = "", editAppScopes = emptySet(),
                        )
                    }
                    showMessage(if (s.editingApp == null) "应用已创建" else "应用已更新")
                    loadApps()
                } else {
                    _uiState.update { it.copy(isLoadingApps = false) }
                    showMessage(res.message ?: "保存失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingApps = false) }
                showMessage(e.message ?: "保存失败")
            }
        }
    }

    fun requestDeleteApp(app: AppInfo) {
        _uiState.update { it.copy(confirmDeleteApp = app) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(confirmDeleteApp = null) }
    }

    fun confirmDeleteApp() {
        val app = _uiState.value.confirmDeleteApp ?: return
        _uiState.update { it.copy(confirmDeleteApp = null) }
        deleteApp(app)
    }

    private fun deleteApp(app: AppInfo) {
        val id = app.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            try {
                val res = api.deleteApps(listOf(id))
                if (res.code == 200) {
                    _uiState.update { it.copy(isLoadingApps = false) }
                    showMessage("应用已删除")
                    loadApps()
                } else {
                    _uiState.update { it.copy(isLoadingApps = false) }
                    showMessage(res.message ?: "删除失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingApps = false) }
                showMessage(e.message ?: "删除失败")
            }
        }
    }

    fun requestResetSecret(app: AppInfo) {
        _uiState.update { it.copy(confirmResetApp = app) }
    }

    fun dismissResetConfirm() {
        _uiState.update { it.copy(confirmResetApp = null) }
    }

    fun confirmResetApp() {
        val app = _uiState.value.confirmResetApp ?: return
        _uiState.update { it.copy(confirmResetApp = null) }
        resetAppSecret(app)
    }

    private fun resetAppSecret(app: AppInfo) {
        val id = app.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            try {
                val res = api.resetAppSecret(id)
                if (res.code == 200) {
                    _uiState.update { it.copy(isLoadingApps = false) }
                    showMessage("密钥已重置")
                    loadApps()
                } else {
                    _uiState.update { it.copy(isLoadingApps = false) }
                    showMessage(res.message ?: "重置失败")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingApps = false) }
                showMessage(e.message ?: "重置失败")
            }
        }
    }

    private fun showMessage(message: String) {
        _events.trySend(SettingsEvent.Message(message))
    }
}
