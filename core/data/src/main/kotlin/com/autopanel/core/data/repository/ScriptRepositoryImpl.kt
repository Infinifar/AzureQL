package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.model.ScriptAddRequest
import com.autopanel.core.model.ScriptDeleteRequest
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.ScriptUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptRepositoryImpl @Inject constructor(
    private val api: AutoPanelApiService
) : ScriptRepository {

    override suspend fun getScripts(): Result<List<ScriptFile>> {
        return try {
            val res = api.getScripts()
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "获取脚本列表失败"))
        } catch (e: Exception) {
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
            Result.failure(e)
        }
    }

    override suspend fun addScript(filename: String, path: String, content: String): Result<Unit> {
        return try {
            val res = api.addScript(ScriptAddRequest(filename, path, content))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "添加脚本失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateScript(filename: String, path: String, content: String): Result<Unit> {
        return try {
            val res = api.updateScript(ScriptUpdateRequest(filename, path, content))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "保存脚本失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteScript(filename: String, path: String, isDir: Boolean): Result<Unit> {
        return try {
            val res = api.deleteScript(
                ScriptDeleteRequest(filename, path, if (isDir) "directory" else "file")
            )
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "删除脚本失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
