package com.autopanel.core.model

import kotlinx.serialization.Serializable

/**
 * 统一 API 响应包装
 */
@Serializable
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null
)
