package com.qinglong.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 任务状态码 */
object TaskStatus {
    const val RUNNING = 0
    const val WAITING = 1
    const val IDLE = 2
    const val DISABLED = 3
    const val UNKNOWN = 4
}

@Serializable
data class TaskInfo(
    @Serializable(with = ObjectIdSerializer::class)
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val command: String? = null,
    val schedule: String? = null,
    val status: Double? = null,   // 0=running, 0.5=waiting, 1=idle
    @SerialName("isDisabled") val isDisabled: Int? = null,
    @SerialName("isPinned") val isPinned: Int? = null,
    @SerialName("isSystem") val isSystem: Int? = null,
    val pid: String? = null,
    @SerialName("log_path") val logPath: String? = null,
    @SerialName("last_running_time") val lastRunningTime: Long? = null,
    @SerialName("last_execution_time") val lastExecutionTime: Long? = null,
    val saved: String? = null,
    val timestamp: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null
) {
    /** 推导状态码：0=运行中, 1=队列中, 2=空闲, 3=已禁用 */
    val statusCode: Int
        get() = when {
            isDisabled == 1 -> TaskStatus.DISABLED
            status != null && status <= 0.0 -> TaskStatus.RUNNING
            status != null && status <= 0.5 -> TaskStatus.WAITING
            else -> TaskStatus.IDLE
        }

    val statusText: String
        get() = when (statusCode) {
            TaskStatus.RUNNING -> "运行中"
            TaskStatus.WAITING -> "队列中"
            TaskStatus.DISABLED -> "已禁用"
            else -> "空闲中"
        }

    val pinned: Boolean get() = isPinned == 1
}

/** 分页任务列表响应 (v15 API) */
@Serializable
data class TaskListData(
    val data: List<TaskInfo>? = null,
    val total: Int? = null
)
