package com.autopanel.core.domain

import com.autopanel.core.model.LoginRequest
import com.autopanel.core.model.LoginResult
import com.autopanel.core.model.TwoFactorRequest

interface AuthRepository {
    suspend fun login(request: LoginRequest): LoginResult
    suspend fun loginTwoFactor(request: TwoFactorRequest): LoginResult
    suspend fun logout(): Result<Unit>
    suspend fun saveCredentials(
        host: String,
        username: String,
        password: String,
        token: String,
        alias: String? = null,
        remember: Boolean = false
    )
    suspend fun getToken(): String?
    suspend fun getHost(): String?
    suspend fun clearCredentials()
}
