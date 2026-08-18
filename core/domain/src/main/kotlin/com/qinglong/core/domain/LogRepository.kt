package com.qinglong.core.domain

import com.qinglong.core.model.LogFile
import com.qinglong.core.model.LoginLogEntry

interface LogRepository {
    suspend fun getLogFiles(): Result<List<LogFile>>
    suspend fun getLogContent(file: String, path: String = ""): Result<String>
    suspend fun getTaskLog(taskId: Int): Result<String>
    suspend fun getLoginLogs(): Result<List<LoginLogEntry>>
}
