package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.LogFile
import com.autopanel.core.model.LogDeleteRequest
import com.autopanel.core.model.LoginLogEntry
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>
) : LogRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getLogFiles(): Result<List<LogFile>> {
        return try {
            val res = api.getLogFiles()
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "获取日志文件列表失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getLogContent(file: String, path: String): Result<String> {
        return try {
            val res = api.getLogDetail(file, path)
            if (res.code == 200) Result.success(res.data ?: "")
            else Result.failure(Exception(res.message ?: "获取日志内容失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getTaskLog(taskId: Int): Result<String> {
        return try {
            val res = api.getTaskLog(taskId)
            if (res.code == 200) Result.success(res.data ?: "")
            else Result.failure(Exception(res.message ?: "获取任务日志失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getLoginLogs(): Result<List<LoginLogEntry>> {
        return try {
            val res = api.getLoginLogs()
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "获取登录日志失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun deleteLog(log: LogFile): Result<Unit> {
        val filename = log.title ?: return Result.failure(IllegalArgumentException("日志文件名为空"))
        return try {
            val response = api.deleteLog(
                LogDeleteRequest(
                    filename = filename,
                    path = log.parent.orEmpty(),
                    type = log.type
                )
            )
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception(response.message ?: "删除日志失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
