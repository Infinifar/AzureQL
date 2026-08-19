package com.qinglong.feature.settings

import com.qinglong.core.model.AppInfo
import com.qinglong.core.model.LoginLogEntry
import com.qinglong.core.model.SystemConfig

data class SettingsUiState(
    // 系统配置
    val systemConfig: SystemConfig? = null,
    val isLoadingConfig: Boolean = false,
    val configExpanded: Boolean = false,
    // 编辑字段
    val editLogFrequency: String = "",
    val editConcurrency: String = "",
    // 登录日志
    val loginLogs: List<LoginLogEntry> = emptyList(),
    val isLoadingLogs: Boolean = false,
    val logsExpanded: Boolean = false,
    // 应用设置
    val apps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val appsExpanded: Boolean = false,
    val showAppDialog: Boolean = false,
    val editingApp: AppInfo? = null,
    val editAppName: String = "",
    val editAppScopes: Set<String> = emptySet(),
    val confirmDeleteApp: AppInfo? = null,
    val confirmResetApp: AppInfo? = null,
    // 修改密码
    val showPasswordDialog: Boolean = false,
    val oldPassword: String = "",
    val newPassword: String = "",
    val isLoadingPassword: Boolean = false,
    // 服务端版本
    val serverVersion: String? = null,
    val error: String? = null,
    val successMessage: String? = null
)
