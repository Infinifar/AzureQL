package com.autopanel.feature.backup

import com.autopanel.core.model.BackupModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
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
    private lateinit var webDavSettings: FakeWebDavSettingsStore
    private lateinit var webDavStorage: FakeWebDavBackupStorage
    private lateinit var s3Settings: FakeS3SettingsStore
    private lateinit var s3Storage: FakeS3BackupStorage
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        controller = FakeBackupWorkController()
        webDavSettings = FakeWebDavSettingsStore()
        webDavStorage = FakeWebDavBackupStorage()
        s3Settings = FakeS3SettingsStore()
        s3Storage = FakeS3BackupStorage()
        viewModel = BackupViewModel(controller, webDavSettings, webDavStorage, s3Settings, s3Storage)
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

        val returnedViewModel = BackupViewModel(
            controller,
            webDavSettings,
            webDavStorage,
            s3Settings,
            s3Storage
        )
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

    @Test
    fun `webdav settings are saved and tested before network export is enabled`() = runTest(dispatcher) {
        viewModel.onWebDavUrlChanged("https://dav.example.com/root")
        viewModel.onWebDavUsernameChanged("alice")
        viewModel.onWebDavPasswordChanged("secret")
        viewModel.onWebDavRemoteDirectoryChanged("AzureQL/backups")

        val message = async { viewModel.events.first() }
        viewModel.saveAndTestWebDav()
        advanceUntilIdle()

        assertEquals(BackupEvent.Message("WebDAV 设置已保存，连接测试成功"), message.await())
        assertEquals("https://dav.example.com/root", webDavSettings.saved?.serverUrl)
        assertEquals("secret", webDavSettings.saved?.password)
        assertTrue(viewModel.uiState.value.canExportToNetwork)

        viewModel.exportBackupToNetwork(NetworkStorageProvider.WEBDAV)
        assertTrue(BackupModule.BASE.apiValue in controller.networkExportModules)
        assertEquals(NetworkStorageProvider.WEBDAV, controller.networkExportProvider)
    }

    @Test
    fun `invalid webdav url is rejected without saving credentials`() = runTest(dispatcher) {
        viewModel.onWebDavUrlChanged("not a url")
        val message = async { viewModel.events.first() }

        viewModel.saveAndTestWebDav()
        advanceUntilIdle()

        assertEquals(BackupEvent.Message("请输入有效的 WebDAV 地址"), message.await())
        assertNull(webDavSettings.saved)
    }

    @Test
    fun `s3 settings are tested before s3 export is enabled`() = runTest(dispatcher) {
        viewModel.onS3EndpointChanged("https://s3.example.com")
        viewModel.onS3AccessKeyIdChanged("access")
        viewModel.onS3SecretAccessKeyChanged("secret")
        viewModel.onS3BucketChanged("backups")

        val message = async { viewModel.events.first() }
        viewModel.saveAndTestS3()
        advanceUntilIdle()

        assertEquals(BackupEvent.Message("S3 设置已保存，连接测试成功"), message.await())
        assertTrue(viewModel.uiState.value.canExportToNetwork)
        assertEquals("backups", s3Settings.saved?.bucket)

        viewModel.exportBackupToNetwork(NetworkStorageProvider.S3)
        assertEquals(NetworkStorageProvider.S3, controller.networkExportProvider)
        assertTrue(BackupModule.BASE.apiValue in controller.networkExportModules)
    }

    @Test
    fun `s3 requires credentials before settings are persisted`() = runTest(dispatcher) {
        viewModel.onS3EndpointChanged("https://s3.example.com")
        viewModel.onS3BucketChanged("backups")
        val message = async { viewModel.events.first() }

        viewModel.saveAndTestS3()
        advanceUntilIdle()

        assertEquals(BackupEvent.Message("请输入 S3 Access Key ID"), message.await())
        assertNull(s3Settings.saved)
    }

    @Test
    fun `network backup is listed downloaded and then waits for restore confirmation`() = runTest(dispatcher) {
        webDavSettings.save(
            serverUrl = "https://dav.example.com",
            username = "alice",
            remoteDirectory = "AzureQL",
            password = "secret",
            isVerified = true
        )
        val backup = NetworkBackupFile(
            provider = NetworkStorageProvider.WEBDAV,
            remoteId = "azureql_backup_20260910.tgz",
            fileName = "azureql_backup_20260910.tgz",
            sizeBytes = 4096,
            modifiedAtEpochMillis = 1_789_000_000_000
        )
        webDavStorage.backups = Result.success(listOf(backup))
        advanceUntilIdle()

        viewModel.loadNetworkBackups(NetworkStorageProvider.WEBDAV)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showNetworkRestorePicker)
        assertEquals(listOf(backup), viewModel.uiState.value.networkBackups)

        viewModel.importNetworkBackup(backup)
        assertEquals(backup.remoteId, controller.networkImport?.remoteId)
        assertEquals(BackupOperation.DOWNLOADING_NETWORK, viewModel.uiState.value.operation)

        controller.transfer.value = BackupWorkSnapshot(
            id = controller.importWorkId,
            kind = BackupWorkKind.NETWORK_IMPORT,
            status = BackupWorkStatus.SUCCEEDED,
            operation = BackupOperation.IMPORTING
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showRestoreConfirmation)
        assertFalse(controller.restoreStarted)
    }
}
private class FakeBackupWorkController : BackupWorkController {
    override val transfer = MutableStateFlow<BackupWorkSnapshot?>(null)
    override val restore = MutableStateFlow<BackupWorkSnapshot?>(null)
    var exportUri: String? = null
    var exportModules: Set<String> = emptySet()
    var importUri: String? = null
    var networkExportModules: Set<String> = emptySet()
    var networkExportProvider: NetworkStorageProvider? = null
    var networkImport: NetworkBackupFile? = null
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

    override fun startNetworkExport(
        provider: NetworkStorageProvider,
        modules: Set<String>
    ): String {
        networkExportProvider = provider
        networkExportModules = modules
        return exportWorkId
    }

    override fun startNetworkImport(
        provider: NetworkStorageProvider,
        remoteId: String,
        contentLength: Long?,
        maxBytes: Long
    ): String {
        networkImport = NetworkBackupFile(provider, remoteId, remoteId.substringAfterLast('/'), contentLength)
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

private class FakeWebDavSettingsStore : WebDavSettingsStore {
    override val settings = MutableStateFlow(WebDavSettings())
    var saved: WebDavConnection? = null

    override suspend fun save(
        serverUrl: String,
        username: String,
        remoteDirectory: String,
        password: String?,
        isVerified: Boolean
    ) {
        saved = WebDavConnection(
            serverUrl,
            username,
            password ?: saved?.password.orEmpty(),
            remoteDirectory
        )
        settings.value = WebDavSettings(
            serverUrl = serverUrl,
            username = username,
            remoteDirectory = remoteDirectory,
            hasSavedPassword = !password.isNullOrEmpty(),
            isConfigured = isVerified
        )
    }

    override suspend fun loadConnection(requireVerified: Boolean): WebDavConnection? =
        saved?.takeIf { !requireVerified || settings.value.isConfigured }
}

private class FakeWebDavBackupStorage : WebDavBackupStorage {
    var testResult: Result<Unit> = Result.success(Unit)
    var backups: Result<List<NetworkBackupFile>> = Result.success(emptyList())

    override suspend fun testConnection(connection: WebDavConnection): Result<Unit> = testResult

    override suspend fun uploadBackup(
        connection: WebDavConnection,
        source: File,
        fileName: String,
        onProgress: (Long, Long) -> Unit
    ): Result<Unit> = Result.success(Unit)

    override suspend fun listBackups(connection: WebDavConnection): Result<List<NetworkBackupFile>> = backups

    override suspend fun downloadBackup(
        connection: WebDavConnection,
        remoteId: String,
        destination: File,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit
    ): Result<Unit> = Result.success(Unit)
}

private class FakeS3SettingsStore : S3SettingsStore {
    override val settings = MutableStateFlow(S3Settings())
    var saved: S3Connection? = null

    override suspend fun save(
        endpoint: String,
        accessKeyId: String?,
        secretAccessKey: String?,
        bucket: String,
        region: String,
        pathStyle: Boolean,
        remoteDirectory: String,
        isVerified: Boolean
    ) {
        saved = S3Connection(
            endpoint = endpoint,
            accessKeyId = accessKeyId ?: saved?.accessKeyId.orEmpty(),
            secretAccessKey = secretAccessKey ?: saved?.secretAccessKey.orEmpty(),
            bucket = bucket,
            region = region,
            pathStyle = pathStyle,
            remoteDirectory = remoteDirectory
        )
        settings.value = S3Settings(
            endpoint = endpoint,
            bucket = bucket,
            region = region,
            pathStyle = pathStyle,
            remoteDirectory = remoteDirectory,
            hasSavedAccessKey = saved?.accessKeyId?.isNotEmpty() == true,
            hasSavedSecretKey = saved?.secretAccessKey?.isNotEmpty() == true,
            isConfigured = isVerified
        )
    }

    override suspend fun loadConnection(requireVerified: Boolean): S3Connection? =
        saved?.takeIf { !requireVerified || settings.value.isConfigured }
}

private class FakeS3BackupStorage : S3BackupStorage {
    var testResult: Result<Unit> = Result.success(Unit)
    var backups: Result<List<NetworkBackupFile>> = Result.success(emptyList())

    override suspend fun testConnection(connection: S3Connection): Result<Unit> = testResult

    override suspend fun uploadBackup(
        connection: S3Connection,
        source: File,
        fileName: String,
        onProgress: (Long, Long) -> Unit
    ): Result<Unit> = Result.success(Unit)

    override suspend fun listBackups(connection: S3Connection): Result<List<NetworkBackupFile>> = backups

    override suspend fun downloadBackup(
        connection: S3Connection,
        remoteId: String,
        destination: File,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit
    ): Result<Unit> = Result.success(Unit)
}
