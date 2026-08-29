package com.autopanel.app

import com.autopanel.core.data.session.SessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val token = MutableStateFlow<String?>(null)
    private val darkMode = MutableStateFlow("dark")
    private val themeColor = MutableStateFlow<String?>("#FF089DAE")
    private val dynamicColor = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { sessionManager.tokenFlow } returns token
        every { sessionManager.darkModeFlow } returns darkMode
        every { sessionManager.themeColorFlow } returns themeColor
        every { sessionManager.dynamicColorFlow } returns dynamicColor
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startup becomes ready only with one complete local snapshot`() = runTest(dispatcher) {
        val viewModel = AppViewModel(sessionManager)

        assertFalse(viewModel.startupState.value.isReady)
        runCurrent()

        assertEquals(
            AppStartupState(
                isReady = true,
                isLoggedIn = false,
                darkMode = "dark",
                themeColor = "#FF089DAE",
                dynamicColor = true
            ),
            viewModel.startupState.value
        )
    }

    @Test
    fun `startup state stays eagerly current without a Compose collector`() = runTest(dispatcher) {
        val viewModel = AppViewModel(sessionManager)
        runCurrent()

        token.value = "token"
        darkMode.value = "light"
        runCurrent()

        assertTrue(viewModel.startupState.value.isLoggedIn)
        assertEquals("light", viewModel.startupState.value.darkMode)
    }
}
