package com.autopanel.core.data.repository

import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.DashboardTaskResultItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider

class DashboardRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val cache = mockk<ResponseCache>(relaxed = true)
    private val repository = DashboardRepositoryImpl(Provider { api }, cache)

    @Test
    fun `today successes preserve counts and deleted task marker`() = runTest {
        val tasks = listOf(
            DashboardTaskResultItem(
                id = 21,
                name = "任务#21",
                successCount = 3,
                deleted = true
            )
        )
        coEvery { api.getDashboardSuccesses() } returns ApiResponse(code = 200, data = tasks)

        assertEquals(tasks, repository.getTodaySuccesses().getOrThrow())
    }

    @Test
    fun `today failures preserve business error`() = runTest {
        coEvery { api.getDashboardFailures() } returns ApiResponse(code = 500, message = "query failed")

        val result = repository.getTodayFailures()

        assertTrue(result.isFailure)
        assertEquals("query failed", result.exceptionOrNull()?.message)
    }
}
