package com.autopanel.app.home

import com.autopanel.core.domain.DashboardRepository
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardSystem
import com.autopanel.core.model.DashboardRuntime
import com.autopanel.core.model.DashboardRunningTask
import com.autopanel.core.model.DashboardTopCountItem
import com.autopanel.core.model.DashboardTopTimeItem
import com.autopanel.core.model.DashboardTrendItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<DashboardRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getOverview() } returns Result.success(DashboardOverview())
        coEvery { repository.getSystem() } returns Result.success(DashboardSystem())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `home requests and exposes official seven day trend`() = runTest(dispatcher) {
        val trend = listOf(
            DashboardTrendItem(date = "08-20", total = 3, success = 2, fail = 1)
        )
        coEvery { repository.getTrend(7) } returns Result.success(trend)

        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTrend(7) }
        assertEquals(trend, viewModel.uiState.value.trend)
    }

    @Test
    fun `task details load only after overview card is opened`() = runTest(dispatcher) {
        coEvery { repository.getTrend(7) } returns Result.success(emptyList())
        val runtime = DashboardRuntime(
            runningCount = 1,
            queuedCount = 2,
            running = listOf(DashboardRunningTask(id = 7, name = "签到", elapsed = 16))
        )
        val topCount = listOf(
            DashboardTopCountItem(rank = 1, name = "签到", runCount = 3, avgTime = 16_000)
        )
        val topTime = listOf(
            DashboardTopTimeItem(rank = 1, name = "大任务", avgTime = 265_000, maxTime = 365_000)
        )
        coEvery { repository.getRuntime() } returns Result.success(runtime)
        coEvery { repository.getTopCount() } returns Result.success(topCount)
        coEvery { repository.getTopTime() } returns Result.success(topTime)
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getRuntime() }
        coVerify(exactly = 0) { repository.getTopCount() }
        coVerify(exactly = 0) { repository.getTopTime() }

        viewModel.showTaskDetails()
        advanceUntilIdle()

        assertEquals(runtime, viewModel.uiState.value.runtime)
        assertEquals(topCount, viewModel.uiState.value.topCount)
        assertEquals(topTime, viewModel.uiState.value.topTime)
        coVerify(exactly = 1) { repository.getRuntime() }
        coVerify(exactly = 1) { repository.getTopCount() }
        coVerify(exactly = 1) { repository.getTopTime() }
    }
}
