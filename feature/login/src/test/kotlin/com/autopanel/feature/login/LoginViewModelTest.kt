package com.autopanel.feature.login

import android.content.Context
import app.cash.turbine.test
import com.autopanel.core.data.session.AuthMode
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.SessionSnapshot
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.domain.LoginClientCredentialsUseCase
import com.autopanel.core.domain.LoginTwoFactorUseCase
import com.autopanel.core.domain.LoginUseCase
import com.autopanel.core.domain.SaveCredentialsUseCase
import com.autopanel.core.model.LoginData
import com.autopanel.core.model.LoginResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val loginUseCase = mockk<LoginUseCase>()
    private val loginTwoFactorUseCase = mockk<LoginTwoFactorUseCase>()
    private val loginClientCredentialsUseCase = mockk<LoginClientCredentialsUseCase>()
    private val saveCredentialsUseCase = mockk<SaveCredentialsUseCase>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { sessionManager.getSession() } returns SessionSnapshot()
        coEvery { sessionManager.getRememberedCredential(any()) } returns null
        coEvery { sessionManager.configureConnection(any(), any(), any(), any(), any()) } returns Unit
        everyAccounts()
        createViewModel()
        testDispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canLogin requires explicit consent for HTTP`() {
        fillPasswordForm("http://192.168.1.1:5700")
        assertFalse(viewModel.canLogin())

        viewModel.onAllowInsecureHttpChanged(true)
        assertTrue(viewModel.canLogin())
    }

    @Test
    fun `login with invalid host shows error`() = runTest(testDispatcher) {
        fillPasswordForm("not-a-url")

        viewModel.login()

        assertTrue(viewModel.uiState.value is LoginUiState.Error)
        assertTrue((viewModel.uiState.value as LoginUiState.Error).message.contains("http"))
    }

    @Test
    fun `login persists connection before first mTLS request`() = runTest(testDispatcher) {
        val certSession = SessionSnapshot(
            host = "https://panel.example.com",
            certPath = "/private/client.p12",
            certPassword = "old-password"
        )
        coEvery { sessionManager.getSession() } returns certSession
        createViewModel()
        testDispatcher.scheduler.runCurrent()
        viewModel.onCertPasswordChanged("new-password")
        fillPasswordForm("https://panel.example.com")
        coEvery { loginUseCase.invoke("admin", "pass") } returns
            LoginResult.Success(LoginData(token = "token"))
        coEvery { saveCredentialsUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            sessionManager.configureConnection(
                host = "https://panel.example.com",
                certPath = "/private/client.p12",
                certPassword = "new-password",
                customCaPath = null,
                allowInsecureHttp = false
            )
            loginUseCase.invoke("admin", "pass")
        }
        assertEquals(LoginUiState.Success, viewModel.uiState.value)
    }

    @Test
    fun `password login success saves encrypted credential inputs`() = runTest(testDispatcher) {
        coEvery { loginUseCase.invoke("admin", "pass") } returns
            LoginResult.Success(LoginData(token = "test-token"))
        coEvery { saveCredentialsUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        fillPasswordForm("https://panel.example.com")

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            saveCredentialsUseCase.invoke(
                host = "https://panel.example.com",
                username = "admin",
                password = "pass",
                token = "test-token",
                alias = null,
                remember = false,
                allowInsecureHttp = false,
                isClientCredentials = false
            )
        }
        assertEquals(LoginUiState.Success, viewModel.uiState.value)
    }

    @Test
    fun `client credentials login is connected to official token endpoint use case`() = runTest(testDispatcher) {
        coEvery { loginClientCredentialsUseCase.invoke("client", "secret") } returns
            LoginResult.Success(LoginData(token = "app-token"))
        coEvery { saveCredentialsUseCase.invoke(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        viewModel.onHostChanged("https://panel.example.com")
        viewModel.onUseClientIdModeChanged(true)
        viewModel.onClientIdChanged("client")
        viewModel.onClientSecretChanged("secret")

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { loginClientCredentialsUseCase.invoke("client", "secret") }
        coVerify {
            saveCredentialsUseCase.invoke(
                host = "https://panel.example.com",
                username = "client",
                password = "secret",
                token = "app-token",
                alias = null,
                remember = false,
                allowInsecureHttp = false,
                isClientCredentials = true
            )
        }
    }

    @Test
    fun `login with 2FA transitions to NeedTwoFactor`() = runTest(testDispatcher) {
        coEvery { loginUseCase.invoke(any(), any()) } returns LoginResult.NeedTwoFactor("need 2fa")
        fillPasswordForm("https://panel.example.com")

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LoginUiState.NeedTwoFactor)
        assertEquals("admin", (state as LoginUiState.NeedTwoFactor).username)
    }

    @Test
    fun `login failure shows error state`() = runTest(testDispatcher) {
        coEvery { loginUseCase.invoke(any(), any()) } returns LoginResult.Error("bad credentials")
        fillPasswordForm("https://panel.example.com")

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LoginUiState.Error("bad credentials"), viewModel.uiState.value)
    }

    @Test
    fun `selectAccount fills form fields and remembered password`() = runTest(testDispatcher) {
        val account = StoredAccount("https://10.0.0.1:5700", "root", "生产环境")
        coEvery { sessionManager.getRememberedCredential(account) } returns "saved-password"

        viewModel.selectAccount(account)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://10.0.0.1:5700", viewModel.host.value)
        assertEquals("root", viewModel.username.value)
        assertEquals("生产环境", viewModel.alias.value)
        assertEquals("saved-password", viewModel.password.value)
        assertTrue(viewModel.rememberPassword.value)
        assertFalse(viewModel.isLoadingSavedCredential.value)
    }

    @Test
    fun `selectAccount restores client secret for client credentials mode`() = runTest(testDispatcher) {
        val account = StoredAccount(
            host = "https://panel.example.com",
            username = "client-id",
            authMode = AuthMode.CLIENT_CREDENTIALS
        )
        coEvery { sessionManager.getRememberedCredential(account) } returns "saved-secret"

        viewModel.selectAccount(account)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.useClientIdMode.value)
        assertEquals("client-id", viewModel.clientId.value)
        assertEquals("saved-secret", viewModel.clientSecret.value)
        assertEquals("", viewModel.password.value)
        assertTrue(viewModel.rememberPassword.value)
    }

    @Test
    fun `latest selected account owns delayed credential result`() = runTest(testDispatcher) {
        val delayed = StoredAccount("https://one.example.com", "first")
        val latest = StoredAccount("https://two.example.com", "second")
        val delayedSecret = CompletableDeferred<String?>()
        coEvery { sessionManager.getRememberedCredential(delayed) } coAnswers { delayedSecret.await() }
        coEvery { sessionManager.getRememberedCredential(latest) } returns "second-password"

        viewModel.selectAccount(delayed)
        testDispatcher.scheduler.runCurrent()
        viewModel.selectAccount(latest)
        testDispatcher.scheduler.advanceUntilIdle()
        delayedSecret.complete("stale-password")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://two.example.com", viewModel.host.value)
        assertEquals("second", viewModel.username.value)
        assertEquals("second-password", viewModel.password.value)
        assertFalse(viewModel.isLoadingSavedCredential.value)
    }

    @Test
    fun `empty 2FA code shows error`() {
        viewModel.submitTwoFactor()
        assertEquals("请输入验证码", viewModel.twoFactorError.value)
    }

    private fun createViewModel() {
        viewModel = LoginViewModel(
            loginUseCase = loginUseCase,
            loginTwoFactorUseCase = loginTwoFactorUseCase,
            loginClientCredentialsUseCase = loginClientCredentialsUseCase,
            saveCredentialsUseCase = saveCredentialsUseCase,
            sessionManager = sessionManager,
            context = context
        )
    }

    private fun fillPasswordForm(host: String) {
        viewModel.onHostChanged(host)
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("pass")
    }

    private fun everyAccounts() {
        io.mockk.every { sessionManager.accountsFlow } returns emptyFlow()
    }
}
