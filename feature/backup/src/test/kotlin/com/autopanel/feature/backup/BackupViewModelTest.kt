package com.autopanel.feature.backup

import androidx.lifecycle.SavedStateHandle
import com.autopanel.core.model.BackupModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var controller: FakeBackupWorkController
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        controller = FakeBackupWorkController()
        viewModel = BackupViewModel(controller, SavedStateHandle())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `export is enqueued with selected modules and observed progress`() = runTest(dispatcher) {
        viewModel.exportBackup("content://backup/export")
        assertEquals("content://backup/export", controller.exportUri)
        assertTrue(BackupModule.BASE.apiValue in controller.exportModules)

        controller.transfer.value = BackupWorkSnapshot(
            id = "export-1",
            kind = BackupWorkKind.EXPORT,
            status = BackupWorkStatus.RUNNING,
            operation = BackupOperation.EXPORTING,
            transferredBytes = 512,
            totalBytes = 1024
        )
        advanceUntilIdle()

        assertEquals(BackupOperation.EXPORTING, viewModel.uiState.value.operation)
        assertEquals(0.5f, viewModel.uiState.value.progress)
    }

    @Test
    fun `completed import waits for explicit restore confirmation`() = runTest(dispatcher) {
        viewModel.importBackup("content://backup/import", 2048)
        controller.transfer.value = BackupWorkSnapshot(
            id = "import-1",
            kind = BackupWorkKind.IMPORT,
            status = BackupWorkStatus.SUCCEEDED,
            operation = BackupOperation.IMPORTING
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBusy)
        assertTrue(viewModel.uiState.value.showRestoreConfirmation)
        assertFalse(controller.restoreStarted)

        viewModel.confirmRestore()
        assertTrue(controller.restoreStarted)
        assertEquals(BackupOperation.ACTIVATING_RESTORE, viewModel.uiState.value.operation)
    }

    @Test
    fun `oversized import is rejected before work is enqueued`() = runTest(dispatcher) {
        viewModel.onMaxImportSizeChanged("1")
        viewModel.importBackup("content://backup/large", 2L * 1024L * 1024L)

        assertNull(controller.importUri)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `cancel delegates to persistent work controller`() {
        viewModel.exportBackup("content://backup/export")
        viewModel.cancelTransfer()

        assertTrue(controller.cancelled)
    }
}
private class FakeBackupWorkController : BackupWorkController {
    override val transfer = MutableStateFlow<BackupWorkSnapshot?>(null)
    override val restore = MutableStateFlow<BackupWorkSnapshot?>(null)
    var exportUri: String? = null
    var exportModules: Set<String> = emptySet()
    var importUri: String? = null
    var restoreStarted = false
    var cancelled = false

    override fun startExport(destinationUri: String, modules: Set<String>) {
        exportUri = destinationUri
        exportModules = modules
    }

    override fun startImport(sourceUri: String, contentLength: Long?, maxBytes: Long) {
        importUri = sourceUri
    }

    override fun cancelTransfer() {
        cancelled = true
    }

    override fun startRestore() {
        restoreStarted = true
    }
}
