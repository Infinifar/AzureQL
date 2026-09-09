package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.LogFile
import com.autopanel.core.model.LogDeleteRequest
import com.autopanel.core.model.LoginLogEntry
import com.autopanel.core.model.SystemLogContent
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
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

    override suspend fun getSystemLog(day: String, limit: Int): Result<SystemLogContent> {
        val safeLimit = limit.coerceIn(1, MAX_SYSTEM_LOG_BYTES)
        return try {
            var response = api.getSystemLog(day, day, safeLimit)
            if (response.code() == 400) {
                response.errorBody()?.close()
                // QingLong <= 2.20 rejects the newer `limit` query key. The streamed response is
                // still bounded below, so retrying without it remains memory-safe.
                response = api.getSystemLog(day, day, null)
            }
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return Result.failure(Exception("获取系统日志失败（HTTP ${response.code()}）"))
            }
            val body = response.body()
                ?: return Result.failure(Exception("系统日志响应为空"))
            val totalBytes = response.headers()["X-QL-Log-Total"]?.toLongOrNull()
                ?: body.contentLength().coerceAtLeast(0L)
            val serverTruncated = response.headers()["X-QL-Log-Truncated"]
                ?.equals("true", ignoreCase = true) == true
            val (bytes, clientTruncated) = body.use { readAtMost(it.byteStream(), safeLimit) }
            Result.success(
                SystemLogContent(
                    content = bytes.toString(Charsets.UTF_8),
                    totalBytes = totalBytes,
                    truncated = serverTruncated || clientTruncated || totalBytes > bytes.size
                )
            )
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

    private fun readAtMost(input: java.io.InputStream, maxBytes: Int): Pair<ByteArray, Boolean> {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var remaining = maxBytes + 1
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        val all = output.toByteArray()
        return if (all.size > maxBytes) all.copyOf(maxBytes) to true else all to false
    }

    private companion object {
        const val MAX_SYSTEM_LOG_BYTES = 1_048_576
    }
}
