package com.autopanel.core.data.session

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.LoginData
import com.autopanel.core.model.LoginRequest
import com.autopanel.core.model.TwoFactorRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SavedAccountSwitcherTest {
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val retrofitClient = mockk<AutoPanelRetrofitClient>(relaxed = true)
    private val api = mockk<AutoPanelApiService>()
    private val operationGuard = mockk<AccountOperationGuard>()
    private lateinit var switcher: SavedAccountSwitcher

    private val account = StoredAccount(
        host = "https://panel.example.com",
        username = "admin",
        alias = "Production",
        certPath = "/private/client.p12",
        customCaPath = "/private/ca.pem"
    )

    @Before
    fun setUp() {
        switcher = SavedAccountSwitcher(sessionManager, retrofitClient, operationGuard)
        coEvery { operationGuard.canChangeActiveAccount() } returns true
        coEvery { sessionManager.getRememberedCredential(account) } returns "remembered-password"
        coEvery { sessionManager.getRememberedCertificatePassword(account) } returns "cert-password"
        coEvery { retrofitClient.createApiService(any(), any()) } returns api
        every { retrofitClient.cancelAllRequests() } returns Unit
    }

    @Test
    fun `successful switch commits only after candidate login succeeds`() = runTest {
        val profile = slot<SessionSnapshot>()
        coEvery {
            retrofitClient.createApiService(account.host, capture(profile))
        } returns api
        coEvery {
            api.login(LoginRequest("admin", "remembered-password"))
        } returns ApiResponse(code = 200, data = LoginData(token = "new-token"))

        val result = switcher.switch(account)

        assertEquals(SavedAccountSwitchResult.Success, result)
        assertEquals(account.certPath, profile.captured.certPath)
        assertEquals("cert-password", profile.captured.certPassword)
        assertEquals(account.customCaPath, profile.captured.customCaPath)
        coVerify(exactly = 1) {
            sessionManager.activateStoredAccount(
                account,
                "remembered-password",
                "cert-password",
                "new-token"
            )
        }
    }

    @Test
    fun `failed candidate login leaves active session untouched`() = runTest {
        coEvery {
            api.login(LoginRequest("admin", "remembered-password"))
        } returns ApiResponse(code = 401, message = "invalid credentials")

        val result = switcher.switch(account)

        assertTrue(result is SavedAccountSwitchResult.Failure)
        coVerify(exactly = 0) { sessionManager.activateStoredAccount(any(), any(), any(), any()) }
    }

    @Test
    fun `password account can complete two factor challenge`() = runTest {
        coEvery {
            api.login(LoginRequest("admin", "remembered-password"))
        } returns ApiResponse(code = 420, message = "2FA required")
        coEvery {
            api.loginTwoFactor(TwoFactorRequest("admin", "remembered-password", "123456"))
        } returns ApiResponse(code = 200, data = LoginData(token = "two-factor-token"))

        assertTrue(switcher.switch(account) is SavedAccountSwitchResult.NeedsTwoFactor)
        assertEquals(SavedAccountSwitchResult.Success, switcher.switch(account, "123456"))
        coVerify(exactly = 1) {
            sessionManager.activateStoredAccount(
                account,
                "remembered-password",
                "cert-password",
                "two-factor-token"
            )
        }
    }
}
