package com.autopanel.feature.log

import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.SystemConfig
import com.autopanel.core.model.SystemLogContent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class SystemLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val configRepository = mockk<ConfigRepository>()
    private val logRepository = mockk<LogRepository>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `calendar exposes seven descending server days`() {
        assertEquals(
            listOf("2026-09-09", "2026-09-08", "2026-09-07", "2026-09-06", "2026-09-05", "2026-09-04", "2026-09-03"),
            recentSystemLogDays("Asia/Shanghai", LocalDate.of(2026, 9, 9))
        )
    }

    @Test
    fun `selecting a day lazily loads its system log`() = runTest(dispatcher) {
        coEvery { configRepository.getSystemConfig() } returns Result.success(SystemConfig(timezone = "Asia/Shanghai"))
        coEvery { logRepository.getSystemLog("2026-09-09", any()) } returns Result.success(
            SystemLogContent("server started", 2_000_000, truncated = true)
        )
        val viewModel = SystemLogViewModel(configRepository, logRepository)
        advanceUntilIdle()

        coVerify(exactly = 0) { logRepository.getSystemLog(any(), any()) }
        viewModel.showDay("2026-09-09")
        advanceUntilIdle()

        coVerify(exactly = 1) { logRepository.getSystemLog("2026-09-09", 1_048_576) }
        assertEquals("server started", viewModel.uiState.value.content)
        assertTrue(viewModel.uiState.value.truncated)
        assertFalse(viewModel.uiState.value.isLoadingContent)
    }
}
