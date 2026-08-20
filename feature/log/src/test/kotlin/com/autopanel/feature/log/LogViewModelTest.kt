package com.autopanel.feature.log

import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.LogFile
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<LogRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmed delete removes selected log and refreshes list`() = runTest(dispatcher) {
        val log = LogFile(title = "task.log", type = "file", parent = "2026-08")
        coEvery { repository.getLogFiles() } returnsMany listOf(
            Result.success(listOf(log)),
            Result.success(emptyList())
        )
        coEvery { repository.deleteLog(log) } returns Result.success(Unit)
        val viewModel = LogViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDelete(log)
        assertEquals(log, viewModel.uiState.value.confirmDelete)
        viewModel.confirmDelete()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteLog(log) }
        coVerify(exactly = 2) { repository.getLogFiles() }
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(emptyList<LogFile>(), viewModel.uiState.value.logs)
    }
}
