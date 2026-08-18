package com.qinglong.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginLogEntry(
    val address: String? = null,
    val ip: String? = null,
    val platform: String? = null,
    val status: Int? = null,     // 0=success, 1=failure
    val time: String? = null
) {
    val statusText: String
        get() = if (status == 1) "失败" else "成功"
}

@Serializable
data class LoginLogsResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: List<LoginLogEntry>? = null
)

@Serializable
data class SystemConfig(
    @SerialName("logRemoveFrequency") val logRemoveFrequency: Int? = null,
    @SerialName("cronConcurrency") val cronConcurrency: Int? = null
)

/** system config 响应中 data.info 的结构 */
@Serializable
data class SystemConfigData(
    val info: SystemConfig? = null
)

@Serializable
data class DependenceLogEntry(
    val log: List<String>? = null
)

@Serializable
data class DependenceLogResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: DependenceLogEntry? = null
)

/** 日志文件条目（GET /api/logs/ 返回） */
@Serializable
data class LogFile(
    val id: String? = null,
    val name: String? = null,
    val path: String? = null,
    val size: Long? = null,
    @SerialName("taskId") val taskId: String? = null,
    @SerialName("taskName") val taskName: String? = null,
    val status: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null
)
