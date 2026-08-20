package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.EnvRepository
import com.autopanel.core.model.EnvCreateRequest
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.model.EnvUpdateRequest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class EnvRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>
) : EnvRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getEnvs(search: String): Result<List<EnvInfo>> {
        return try {
            val res = api.getEnvs(search)
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "获取环境变量失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun addEnvs(envs: List<Triple<String, String, String?>>): Result<List<EnvInfo>> {
        return try {
            val body = envs.map { EnvCreateRequest(it.first, it.second, it.third) }
            val res = api.addEnvs(body)
            if (res.code == 200) Result.success(res.data.orEmpty())
            else Result.failure(Exception(res.message ?: "添加失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateEnv(id: Int, name: String, value: String, remarks: String?): Result<Unit> {
        return try {
            val res = api.updateEnv(EnvUpdateRequest(id, name, value, remarks))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "更新失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun deleteEnvs(ids: List<Int>) = apiCall { api.deleteEnvs(ids) }
    override suspend fun enableEnvs(ids: List<Int>) = apiCall { api.enableEnvs(ids) }
    override suspend fun disableEnvs(ids: List<Int>) = apiCall { api.disableEnvs(ids) }
    override suspend fun pinEnvs(ids: List<Int>) = apiCall { api.pinEnvs(ids) }
    override suspend fun unpinEnvs(ids: List<Int>) = apiCall { api.unpinEnvs(ids) }

    private suspend fun apiCall(call: suspend () -> com.autopanel.core.model.ApiResponse<Unit>): Result<Unit> {
        return try {
            val res = call()
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "操作失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
