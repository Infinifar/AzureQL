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
    @SerialName("changeLogLink") val changeLogLink: String? = null
)
