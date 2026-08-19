package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.BackupRepository
import com.autopanel.core.model.BackupExportRequest
import com.autopanel.core.model.BackupModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val api: AutoPanelApiService
) : BackupRepository {

    override suspend fun exportBackup(
        modules: Set<BackupModule>,
        destination: OutputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val selected = (modules + BackupModule.BASE).map(BackupModule::apiValue)
            val response = api.exportData(BackupExportRequest(selected))
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                body?.close()
                response.errorBody()?.close()
                return@withContext Result.failure(
                    Exception("导出备份失败（HTTP ${response.code()}）")
                )
            }
            body.use { responseBody ->
                val input = BufferedInputStream(responseBody.byteStream())
                input.use {
                    if (!it.hasGzipHeader()) {
                        return@withContext Result.failure(
                            Exception("服务端未返回有效的 Gzip 备份")
                        )
                    }
                    it.copyTo(destination)
                }
            }
            destination.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun importBackup(
        source: InputStream,
        contentLength: Long?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val buffered = (source as? BufferedInputStream) ?: BufferedInputStream(source)
            if (!buffered.hasGzipHeader()) {
                return@withContext Result.failure(
                    IllegalArgumentException("所选文件不是有效的 .tgz/.gz 备份")
                )
            }
            val requestBody = InputStreamRequestBody(buffered, contentLength)
            val part = MultipartBody.Part.createFormData("data", "data.tgz", requestBody)
            val response = api.importData(part)
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception(response.message ?: "上传备份失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun activateImportedBackup(): Result<Unit> {
        return try {
            val response = api.activateImportedData()
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception(response.message ?: "恢复备份失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun healthCheck(): Result<Unit> {
        return try {
            val response = api.healthCheck()
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception(response.message ?: "服务尚未恢复"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun BufferedInputStream.hasGzipHeader(): Boolean {
        mark(2)
        val first = read()
        val second = read()
        reset()
        return first == 0x1f && second == 0x8b
    }

    private class InputStreamRequestBody(
        private val input: InputStream,
        private val length: Long?
    ) : RequestBody() {
        override fun contentType() = "application/gzip".toMediaType()
        override fun contentLength(): Long = length?.takeIf { it >= 0 } ?: -1L
        override fun isOneShot(): Boolean = true

        override fun writeTo(sink: BufferedSink) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                sink.write(buffer, 0, count)
            }
        }
    }
}
