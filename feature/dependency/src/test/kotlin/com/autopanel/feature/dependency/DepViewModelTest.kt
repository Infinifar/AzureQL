package com.autopanel.feature.dependency

import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DepViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<DependencyRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getDependencies(any(), any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmed item reinstall submits only selected dependency`() = runTest(dispatcher) {
        val dependency = DependencyInfo(id = 42, name = "axios")
        coEvery { repository.reinstallDependencies(listOf(42)) } returns Result.success(
            listOf(dependency.copy(status = DependencyStatus.QUEUED))
        )
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestReinstall(dependency)
        assertEquals(dependency, viewModel.uiState.value.confirmReinstall)
        viewModel.confirmReinstall()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.reinstallDependencies(listOf(42)) }
        assertNull(viewModel.uiState.value.confirmReinstall)
        assertEquals("axios重装任务已提交", viewModel.uiState.value.successMessage)
    }

    @Test
    fun `confirmed long press delete removes only selected dependency`() = runTest(dispatcher) {
        val dependency = DependencyInfo(id = 7, name = "requests")
        coEvery { repository.deleteDependencies(listOf(7)) } returns Result.success(listOf(dependency))
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDelete(dependency)
        assertEquals(dependency, viewModel.uiState.value.confirmDelete)
        viewModel.confirmDelete()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteDependencies(listOf(7)) }
        assertNull(viewModel.uiState.value.confirmDelete)
        assertEquals("requests删除任务已提交", viewModel.uiState.value.successMessage)
    }
}
