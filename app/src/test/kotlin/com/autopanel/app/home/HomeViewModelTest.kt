package com.autopanel.app.home

import com.autopanel.core.domain.DashboardRepository
import com.autopanel.core.model.DashboardOverview
import com.autopanel.core.model.DashboardSystem
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
}
