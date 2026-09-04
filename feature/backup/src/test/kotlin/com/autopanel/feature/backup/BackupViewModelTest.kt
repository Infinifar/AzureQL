package com.autopanel.feature.backup

import com.autopanel.core.model.BackupModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
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
        viewModel = BackupViewModel(controller)
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
            id = controller.importWorkId,
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

    @Test
    fun `import and restore expose all five durable stages`() = runTest(dispatcher) {
        viewModel.importBackup("content://backup/import", 1024)
        assertEquals(BackupOperation.VALIDATING_IMPORT, viewModel.uiState.value.operation)

        controller.transfer.value = BackupWorkSnapshot(
            id = controller.importWorkId,
            kind = BackupWorkKind.IMPORT,
            status = BackupWorkStatus.RUNNING,
            operation = BackupOperation.IMPORTING,
            transferredBytes = 512,
            totalBytes = 1024
        )
        advanceUntilIdle()
        assertEquals(BackupOperation.IMPORTING, viewModel.uiState.value.operation)

        controller.transfer.value = BackupWorkSnapshot(
            id = controller.importWorkId,
            kind = BackupWorkKind.IMPORT,
            status = BackupWorkStatus.SUCCEEDED,
            operation = BackupOperation.IMPORTING
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRestoreConfirmation)

        viewModel.confirmRestore()
        assertEquals(BackupOperation.ACTIVATING_RESTORE, viewModel.uiState.value.operation)

        val completion = async { viewModel.events.first() }
        runCurrent()
        controller.restore.value = BackupWorkSnapshot(
            id = controller.restoreWorkId,
            kind = BackupWorkKind.RESTORE,
            status = BackupWorkStatus.RUNNING,
            operation = BackupOperation.WAITING_FOR_SERVICE,
            healthCheckAttempt = 3
        )
        advanceUntilIdle()
        assertEquals(BackupOperation.WAITING_FOR_SERVICE, viewModel.uiState.value.operation)
        assertEquals(3, viewModel.uiState.value.healthCheckAttempt)

        controller.restore.value = BackupWorkSnapshot(
            id = controller.restoreWorkId,
            kind = BackupWorkKind.RESTORE,
            status = BackupWorkStatus.SUCCEEDED,
            operation = BackupOperation.WAITING_FOR_SERVICE
        )
        advanceUntilIdle()
        assertEquals(BackupEvent.RestoreCompleted, completion.await())
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `activation cannot be cancelled as a transfer`() = runTest(dispatcher) {
        viewModel.importBackup("content://backup/import", 1024)
        controller.transfer.value = BackupWorkSnapshot(
            id = controller.importWorkId,
            kind = BackupWorkKind.IMPORT,
            status = BackupWorkStatus.SUCCEEDED,
            operation = BackupOperation.IMPORTING
        )
        advanceUntilIdle()
        viewModel.confirmRestore()

        viewModel.cancelTransfer()

        assertFalse(controller.cancelled)
    }

    @Test
    fun `completed export is not replayed after the backup screen is recreated`() = runTest(dispatcher) {
        val completion = async { viewModel.events.first() }
        runCurrent()
        viewModel.exportBackup("content://backup/export")
        controller.transfer.value = BackupWorkSnapshot(
            id = controller.exportWorkId,
            kind = BackupWorkKind.EXPORT,
            status = BackupWorkStatus.SUCCEEDED,
            operation = BackupOperation.EXPORTING
        )
        advanceUntilIdle()
        assertEquals(BackupEvent.Message("备份已保存"), completion.await())

        val returnedViewModel = BackupViewModel(controller)
        val replay = async { returnedViewModel.events.first() }
        advanceUntilIdle()

        assertFalse(replay.isCompleted)
        replay.cancel()
    }

    @Test
    fun `failed export emits the worker message and returns to idle`() = runTest(dispatcher) {
        val failure = async { viewModel.events.first() }
        viewModel.exportBackup("content://backup/export")
        runCurrent()

        controller.transfer.value = BackupWorkSnapshot(
            id = controller.exportWorkId,
            kind = BackupWorkKind.EXPORT,
            status = BackupWorkStatus.FAILED,
            operation = BackupOperation.EXPORTING,
            message = "网络连接失败，请检查服务器状态和网络后重试"
        )
        advanceUntilIdle()

        assertEquals(
            BackupEvent.Message("网络连接失败，请检查服务器状态和网络后重试"),
            failure.await()
        )
        assertFalse(viewModel.uiState.value.isBusy)
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
    val exportWorkId = "export-work"
    val importWorkId = "import-work"
    val restoreWorkId = "restore-work"

    override fun startExport(destinationUri: String, modules: Set<String>): String {
        exportUri = destinationUri
        exportModules = modules
        return exportWorkId
    }

    override fun startImport(sourceUri: String, contentLength: Long?, maxBytes: Long): String {
        importUri = sourceUri
        return importWorkId
    }

    override fun cancelTransfer() {
        cancelled = true
    }

    override fun startRestore(): String {
        restoreStarted = true
        return restoreWorkId
    }
}
