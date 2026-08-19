package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.TaskCreateRequest
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskUpdateRequest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>
) : TaskRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getTasks(search: String, page: Int, size: Int): Result<Pair<List<TaskInfo>, Int>> {
        return try {
            val res = api.getTasks(search, page, size)
            if (res.code == 200) {
                val listData = res.data
                if (listData != null) {
                    Result.success(Pair(listData.data.orEmpty(), listData.total ?: 0))
                } else {
                    Result.success(Pair(emptyList(), 0))
                }
            } else {
                Result.failure(Exception(res.message ?: "获取任务列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addTask(name: String, command: String, schedule: String): Result<Unit> {
        return try {
            val res = api.addTask(TaskCreateRequest(name, command, schedule))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "添加任务失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTask(id: Int, name: String, command: String, schedule: String): Result<Unit> {
        return try {
            val res = api.updateTask(TaskUpdateRequest(id, name, command, schedule))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "更新任务失败"))
        } catch (e: Exception) {
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

    override suspend fun getTaskLog(id: Int): Result<String> {
        return try {
            val res = api.getTaskLog(id)
            if (res.code == 200) {
                Result.success(res.data ?: "")
            } else {
                Result.failure(Exception(res.message ?: "加载日志失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun apiCall(call: suspend () -> com.autopanel.core.model.ApiResponse<Unit>): Result<Unit> {
        return try {
            val res = call()
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "操作失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
