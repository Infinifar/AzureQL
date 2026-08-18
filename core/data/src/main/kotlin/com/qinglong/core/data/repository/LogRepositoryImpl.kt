package com.qinglong.core.data.repository

import com.qinglong.core.data.remote.QLApiService
import com.qinglong.core.domain.LogRepository
import com.qinglong.core.model.LogFile
import com.qinglong.core.model.LoginLogEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val api: QLApiService
) : LogRepository {

    override suspend fun getLogFiles(): Result<List<LogFile>> {
        return try {
            val res = api.getLogFiles()
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "获取日志文件列表失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLogContent(file: String, path: String): Result<String> {
        return try {
            val res = api.getLogDetail(file, path)
            if (res.code == 200) Result.success(res.data ?: "")
            else Result.failure(Exception(res.message ?: "获取日志内容失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTaskLog(taskId: Int): Result<String> {
        return try {
            val res = api.getTaskLog(taskId)
            if (res.code == 200) Result.success(res.data ?: "")
            else Result.failure(Exception(res.message ?: "获取任务日志失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLoginLogs(): Result<List<LoginLogEntry>> {
        return try {
            val res = api.getLoginLogs()
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "获取登录日志失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
