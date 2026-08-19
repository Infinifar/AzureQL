package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.model.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryImplTest {

    private val retrofitClient = mockk<AutoPanelRetrofitClient>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val apiService = mockk<AutoPanelApiService>()

    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        repository = AuthRepositoryImpl(retrofitClient, sessionManager)
    }

    @Test
    fun `login with no host returns Error`() = runTest {
        every { sessionManager.host } returns null

        val result = repository.login(LoginRequest("admin", "pass"))
        assertTrue(result is LoginResult.Error)
        assertTrue((result as LoginResult.Error).message.contains("服务器地址"))
    }

    @Test
    fun `login 420 returns NeedTwoFactor`() = runTest {
        every { sessionManager.host } returns "http://localhost:5700"
        every { retrofitClient.createApiService(any()) } returns apiService
        coEvery { apiService.login(any()) } returns ApiResponse(code = 420, message = "需要两步验证")

        val result = repository.login(LoginRequest("admin", "pass"))
        assertTrue(result is LoginResult.NeedTwoFactor)
    }

    @Test
    fun `login 200 with token returns Success`() = runTest {
        every { sessionManager.host } returns "http://localhost:5700"
        every { retrofitClient.createApiService(any()) } returns apiService
        coEvery { apiService.login(any()) } returns ApiResponse(
            code = 200,
            data = LoginData(token = "test-token")
        )

        val result = repository.login(LoginRequest("admin", "pass"))
        assertTrue(result is LoginResult.Success)
        assertEquals("test-token", (result as LoginResult.Success).data.token)
    }

    @Test
    fun `login 200 without token returns Error`() = runTest {
        every { sessionManager.host } returns "http://localhost:5700"
        every { retrofitClient.createApiService(any()) } returns apiService
        coEvery { apiService.login(any()) } returns ApiResponse(
            code = 200,
            data = LoginData(token = null)
        )

        val result = repository.login(LoginRequest("admin", "pass"))
        assertTrue(result is LoginResult.Error)
    }

    @Test
    fun `twoFactor login 200 with token returns Success`() = runTest {
        every { sessionManager.host } returns "http://localhost:5700"
        every { retrofitClient.createApiService(any()) } returns apiService
        coEvery { apiService.loginTwoFactor(any()) } returns ApiResponse(
            code = 200,
            data = LoginData(token = "2fa-token")
        )

        val result = repository.loginTwoFactor(TwoFactorRequest("admin", "pass", "123456"))
        assertTrue(result is LoginResult.Success)
        assertEquals("2fa-token", (result as LoginResult.Success).data.token)
    }
}
