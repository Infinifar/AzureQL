package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.model.ScriptAddRequest
import com.autopanel.core.model.ScriptDeleteRequest
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.ScriptUpdateRequest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ScriptRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>,
    private val responseCache: ResponseCache
) : ScriptRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getCachedScripts(): List<ScriptFile>? =
        responseCache.read(ResponseCache.SCRIPTS, ListSerializer(ScriptFile.serializer()))

    override suspend fun getScripts(): Result<List<ScriptFile>> {
        return try {
            val res = api.getScripts()
            if (res.code == 200) {
                val value = res.data.orEmpty()
                responseCache.write(
                    ResponseCache.SCRIPTS,
                    ListSerializer(ScriptFile.serializer()),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取脚本列表失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getScriptContent(filename: String, path: String): Result<String> {
        return try {
            val res = api.getScriptContent(filename, path)
            if (res.code == 200) {
                Result.success(res.data ?: "")
            } else {
                Result.failure(Exception(res.message ?: "获取脚本内容失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(Exception("读取脚本 $filename 失败: ${e.message ?: "未知错误"}", e))
        }
    }

    override suspend fun addScript(filename: String, path: String, content: String): Result<Unit> {
        return try {
            val res = api.addScript(ScriptAddRequest(filename, path, content))
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.SCRIPTS)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "添加脚本失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateScript(filename: String, path: String, content: String): Result<Unit> {
        return try {
            val res = api.updateScript(ScriptUpdateRequest(filename, path, content))
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.SCRIPTS)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "保存脚本失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(Exception("保存脚本 $filename 失败: ${e.message ?: "未知错误"}", e))
        }
    }

    override suspend fun deleteScript(filename: String, path: String, isDir: Boolean): Result<Unit> {
        return try {
            val res = api.deleteScript(
                ScriptDeleteRequest(filename, path, if (isDir) "directory" else "file")
            )
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.SCRIPTS)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "删除脚本失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
