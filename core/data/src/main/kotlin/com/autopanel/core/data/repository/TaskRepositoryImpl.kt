package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.TaskCreateRequest
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskExtraSchedule
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskLogChunk
import com.autopanel.core.model.TaskScheduleType
import com.autopanel.core.model.TaskUpdateRequest
import com.autopanel.core.model.TaskListData
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>,
    private val responseCache: ResponseCache
) : TaskRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getCachedTasks(
        search: String,
        page: Int,
        size: Int,
        labels: Set<String>
    ): Pair<List<TaskInfo>, Int>? {
        val cached = responseCache.read(
            ResponseCache.taskPageKey(search, page, size, labels),
            TaskListData.serializer()
        ) ?: return null
        return cached.data.orEmpty() to (cached.total ?: 0)
    }

    override suspend fun getTasks(
        search: String,
        page: Int,
        size: Int,
        labels: Set<String>
    ): Result<Pair<List<TaskInfo>, Int>> {
        return try {
            val res = api.getTasks(search, page, size, labels.toQingLongViewQuery())
            if (res.code == 200) {
                val listData = res.data
                if (listData != null) {
                    responseCache.write(
                        ResponseCache.taskPageKey(search, page, size, labels),
                        TaskListData.serializer(),
                        listData
                    )
                    Result.success(Pair(listData.data.orEmpty(), listData.total ?: 0))
                } else {
                    Result.success(Pair(emptyList(), 0))
                }
            } else {
                Result.failure(Exception(res.message ?: "获取任务列表失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun addTask(draft: TaskDraft): Result<Unit> {
        return try {
            val res = api.addTask(draft.toCreateRequest())
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.TASKS_PREFIX)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "添加任务失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateTask(draft: TaskDraft): Result<Unit> {
        return try {
            val res = api.updateTask(draft.toUpdateRequest())
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.TASKS_PREFIX)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "更新任务失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun deleteTasks(ids: List<Int>) = apiCall { api.deleteTasks(ids) }
    override suspend fun runTasks(ids: List<Int>) = apiCall { api.runTasks(ids) }
    override suspend fun stopTasks(ids: List<Int>) = apiCall { api.stopTasks(ids) }
    override suspend fun enableTasks(ids: List<Int>) = apiCall { api.enableTasks(ids) }
    override suspend fun disableTasks(ids: List<Int>) = apiCall { api.disableTasks(ids) }
    override suspend fun pinTasks(ids: List<Int>) = apiCall { api.pinTasks(ids) }
    override suspend fun unpinTasks(ids: List<Int>) = apiCall { api.unpinTasks(ids) }

    override suspend fun getTask(id: Int): Result<TaskInfo> {
        return try {
            val res = api.getTaskDetail(id)
            val data = res.data
            if (res.code == 200 && data != null) {
                Result.success(data)
            } else {
                Result.failure(Exception(res.message ?: "获取任务详情失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getTaskLog(id: Int): Result<String> {
        return try {
            val res = api.getTaskLog(id)
            if (res.code == 200) {
                Result.success(res.data ?: "")
            } else {
                Result.failure(Exception(res.message ?: "加载日志失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getTaskLogChunk(
        id: Int,
        offset: Long?,
        limit: Int,
        tail: Boolean
    ): Result<TaskLogChunk> {
        return try {
            val response = api.getTaskLogChunk(
                id = id,
                offset = offset,
                limit = limit.coerceIn(1, 1024 * 1024),
                tail = tail
            )
            if (response.code == 200) {
                val content = response.content ?: response.data.orEmpty()
                val start = response.offset ?: offset ?: 0L
                val next = response.nextOffset
                    ?: (start + content.toByteArray(Charsets.UTF_8).size)
                Result.success(
                    TaskLogChunk(
                        content = content,
                        offset = start,
                        nextOffset = next,
                        total = response.total ?: next,
                        truncated = response.truncated == true,
                        logStatus = (response.logStatus as? JsonPrimitive)?.contentOrNull
                    )
                )
            } else {
                Result.failure(Exception(response.message ?: "加载日志失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun apiCall(call: suspend () -> com.autopanel.core.model.ApiResponse<Unit>): Result<Unit> {
        return try {
            val res = call()
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.TASKS_PREFIX)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "操作失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}

private fun Set<String>.toQingLongViewQuery(): String? {
    val normalized = map(String::trim).filter(String::isNotEmpty).distinct().sorted()
    if (normalized.isEmpty()) return null
    return buildJsonObject {
        put("filterRelation", "and")
        putJsonArray("filters") {
            normalized.forEach { label ->
                addJsonObject {
                    put("property", "labels")
                    put("value", label)
                    put("operation", "Reg")
                }
            }
        }
    }.toString()
}

private fun TaskDraft.toCreateRequest() = TaskCreateRequest(
    name = name,
    command = command,
    schedule = scheduleType.toSchedule(schedule),
    labels = labels,
    extraSchedules = if (scheduleType == TaskScheduleType.NORMAL) {
        extraSchedules.map(::TaskExtraSchedule)
    } else {
        emptyList()
    },
    taskBefore = taskBefore,
    taskAfter = taskAfter,
    logName = logName,
    allowMultipleInstances = if (allowMultipleInstances) 1 else 0,
    workDir = workDir
)

private fun TaskDraft.toUpdateRequest() = TaskUpdateRequest(
    id = requireNotNull(id) { "任务 ID 不能为空" },
    name = name,
    command = command,
    schedule = scheduleType.toSchedule(schedule),
    labels = labels,
    extraSchedules = if (scheduleType == TaskScheduleType.NORMAL) {
        extraSchedules.map(::TaskExtraSchedule)
    } else {
        emptyList()
    },
    taskBefore = taskBefore,
    taskAfter = taskAfter,
    logName = logName,
    allowMultipleInstances = if (allowMultipleInstances) 1 else 0,
    workDir = workDir
)
