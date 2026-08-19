package com.autopanel.core.domain

import com.autopanel.core.model.BackupModule
import java.io.InputStream
import java.io.OutputStream

/**
 * 青龙官方备份接口。输入输出均采用流，避免把可能很大的归档一次性载入内存。
 * 流的生命周期由调用方负责，仓库只在挂起调用期间读写。
 */
interface BackupRepository {
    suspend fun exportBackup(modules: Set<BackupModule>, destination: OutputStream): Result<Unit>
    suspend fun importBackup(source: InputStream, contentLength: Long?): Result<Unit>
    suspend fun activateImportedBackup(): Result<Unit>
    suspend fun healthCheck(): Result<Unit>
}
