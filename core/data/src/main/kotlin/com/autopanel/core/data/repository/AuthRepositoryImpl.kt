package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.domain.AuthRepository
import com.autopanel.core.model.LoginRequest
import com.autopanel.core.model.LoginResult
import com.autopanel.core.model.TwoFactorRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val retrofitClient: AutoPanelRetrofitClient,
    private val sessionManager: SessionManager
) : AuthRepository {

    override suspend fun login(request: LoginRequest): LoginResult {
        return try {
            val host = sessionManager.host
                ?: return LoginResult.Error("服务器地址未设置")
            val service = retrofitClient.createApiService(host)
            val response = service.login(request)

            when (response.code) {
                200 -> {
                    val data = response.data
                    if (data != null && data.token != null) {
                        LoginResult.Success(data)
                    } else {
                        LoginResult.Error("登录响应缺少 token")
                    }
                }
                420 -> LoginResult.NeedTwoFactor(response.message ?: "需要两步验证")
                else -> LoginResult.Error(response.message ?: "登录失败 (${response.code})")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "网络请求失败")
        }
    }

    override suspend fun loginTwoFactor(request: TwoFactorRequest): LoginResult {
        return try {
            val host = sessionManager.host
                ?: return LoginResult.Error("服务器地址未设置")
            val service = retrofitClient.createApiService(host)
            val response = service.loginTwoFactor(request)

            when (response.code) {
                200 -> {
                    val data = response.data
                    if (data != null && data.token != null) {
                        LoginResult.Success(data)
                    } else {
                        LoginResult.Error("验证成功但缺少 token")
                    }
                }
                else -> LoginResult.Error(response.message ?: "两步验证失败 (${response.code})")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "网络请求失败")
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            val host = sessionManager.host ?: return Result.failure(Exception("服务器地址未设置"))
            val service = retrofitClient.createApiService(host)
            val response = service.logout()
            if (response.code == 200) {
                sessionManager.clearSession()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "登出失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveCredentials(
        host: String,
        username: String,
        password: String,
        token: String,
        alias: String?,
        remember: Boolean
    ) {
        sessionManager.saveSession(host, username, password, token, alias, remember)
    }

    override suspend fun getToken(): String? = sessionManager.token
    override suspend fun getHost(): String? = sessionManager.host
    override suspend fun clearCredentials() { sessionManager.clearSession() }
}
