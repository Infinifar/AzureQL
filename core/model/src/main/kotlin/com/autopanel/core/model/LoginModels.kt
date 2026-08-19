package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 登录请求体
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 两步验证请求体
 */
@Serializable
data class TwoFactorRequest(
    val username: String,
    val password: String,
    val code: String
)

/**
 * 登录成功后的数据载荷（ApiResponse.data 的内容）。
 * 不含 code/message —— 这些由 ApiResponse 统一处理。
 */
@Serializable
data class LoginData(
    val token: String? = null,
    @SerialName("lastip") val lastIp: String? = null,
    @SerialName("lastaddr") val lastAddr: String? = null,
    @SerialName("lastlogon") val lastLogon: Long? = null,
    val retries: Int? = null,
    val platform: String? = null
)

/**
 * 登录结果密封类 —— 清晰表达三种可能结果
 */
sealed class LoginResult {
    /** 登录成功，携带 token 等数据 */
    data class Success(val data: LoginData) : LoginResult()

    /** 需要两步验证 */
    data class NeedTwoFactor(val message: String) : LoginResult()

    /** 登录失败 */
    data class Error(val message: String) : LoginResult()
}
