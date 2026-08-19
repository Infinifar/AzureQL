package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 青龙 /api/system 返回的版本/系统信息 */
@Serializable
data class SystemInfo(
    val version: String? = null,
    @SerialName("isInitialized") val isInitialized: Boolean? = null,
    @SerialName("publishTime") val publishTime: Long? = null,
    val branch: String? = null,
    @SerialName("changeLog") val changeLog: String? = null,
    @SerialName("changeLogLink") val changeLogLink: String? = null,
    // 旧版青龙字段（兼容）
    @SerialName("os_type") val osType: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("cpu_usage") val cpuUsage: Double? = null,
    @SerialName("mem_total") val memTotal: String? = null,
    @SerialName("mem_usage") val memUsage: String? = null,
    @SerialName("disk_total") val diskTotal: String? = null,
    @SerialName("disk_usage") val diskUsage: String? = null,
    @SerialName("node_version") val nodeVersion: String? = null,
    @SerialName("npm_version") val npmVersion: String? = null
)
