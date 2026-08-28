package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.domain.DashboardRepository
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardRuntime
import com.autopanel.core.model.DashboardSystem
import com.autopanel.core.model.DashboardTrendItem
import com.autopanel.core.model.DashboardTopCountItem
import com.autopanel.core.model.DashboardTopTimeItem
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>,
    private val responseCache: ResponseCache
) : DashboardRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getCachedOverview(): DashboardOverview? =
        responseCache.read(ResponseCache.DASHBOARD_OVERVIEW, DashboardOverview.serializer())

    override suspend fun getCachedTrend(days: Int): List<DashboardTrendItem>? =
        responseCache.read(
            ResponseCache.DASHBOARD_TREND_PREFIX + days,
            ListSerializer(DashboardTrendItem.serializer())
        )

    override suspend fun getCachedSystem(): DashboardSystem? =
        responseCache.read(ResponseCache.DASHBOARD_SYSTEM, DashboardSystem.serializer())

    override suspend fun getCachedRuntime(): DashboardRuntime? =
        responseCache.read(ResponseCache.DASHBOARD_RUNTIME, DashboardRuntime.serializer())

    override suspend fun getCachedTopCount(): List<DashboardTopCountItem>? =
        responseCache.read(
            ResponseCache.DASHBOARD_TOP_COUNT,
            ListSerializer(DashboardTopCountItem.serializer())
        )

    override suspend fun getCachedTopTime(): List<DashboardTopTimeItem>? =
        responseCache.read(
            ResponseCache.DASHBOARD_TOP_TIME,
            ListSerializer(DashboardTopTimeItem.serializer())
        )

    override suspend fun getOverview(): Result<DashboardOverview> {
        return try {
            val res = api.getDashboardOverview()
            if (res.code == 200) {
                val value = res.data ?: DashboardOverview()
                responseCache.write(
                    ResponseCache.DASHBOARD_OVERVIEW,
                    DashboardOverview.serializer(),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取总览失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getTrend(days: Int): Result<List<DashboardTrendItem>> {
        return try {
            val res = api.getDashboardTrend(days)
            if (res.code == 200) {
                val value = res.data.orEmpty()
                responseCache.write(
                    ResponseCache.DASHBOARD_TREND_PREFIX + days,
                    ListSerializer(DashboardTrendItem.serializer()),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取任务趋势失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getTopCount(): Result<List<DashboardTopCountItem>> {
        return try {
            val res = api.getDashboardTopCount()
            if (res.code == 200) {
                val value = res.data.orEmpty()
                responseCache.write(
                    ResponseCache.DASHBOARD_TOP_COUNT,
                    ListSerializer(DashboardTopCountItem.serializer()),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取今日执行次数排行失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getTopTime(): Result<List<DashboardTopTimeItem>> {
        return try {
            val res = api.getDashboardTopTime()
            if (res.code == 200) {
                val value = res.data.orEmpty()
                responseCache.write(
                    ResponseCache.DASHBOARD_TOP_TIME,
                    ListSerializer(DashboardTopTimeItem.serializer()),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取今日耗时排行失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getSystem(): Result<DashboardSystem> {
        return try {
            val res = api.getDashboardSystem()
            if (res.code == 200) {
                val value = res.data ?: DashboardSystem()
                responseCache.write(
                    ResponseCache.DASHBOARD_SYSTEM,
                    DashboardSystem.serializer(),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取系统状态失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getRuntime(): Result<DashboardRuntime> {
        return try {
            val res = api.getDashboardRuntime()
            if (res.code == 200) {
                val value = res.data ?: DashboardRuntime()
                responseCache.write(
                    ResponseCache.DASHBOARD_RUNTIME,
                    DashboardRuntime.serializer(),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取运行状态失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun reloadSystem(): Result<Unit> {
        return try {
            val res = api.reloadSystem()
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "重启失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
