package com.qinglong.core.data.repository

import com.qinglong.core.data.remote.QLApiService
import com.qinglong.core.domain.DashboardRepository
import com.qinglong.core.model.DashboardOverview
import com.qinglong.core.model.DashboardRuntime
import com.qinglong.core.model.DashboardSystem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val api: QLApiService
) : DashboardRepository {

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
}
