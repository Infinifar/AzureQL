package com.autopanel.feature.backup

import com.autopanel.core.domain.BackupRepository
import com.autopanel.core.model.BackupModule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.thirdArg
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<BackupRepository>()
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = BackupViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `export reports progress and returns to idle`() = runTest(dispatcher) {
        coEvery {
            repository.exportBackup(any(), any(), any())
        } coAnswers {
            thirdArg<(Long, Long?) -> Unit>().invoke(1024, 1024)
            Result.success(Unit)
        }

        viewModel.exportBackup(ByteArrayOutputStream())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals(0, viewModel.uiState.value.transferredBytes)
        assertNull(viewModel.uiState.value.totalBytes)
    }

    @Test
    fun `oversized import is rejected before repository call`() = runTest(dispatcher) {
        val source = mockk<InputStream>(relaxed = true)
        viewModel.onMaxImportSizeChanged("1")

        viewModel.importBackup(source, 2L * 1024L * 1024L)

        verify { source.close() }
        coVerify(exactly = 0) { repository.importBackup(any(), any(), any()) }
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `cancel stops active export`() = runTest(dispatcher) {
        coEvery {
            repository.exportBackup(any(), any(), any())
        } coAnswers { awaitCancellation() }
        val destination = mockk<OutputStream>(relaxed = true)

        viewModel.exportBackup(destination)
        runCurrent()
        viewModel.cancelTransfer()
        runCurrent()

        assertFalse(viewModel.uiState.value.isBusy)
        verify { destination.close() }
    }

    @Test
    fun `restore completes after service becomes healthy`() = runTest(dispatcher) {
        coEvery { repository.activateImportedBackup() } returns Result.success(Unit)
        coEvery { repository.healthCheck() } returnsMany listOf(
            Result.failure(Exception("restarting")),
            Result.success(Unit)
        )

        viewModel.confirmRestore()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        coVerify(exactly = 2) { repository.healthCheck() }
    }
}
