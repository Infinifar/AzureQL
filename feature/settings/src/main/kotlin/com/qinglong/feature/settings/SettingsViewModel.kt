package com.qinglong.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.data.remote.QLApiService
import com.qinglong.core.domain.ConfigRepository
import com.qinglong.core.domain.LogRepository
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

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }
}
