package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.data.session.AuthMode
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.domain.AuthRepository
import com.autopanel.core.model.LoginRequest
import com.autopanel.core.model.LoginResult
import com.autopanel.core.model.TwoFactorRequest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val retrofitClient: AutoPanelRetrofitClient,
    private val sessionManager: SessionManager
) : AuthRepository {

    override suspend fun login(request: LoginRequest): LoginResult {
        return try {
            val host = sessionManager.getSession().host
                ?: return LoginResult.Error("服务器地址未设置")
            val service = retrofitClient.createApiService(host)
            service.login(request).toLoginResult(allowTwoFactor = true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LoginResult.Error(e.message ?: "网络请求失败")
        }
    }

    override suspend fun loginWithClientCredentials(
        clientId: String,
        clientSecret: String
    ): LoginResult {
        return try {
            val host = sessionManager.getSession().host
                ?: return LoginResult.Error("服务器地址未设置")
            retrofitClient.createApiService(host)
                .loginWithClientCredentials(clientId, clientSecret)
                .toLoginResult(allowTwoFactor = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LoginResult.Error(e.message ?: "网络请求失败")
        }
    }

    override suspend fun loginTwoFactor(request: TwoFactorRequest): LoginResult {
        return try {
            val host = sessionManager.getSession().host
                ?: return LoginResult.Error("服务器地址未设置")
            val service = retrofitClient.createApiService(host)
            service.loginTwoFactor(request).toLoginResult(allowTwoFactor = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            LoginResult.Error(e.message ?: "网络请求失败")
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val host = sessionManager.getSession().host
                ?: return Result.failure(Exception("服务器地址未设置"))
            val service = retrofitClient.createApiService(host)
            val response = service.logout()
            if (response.code == 200) {
                sessionManager.clearSession()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "登出失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun saveCredentials(
        host: String,
        username: String,
        password: String,
        token: String,
        alias: String?,
        remember: Boolean,
        allowInsecureHttp: Boolean,
        isClientCredentials: Boolean
    ) {
        sessionManager.saveSession(
            host = host,
            username = username,
            password = password,
            token = token,
            alias = alias,
            remember = remember,
            allowInsecureHttp = allowInsecureHttp,
            authMode = if (isClientCredentials) {
                AuthMode.CLIENT_CREDENTIALS
            } else {
                AuthMode.PASSWORD
            }
        )
    }

    override suspend fun getToken(): String? = sessionManager.getSession().token
    override suspend fun getHost(): String? = sessionManager.getSession().host
    override suspend fun clearCredentials() { sessionManager.clearSession() }

    private fun com.autopanel.core.model.ApiResponse<com.autopanel.core.model.LoginData>.toLoginResult(
        allowTwoFactor: Boolean
    ): LoginResult {
        val payload = data
        return when (code) {
        200 -> if (payload?.token != null) {
            LoginResult.Success(payload)
        } else {
            LoginResult.Error("登录响应缺少 token")
        }
        420 -> if (allowTwoFactor) {
            LoginResult.NeedTwoFactor(message ?: "需要两步验证")
        } else {
            LoginResult.Error(message ?: "此登录方式不支持两步验证")
        }
        else -> LoginResult.Error(message ?: "登录失败 ($code)")
        }
    }
}
