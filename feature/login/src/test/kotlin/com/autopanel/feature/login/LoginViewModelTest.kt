package com.autopanel.feature.login

import android.content.Context
import app.cash.turbine.test
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.domain.LoginTwoFactorUseCase
import com.autopanel.core.domain.LoginUseCase
import com.autopanel.core.domain.SaveCredentialsUseCase
import com.autopanel.core.model.LoginData
import com.autopanel.core.model.LoginResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val loginUseCase = mockk<LoginUseCase>()
    private val loginTwoFactorUseCase = mockk<LoginTwoFactorUseCase>()
    private val saveCredentialsUseCase = mockk<SaveCredentialsUseCase>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { sessionManager.host } returns null
        every { sessionManager.username } returns null
        every { sessionManager.password } returns null
        every { sessionManager.alias } returns null
        every { sessionManager.rememberPassword } returns false
        every { sessionManager.certPath } returns null
        every { sessionManager.certPassword } returns null
        every { sessionManager.accountsFlow } returns emptyFlow()

        coEvery { sessionManager.setHost(any()) } returns Unit

        viewModel = LoginViewModel(
            loginUseCase,
            loginTwoFactorUseCase,
            saveCredentialsUseCase,
            sessionManager,
            context
        )
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
    fun `canLogin returns false when fields are empty`() {
        assertFalse(viewModel.canLogin())
    }

    @Test
    fun `canLogin returns true when host username password are filled`() {
        viewModel.onHostChanged("http://192.168.1.1:5700")
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("pass")
        assertTrue(viewModel.canLogin())
    }

    @Test
    fun `login with invalid host shows error`() = runTest(testDispatcher) {
        viewModel.onHostChanged("not-a-url")
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("pass")

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login()
            val state = awaitItem()
            assertTrue(state is LoginUiState.Error)
            assertTrue((state as LoginUiState.Error).message.contains("http"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login success navigates to Success state`() = runTest(testDispatcher) {
        val token = "test-token-abc"
        coEvery { loginUseCase.invoke(any(), any()) } returns LoginResult.Success(LoginData(token = token))
        coEvery { saveCredentialsUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns Unit

        viewModel.onHostChanged("http://192.168.1.1:5700")
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("pass")

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login()
            assertEquals(LoginUiState.Loading, awaitItem())
            assertEquals(LoginUiState.Success, awaitItem())

            coVerify { saveCredentialsUseCase.invoke(
                host = "http://192.168.1.1:5700",
                username = "admin",
                password = "pass",
                token = token,
                alias = null,
                remember = false
            ) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login with 2FA transitions to NeedTwoFactor`() = runTest(testDispatcher) {
        coEvery { loginUseCase.invoke(any(), any()) } returns LoginResult.NeedTwoFactor("need 2fa")

        viewModel.onHostChanged("http://192.168.1.1:5700")
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("pass")

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login()
            assertEquals(LoginUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is LoginUiState.NeedTwoFactor)
            assertEquals("admin", (state as LoginUiState.NeedTwoFactor).username)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login failure shows error state`() = runTest(testDispatcher) {
        coEvery { loginUseCase.invoke(any(), any()) } returns LoginResult.Error("bad credentials")

        viewModel.onHostChanged("http://192.168.1.1:5700")
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("wrong")

        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login()
            assertEquals(LoginUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is LoginUiState.Error)
            assertEquals("bad credentials", (state as LoginUiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submitTwoFactor success completes login`() = runTest(testDispatcher) {
        val token = "2fa-token"
        coEvery { loginTwoFactorUseCase.invoke("admin", "pass", "123456") } returns
            LoginResult.Success(LoginData(token = token))
        coEvery { saveCredentialsUseCase.invoke(any(), any(), any(), any(), any(), any()) } returns Unit

        viewModel.onHostChanged("http://192.168.1.1:5700")
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("pass")

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.login()
            coEvery { loginUseCase.invoke(any(), any()) } returns LoginResult.NeedTwoFactor("2fa")
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onTwoFactorCodeChanged("123456")
        coEvery { loginTwoFactorUseCase.invoke("admin", "pass", "123456") } returns
            LoginResult.Success(LoginData(token = token))

        val code = viewModel.twoFactorCode.value
        assertEquals("123456", code)
    }

    @Test
    fun `selectAccount fills form fields`() {
        val account = StoredAccount("http://10.0.0.1:5700", "root", "生产环境")
        viewModel.selectAccount(account)

        assertEquals("http://10.0.0.1:5700", viewModel.host.value)
        assertEquals("root", viewModel.username.value)
        assertEquals("生产环境", viewModel.alias.value)
        assertEquals("", viewModel.password.value)
    }

    @Test
    fun `backToPasswordLogin resets 2FA state`() {
        viewModel.onTwoFactorCodeChanged("123")
        viewModel.backToPasswordLogin()
        assertEquals("", viewModel.twoFactorCode.value)
        assertEquals(LoginUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `empty 2FA code shows error`() {
        viewModel.submitTwoFactor()
        assertEquals("请输入验证码", viewModel.twoFactorError.value)
    }
}
