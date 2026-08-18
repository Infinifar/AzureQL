package com.qinglong.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/dashboard/overview */
@Serializable
data class DashboardOverview(
    val total: Int? = null,
    val enabled: Int? = null,
    val disabled: Int? = null,
    @SerialName("todayRuns") val todayRuns: Int? = null,
    @SerialName("todaySuccess") val todaySuccess: Int? = null,
    @SerialName("todayFail") val todayFail: Int? = null,
    @SerialName("successRate") val successRate: String? = null,
    @SerialName("avgTime") val avgTime: Int? = null
)

/** GET /api/dashboard/system */
@Serializable
data class DashboardSystem(
    val platform: String? = null,
    val uptime: Long? = null,
    @SerialName("memTotal") val memTotal: Long? = null,
    @SerialName("memFree") val memFree: Long? = null,
    @SerialName("memUsagePercent") val memUsagePercent: String? = null,
    @SerialName("heapUsed") val heapUsed: Int? = null,
    @SerialName("heapTotal") val heapTotal: Int? = null,
    @SerialName("loadAvg") val loadAvg: List<Double>? = null,
    val cpus: Int? = null
)

/** GET /api/dashboard/runtime */
@Serializable
data class DashboardRuntime(
    @SerialName("runningCount") val runningCount: Int? = null,
    @SerialName("queuedCount") val queuedCount: Int? = null,
    val running: List<DashboardRunningTask>? = null
)

@Serializable
data class DashboardRunningTask(
    @SerialName("instanceId") val instanceId: Int? = null,
    val id: Int? = null,
    val name: String? = null,
    val pid: Int? = null,
    val elapsed: Int? = null,
    @SerialName("logPath") val logPath: String? = null
)
