package com.autopanel.core.model

import kotlinx.serialization.Serializable

/**
 * 用户信息
 */
@Serializable
data class UserInfo(
    val username: String = "",
    val avatar: String? = null,
    val title: String? = null
)
