package com.qinglong.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.data.remote.QLApiService
import com.qinglong.core.domain.ConfigRepository
import com.qinglong.core.domain.LogRepository
import com.qinglong.core.model.AppCreateRequest
import com.qinglong.core.model.AppInfo
import com.qinglong.core.model.AppUpdateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepo: ConfigRepository,
    private val logRepo: LogRepository,
    private val api: QLApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSystemConfig()
        loadLoginLogs()
        loadServerVersion()
        loadApps()
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
                            editLogFrequency = cfg.logRemoveFrequency?.toString() ?: "",
                            editConcurrency = cfg.cronConcurrency?.toString() ?: ""
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingConfig = false, error = e.message) }
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
                .onFailure { e -> Log.w("Settings", "getSystemInfo 失败: ${e.message}") }
        }
    }

    fun toggleConfigExpanded() {
        _uiState.update { it.copy(configExpanded = !it.configExpanded) }
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
                    _uiState.update { it.copy(systemConfig = newCfg, successMessage = "配置已保存") }
                    loadSystemConfig()
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun loadLoginLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLogs = true) }
            logRepo.getLoginLogs()
                .onSuccess { list -> _uiState.update { it.copy(loginLogs = list, isLoadingLogs = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingLogs = false, error = e.message) } }
        }
    }

    fun toggleLogsExpanded() {
        _uiState.update { it.copy(logsExpanded = !it.logsExpanded) }
    }

    // ── 修改密码 ──

    fun showPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialog = true, oldPassword = "", newPassword = "") }
    }

    fun dismissPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialog = false, oldPassword = "", newPassword = "") }
    }

    fun onOldPasswordChanged(v: String) { _uiState.update { it.copy(oldPassword = v) } }
    fun onNewPasswordChanged(v: String) { _uiState.update { it.copy(newPassword = v) } }

    fun changePassword() {
        val s = _uiState.value
        if (s.oldPassword.isEmpty() || s.newPassword.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPassword = true) }
            try {
                val res = api.updateAccount(mapOf("password" to s.newPassword, "username" to s.oldPassword))
                if (res.code == 200) {
                    _uiState.update {
                        it.copy(
                            showPasswordDialog = false, isLoadingPassword = false,
                            successMessage = "密码已修改"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoadingPassword = false, error = res.message ?: "修改失败")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingPassword = false, error = e.message) }
            }
        }
    }

    // ── 应用设置 ──

    fun toggleAppsExpanded() {
        _uiState.update { it.copy(appsExpanded = !it.appsExpanded) }
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            runCatching { api.getApps() }
                .onSuccess { res ->
                    if (res.code == 200) {
                        _uiState.update { it.copy(apps = res.data ?: emptyList(), isLoadingApps = false) }
                    } else {
                        _uiState.update { it.copy(isLoadingApps = false, error = res.message ?: "获取应用失败") }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingApps = false, error = e.message) }
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
                            successMessage = if (s.editingApp == null) "应用已创建" else "应用已更新"
                        )
                    }
                    loadApps()
                } else {
                    _uiState.update { it.copy(isLoadingApps = false, error = res.message ?: "保存失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingApps = false, error = e.message) }
            }
        }
    }

    fun deleteApp(app: AppInfo) {
        val id = app.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            try {
                val res = api.deleteApps(listOf(id))
                if (res.code == 200) {
                    _uiState.update { it.copy(isLoadingApps = false, successMessage = "应用已删除") }
                    loadApps()
                } else {
                    _uiState.update { it.copy(isLoadingApps = false, error = res.message ?: "删除失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingApps = false, error = e.message) }
            }
        }
    }

    fun resetAppSecret(app: AppInfo) {
        val id = app.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            try {
                val res = api.resetAppSecret(id)
                if (res.code == 200) {
                    _uiState.update { it.copy(isLoadingApps = false, successMessage = "密钥已重置") }
                    loadApps()
                } else {
                    _uiState.update { it.copy(isLoadingApps = false, error = res.message ?: "重置失败") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingApps = false, error = e.message) }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }
}
