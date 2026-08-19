package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.DashboardRepository
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardRuntime
import com.autopanel.core.model.DashboardSystem
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>
) : DashboardRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getOverview(): Result<DashboardOverview> {
        return try {
            val res = api.getDashboardOverview()
            if (res.code == 200) Result.success(res.data ?: DashboardOverview())
            else Result.failure(Exception(res.message ?: "获取总览失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSystem(): Result<DashboardSystem> {
        return try {
            val res = api.getDashboardSystem()
            if (res.code == 200) Result.success(res.data ?: DashboardSystem())
            else Result.failure(Exception(res.message ?: "获取系统状态失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRuntime(): Result<DashboardRuntime> {
        return try {
            val res = api.getDashboardRuntime()
            if (res.code == 200) Result.success(res.data ?: DashboardRuntime())
            else Result.failure(Exception(res.message ?: "获取运行状态失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reloadSystem(): Result<Unit> {
        return try {
            val res = api.reloadSystem()
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "重启失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
