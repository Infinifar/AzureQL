package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.model.DependencyCreateRequest
import com.autopanel.core.model.DependencyInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DependencyRepositoryImpl @Inject constructor(
    private val api: AutoPanelApiService
) : DependencyRepository {

    override suspend fun getDependencies(search: String, type: String): Result<List<DependencyInfo>> {
        return try {
            val res = api.getDependencies(search, type)
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "获取依赖列表失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addDependencies(deps: List<Pair<String, String>>): Result<Unit> {
        return try {
            val body = deps.map { DependencyCreateRequest(it.first, it.second) }
            val res = api.addDependencies(body)
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "新建依赖失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reinstallDependencies(ids: List<Int>) = apiCall { api.reinstallDependencies(ids) }
    override suspend fun deleteDependencies(ids: List<Int>) = apiCall { api.deleteDependencies(ids) }

    override suspend fun getDependenceLog(id: Int): Result<String> {
        return try {
            val res = api.getDependenceLog(id)
            if (res.code == 200) {
                val logEntry = res.data
                if (logEntry != null) {
                    Result.success(logEntry.log?.joinToString("\n").orEmpty())
                } else {
                    Result.success("")
                }
            } else {
                Result.failure(Exception(res.message ?: "获取日志失败"))
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
