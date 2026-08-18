package com.qinglong.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 任务状态码 */
object TaskStatus {
    const val RUNNING = 0
    const val QUEUED = 1      // status == 0.5
    const val IDLE = 2
    const val DISABLED = 3
    const val UNKNOWN = 4
}

/**
 * 定时任务（青龙 v2.17+ SQLite 后端，主键为数字 id）
 */
@Serializable
data class TaskInfo(
    val id: Int? = null,
    val name: String? = null,
    val command: String? = null,
    val schedule: String? = null,
    val status: Double? = null,   // 0=running, 0.5=queued, 1=idle
    val pid: Int? = null,
    @SerialName("isDisabled") val isDisabled: Int? = null,
    @SerialName("isSystem") val isSystem: Int? = null,
    @SerialName("isPinned") val isPinned: Int? = null,
    val labels: List<String>? = null,
    @SerialName("last_running_time") val lastRunningTime: Long? = null,
    @SerialName("last_execution_time") val lastExecutionTime: Long? = null,
    @SerialName("sub_id") val subId: Int? = null,
    @SerialName("log_path") val logPath: String? = null,
    @SerialName("log_name") val logName: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null
) {
    val statusCode: Int
        get() = when {
            isDisabled == 1 -> TaskStatus.DISABLED
            status != null && status <= 0.0 -> TaskStatus.RUNNING
            status != null && status <= 0.5 -> TaskStatus.QUEUED
            else -> TaskStatus.IDLE
        }

    val statusText: String
        get() = when (statusCode) {
            TaskStatus.RUNNING -> "运行中"
            TaskStatus.QUEUED -> "队列中"
            TaskStatus.DISABLED -> "已禁用"
            else -> "空闲中"
        }

    val pinned: Boolean get() = isPinned == 1
}

/** 分页任务列表响应 (v2.17+ API) */
@Serializable
data class TaskListData(
    val data: List<TaskInfo>? = null,
    val total: Int? = null
)

/** 创建任务请求体（POST /api/crons） */
@Serializable
data class TaskCreateRequest(
    val name: String,
    val command: String,
    val schedule: String
)

/** 更新任务请求体（PUT /api/crons） */
@Serializable
data class TaskUpdateRequest(
    val id: Int,
    val name: String,
    val command: String,
    val schedule: String
)
