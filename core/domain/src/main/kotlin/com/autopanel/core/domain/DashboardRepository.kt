package com.autopanel.core.domain

import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardRuntime
import com.autopanel.core.model.DashboardSystem

interface DashboardRepository {
    suspend fun getOverview(): Result<DashboardOverview>
    suspend fun getSystem(): Result<DashboardSystem>
    suspend fun getRuntime(): Result<DashboardRuntime>
    suspend fun reloadSystem(): Result<Unit>
}
