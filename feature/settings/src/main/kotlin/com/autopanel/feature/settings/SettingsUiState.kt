package com.autopanel.feature.settings

import com.autopanel.core.model.AppInfo
import com.autopanel.core.model.LoginLogEntry
import com.autopanel.core.model.SystemConfig

data class SettingsUiState(
    // 本地应用偏好
    val languageTag: String = "system",
    val biometricEnabled: Boolean = false,
    // 系统配置
    val systemConfig: SystemConfig? = null,
    val isLoadingConfig: Boolean = false,
    val hasLoadedConfig: Boolean = false,
    val configExpanded: Boolean = false,
    // 编辑字段
    val editLogFrequency: String = "",
    val editConcurrency: String = "",
    // 登录日志
    val loginLogs: List<LoginLogEntry> = emptyList(),
    val isLoadingLogs: Boolean = false,
    val hasLoadedLogs: Boolean = false,
    val logsExpanded: Boolean = false,
    // 应用设置
    val apps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val hasLoadedApps: Boolean = false,
    val appsExpanded: Boolean = false,
    val showAppDialog: Boolean = false,
    val editingApp: AppInfo? = null,
    val editAppName: String = "",
    val editAppScopes: Set<String> = emptySet(),
    val confirmDeleteApp: AppInfo? = null,
    val confirmResetApp: AppInfo? = null,
    // 修改密码
    val showPasswordDialog: Boolean = false,
    val accountUsername: String = "",
    val newPassword: String = "",
    val isLoadingPassword: Boolean = false,
    // 安全设置
    val securityExpanded: Boolean = false,
    val hasLoadedSecurity: Boolean = false,
    val isLoadingSecurity: Boolean = false,
    val twoFactorActivated: Boolean = false,
    val showTwoFactorSetup: Boolean = false,
    val twoFactorSecret: String = "",
    val twoFactorUrl: String = "",
    val twoFactorCode: String = "",
    val confirmDeactivateTwoFactor: Boolean = false,
    // 服务端版本
    val serverVersion: String? = null
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
}
