package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户信息
 */
@Serializable
data class UserInfo(
    val username: String = "",
    val avatar: String? = null,
    val title: String? = null,
    @SerialName("twoFactorActivated") val twoFactorActivated: Boolean = false
)

@Serializable
data class TwoFactorSetup(
    val secret: String = "",
    val url: String = ""
)
