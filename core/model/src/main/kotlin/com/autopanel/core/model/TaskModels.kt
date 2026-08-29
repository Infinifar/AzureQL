package com.autopanel.core.model

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
    @SerialName("extra_schedules") val extraSchedules: List<TaskExtraSchedule>? = null,
    @SerialName("task_before") val taskBefore: String? = null,
    @SerialName("task_after") val taskAfter: String? = null,
    @SerialName("allow_multiple_instances") val allowMultipleInstances: Int? = null,
    @SerialName("work_dir") val workDir: String? = null,
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

@Serializable
data class TaskExtraSchedule(
    val schedule: String
)

enum class TaskScheduleType {
    NORMAL,
    ONCE,
    BOOT;

    fun toSchedule(normalSchedule: String): String = when (this) {
        NORMAL -> normalSchedule
        ONCE -> "@once"
        BOOT -> "@boot"
    }

    companion object {
        fun fromSchedule(schedule: String?): TaskScheduleType = when {
            schedule?.startsWith("@once") == true -> ONCE
            schedule?.startsWith("@boot") == true -> BOOT
            else -> NORMAL
        }
    }
}

data class TaskDraft(
    val id: Int? = null,
    val name: String = "",
    val command: String = "",
    val scheduleType: TaskScheduleType = TaskScheduleType.NORMAL,
    val schedule: String = "",
    val extraSchedules: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val allowMultipleInstances: Boolean = false,
    val logName: String = "",
    val workDir: String = "",
    val taskBefore: String = "",
    val taskAfter: String = ""
)

fun TaskInfo.toDraft(): TaskDraft = TaskDraft(
    id = id,
    name = name.orEmpty(),
    command = command.orEmpty(),
    scheduleType = TaskScheduleType.fromSchedule(schedule),
    schedule = schedule.takeUnless { it?.startsWith('@') == true }.orEmpty(),
    extraSchedules = extraSchedules.orEmpty().map(TaskExtraSchedule::schedule),
    labels = labels.orEmpty(),
    allowMultipleInstances = allowMultipleInstances == 1,
    logName = logName.orEmpty(),
    workDir = workDir.orEmpty(),
    taskBefore = taskBefore.orEmpty(),
    taskAfter = taskAfter.orEmpty()
)

/** 创建任务请求体（POST /api/crons） */
@Serializable
data class TaskCreateRequest(
    val name: String,
    val command: String,
    val schedule: String,
    val labels: List<String>,
    @SerialName("extra_schedules") val extraSchedules: List<TaskExtraSchedule>,
    @SerialName("task_before") val taskBefore: String,
    @SerialName("task_after") val taskAfter: String,
    @SerialName("log_name") val logName: String,
    @SerialName("allow_multiple_instances") val allowMultipleInstances: Int,
    @SerialName("work_dir") val workDir: String
)

/** 更新任务请求体（PUT /api/crons） */
@Serializable
data class TaskUpdateRequest(
    val id: Int,
    val name: String,
    val command: String,
    val schedule: String,
    val labels: List<String>,
    @SerialName("extra_schedules") val extraSchedules: List<TaskExtraSchedule>,
    @SerialName("task_before") val taskBefore: String,
    @SerialName("task_after") val taskAfter: String,
    @SerialName("log_name") val logName: String,
    @SerialName("allow_multiple_instances") val allowMultipleInstances: Int,
    @SerialName("work_dir") val workDir: String
)
