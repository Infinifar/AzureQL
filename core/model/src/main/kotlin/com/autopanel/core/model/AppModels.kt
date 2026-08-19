package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 青龙「应用」（OAuth2 客户端应用）
 * 对应 GET/POST/PUT /api/apps 返回的应用结构。
 */
@Serializable
data class AppInfo(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    val scopes: List<String>? = null
)

/** POST /api/apps 创建应用请求体 */
@Serializable
data class AppCreateRequest(
    val name: String,
    val scopes: List<String>
)

/** PUT /api/apps 更新应用请求体 */
@Serializable
data class AppUpdateRequest(
    val id: Int,
    val name: String,
    val scopes: List<String>
)

/** 可用权限（scope）清单及中文标签 */
object AppScopes {
    const val ENVS = "envs"
    const val CRONS = "crons"
    const val CONFIGS = "configs"
    const val SCRIPTS = "scripts"
    const val LOGS = "logs"
    const val SYSTEM = "system"
    const val DASHBOARD = "dashboard"

    val ALL = listOf(ENVS, CRONS, CONFIGS, SCRIPTS, LOGS, SYSTEM, DASHBOARD)

    fun label(scope: String): String = when (scope) {
        ENVS -> "环境变量"
        CRONS -> "定时任务"
        CONFIGS -> "配置文件"
        SCRIPTS -> "脚本管理"
        LOGS -> "任务日志"
        SYSTEM -> "系统管理"
        DASHBOARD -> "仪表盘"
        else -> scope
    }
}
