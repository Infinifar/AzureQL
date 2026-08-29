package com.autopanel.app.home

import com.autopanel.core.data.session.SessionManager
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    private val sessionManager = mockk<SessionManager>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { sessionManager.aliasFlow } returns flowOf(null)
        coEvery { repository.getCachedOverview() } returns null
        coEvery { repository.getCachedSystem() } returns null
        coEvery { repository.getCachedTrend(any()) } returns null
        coEvery { repository.getCachedRuntime() } returns null
        coEvery { repository.getCachedTopCount() } returns null
        coEvery { repository.getCachedTopTime() } returns null
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

        val viewModel = HomeViewModel(repository, sessionManager)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTrend(7) }
        assertEquals(trend, viewModel.uiState.value.trend)
    }

    @Test
    fun `cached overview remains visible when refresh fails`() = runTest(dispatcher) {
        val cached = DashboardOverview(total = 12, enabled = 8)
        coEvery { repository.getCachedOverview() } returns cached
        coEvery { repository.getTrend(7) } returns Result.failure(IllegalStateException("offline"))
        coEvery { repository.getOverview() } returns Result.failure(IllegalStateException("offline"))
        coEvery { repository.getSystem() } returns Result.failure(IllegalStateException("offline"))

        val viewModel = HomeViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertEquals(cached, viewModel.uiState.value.overview)
    }

    @Test
    fun `home title includes a nonblank saved server alias`() = runTest(dispatcher) {
        every { sessionManager.aliasFlow } returns flowOf("  生产环境  ")
        coEvery { repository.getTrend(7) } returns Result.success(emptyList())

        val viewModel = HomeViewModel(repository, sessionManager)
        advanceUntilIdle()

        assertEquals("生产环境", viewModel.uiState.value.serverAlias)
        assertEquals("AzureQL（生产环境）", formatHomeTitle(viewModel.uiState.value.serverAlias))
        assertEquals("AzureQL", formatHomeTitle("  "))
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
        val viewModel = HomeViewModel(repository, sessionManager)
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
