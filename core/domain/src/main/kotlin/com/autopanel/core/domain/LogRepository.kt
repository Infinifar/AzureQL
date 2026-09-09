package com.autopanel.core.domain

import com.autopanel.core.model.LogFile
import com.autopanel.core.model.LoginLogEntry
import com.autopanel.core.model.SystemLogContent

interface LogRepository {
    suspend fun getLogFiles(): Result<List<LogFile>>
    suspend fun getLogContent(file: String, path: String = ""): Result<String>
    suspend fun getTaskLog(taskId: Int): Result<String>
    suspend fun getLoginLogs(): Result<List<LoginLogEntry>>
    suspend fun getSystemLog(day: String, limit: Int = 1_048_576): Result<SystemLogContent>
    suspend fun deleteLog(log: LogFile): Result<Unit>
}
