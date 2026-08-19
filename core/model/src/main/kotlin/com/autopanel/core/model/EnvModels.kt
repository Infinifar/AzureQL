package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object EnvStatus {
    const val ENABLED = 0
    const val DISABLED = 1
}

/**
 * 环境变量（青龙 v2.17+ SQLite 后端，主键为数字 id）
 */
@Serializable
data class EnvInfo(
    val id: Int? = null,
    val name: String? = null,
    val value: String? = null,
    val remarks: String? = null,
    val timestamp: String? = null,
    val status: Int? = null,       // 0=enabled, 1=disabled
    val position: Double? = null,
    @SerialName("isPinned") val isPinned: Int? = null,   // 0=未置顶, 1=已置顶
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null
) {
    val statusText: String
        get() = if (status == EnvStatus.ENABLED) "已启用" else "已禁用"

    val pinned: Boolean get() = isPinned == 1
}

/** 创建环境变量（POST /api/envs，数组元素） */
@Serializable
data class EnvCreateRequest(
    val name: String,
    val value: String,
    val remarks: String? = null
)

/** 更新环境变量（PUT /api/envs） */
@Serializable
data class EnvUpdateRequest(
    val id: Int,
    val name: String,
    val value: String,
    val remarks: String? = null
)
