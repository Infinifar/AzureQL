package com.autopanel.feature.dependency

import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(DepEvent.Message("axios重装任务已提交"), viewModel.events.first())
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
        assertEquals(DepEvent.Message("requests删除任务已提交"), viewModel.events.first())
    }

    @Test
    fun `reinstall polling keeps queued active and stops at installed terminal state`() = runTest(dispatcher) {
        val installedBefore = DependencyInfo(id = 42, name = "axios", status = DependencyStatus.INSTALLED)
        val queued = installedBefore.copy(status = DependencyStatus.QUEUED)
        val installing = queued.copy(status = DependencyStatus.INSTALLING)
        val installed = queued.copy(status = DependencyStatus.INSTALLED)
        coEvery { repository.getDependencies(any(), any()) } returnsMany listOf(
            Result.success(listOf(installedBefore)),
            Result.success(listOf(installing)),
            Result.success(listOf(installed))
        )
        coEvery { repository.reinstallDependencies(listOf(42)) } returns Result.success(listOf(queued))
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestReinstall(installedBefore)
        viewModel.confirmReinstall()
        advanceUntilIdle()

        assertEquals(DependencyStatus.INSTALLED, viewModel.uiState.value.deps.single().status)
        assertFalse(viewModel.uiState.value.isMutating)
        coVerify(exactly = 1) { repository.reinstallDependencies(listOf(42)) }
    }

    @Test
    fun `reinstall polling stops only after server reports install failure`() = runTest(dispatcher) {
        val installedBefore = DependencyInfo(id = 43, name = "broken", status = DependencyStatus.INSTALLED)
        val queued = installedBefore.copy(status = DependencyStatus.QUEUED)
        val installing = queued.copy(status = DependencyStatus.INSTALLING)
        val failed = queued.copy(status = DependencyStatus.INSTALL_FAILED)
        coEvery { repository.getDependencies(any(), any()) } returnsMany listOf(
            Result.success(listOf(installedBefore)),
            Result.success(listOf(installing)),
            Result.success(listOf(failed))
        )
        coEvery { repository.reinstallDependencies(listOf(43)) } returns Result.success(listOf(queued))
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestReinstall(installedBefore)
        viewModel.confirmReinstall()
        advanceUntilIdle()

        assertEquals(DependencyStatus.INSTALL_FAILED, viewModel.uiState.value.deps.single().status)
        coVerify(exactly = 3) { repository.getDependencies(any(), any()) }
    }

    @Test
    fun `delete polling preserves server delete failure as terminal state`() = runTest(dispatcher) {
        val installed = DependencyInfo(id = 44, name = "undeletable", status = DependencyStatus.INSTALLED)
        val deleting = installed.copy(status = DependencyStatus.UNINSTALLING)
        val failed = installed.copy(status = DependencyStatus.UNINSTALL_FAILED)
        coEvery { repository.getDependencies(any(), any()) } returnsMany listOf(
            Result.success(listOf(installed)),
            Result.success(listOf(deleting)),
            Result.success(listOf(failed))
        )
        coEvery { repository.deleteDependencies(listOf(44)) } returns Result.success(listOf(deleting))
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDelete(installed)
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(DependencyStatus.UNINSTALL_FAILED, viewModel.uiState.value.deps.single().status)
        coVerify(exactly = 3) { repository.getDependencies(any(), any()) }
    }

    @Test
    fun `mutation gate prevents a second item request while first call is running`() = runTest(dispatcher) {
        val first = DependencyInfo(id = 1, name = "first")
        val second = DependencyInfo(id = 2, name = "second")
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.reinstallDependencies(listOf(1)) } coAnswers {
            gate.await()
            Result.success(listOf(first.copy(status = DependencyStatus.QUEUED)))
        }
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestReinstall(first)
        viewModel.confirmReinstall()
        runCurrent()
        assertTrue(viewModel.uiState.value.isMutating)

        viewModel.requestDelete(second)
        assertNull(viewModel.uiState.value.confirmDelete)

        gate.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.deleteDependencies(listOf(2)) }
    }

    @Test
    fun `failed reinstall clears mutation gate and reports failure without polling`() = runTest(dispatcher) {
        val dependency = DependencyInfo(id = 9, name = "broken")
        coEvery { repository.reinstallDependencies(listOf(9)) } returns
            Result.failure(IllegalStateException("queue rejected"))
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestReinstall(dependency)
        viewModel.confirmReinstall()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMutating)
        assertEquals(DepEvent.Message("重新安装失败: queue rejected"), viewModel.events.first())
        coVerify(exactly = 1) { repository.getDependencies(any(), any()) }
    }

    @Test
    fun `queued dependency rejects repeat item operations`() = runTest(dispatcher) {
        val queued = DependencyInfo(id = 11, name = "queued", status = DependencyStatus.QUEUED)
        coEvery { repository.getDependencies(any(), any()) } returns Result.success(listOf(queued))
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.requestReinstall(queued)
        viewModel.requestDelete(queued)

        assertNull(viewModel.uiState.value.confirmReinstall)
        assertNull(viewModel.uiState.value.confirmDelete)
        coVerify(exactly = 0) { repository.reinstallDependencies(any()) }
        coVerify(exactly = 0) { repository.deleteDependencies(any()) }
    }

    @Test
    fun `batch operation excludes dependencies that are already active`() = runTest(dispatcher) {
        val queued = DependencyInfo(id = 11, name = "queued", status = DependencyStatus.QUEUED)
        val installed = DependencyInfo(id = 12, name = "installed", status = DependencyStatus.INSTALLED)
        coEvery { repository.getDependencies(any(), any()) } returns Result.success(listOf(queued, installed))
        coEvery { repository.reinstallDependencies(listOf(12)) } returns Result.success(
            listOf(installed.copy(status = DependencyStatus.QUEUED))
        )
        val viewModel = DepViewModel(repository)
        advanceUntilIdle()

        viewModel.batchReinstall(listOf(11, 12))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.reinstallDependencies(listOf(12)) }
        coVerify(exactly = 0) { repository.reinstallDependencies(listOf(11, 12)) }
    }
}
