package com.qinglong.core.domain

import com.qinglong.core.model.DashboardOverview
import com.qinglong.core.model.DashboardRuntime
import com.qinglong.core.model.DashboardSystem

interface DashboardRepository {
    suspend fun getOverview(): Result<DashboardOverview>
    suspend fun getSystem(): Result<DashboardSystem>
    suspend fun getRuntime(): Result<DashboardRuntime>
}
