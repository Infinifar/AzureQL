package com.autopanel.core.domain

import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardRuntime
import com.autopanel.core.model.DashboardSystem
import com.autopanel.core.model.DashboardTrendItem
import com.autopanel.core.model.DashboardTopCountItem
import com.autopanel.core.model.DashboardTopTimeItem

interface DashboardRepository {
    suspend fun getOverview(): Result<DashboardOverview>
    suspend fun getTrend(days: Int = 7): Result<List<DashboardTrendItem>>
    suspend fun getTopCount(): Result<List<DashboardTopCountItem>>
    suspend fun getTopTime(): Result<List<DashboardTopTimeItem>>
    suspend fun getSystem(): Result<DashboardSystem>
    suspend fun getRuntime(): Result<DashboardRuntime>
    suspend fun reloadSystem(): Result<Unit>
}
