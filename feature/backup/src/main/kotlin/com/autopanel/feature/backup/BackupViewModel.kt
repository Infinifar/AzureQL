package com.autopanel.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.model.BackupModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val BYTES_PER_MB = 1024L * 1024L

@HiltViewModel
class BackupViewModel @Inject internal constructor(
    private val workController: BackupWorkController,
    private val webDavSettingsStore: WebDavSettingsStore,
    private val webDavStorage: WebDavBackupStorage,
    private val s3SettingsStore: S3SettingsStore,
    private val s3Storage: S3BackupStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    // WorkManager retains terminal snapshots across navigation, whereas messages
    // belong only to the screen that starts or observes active work. A buffered
    // single-consumer channel preserves a message until this screen collects it
    // without turning historical work into a new Snackbar on a later screen.
    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    private val handledWorkIds = mutableSetOf<String>()
    private val startedWorkIds = mutableSetOf<String>()
    private val activeWorkIds = mutableSetOf<String>()
    private var pendingImportWorkId: String? = null
    private var networkListJob: Job? = null

    init {
        viewModelScope.launch {
            combine(workController.transfer, workController.restore, ::Pair)
                .collect { (transfer, restore) -> applyWorkState(transfer, restore) }
        }
        viewModelScope.launch {
            webDavSettingsStore.settings.collect { settings ->
                _uiState.update { state ->
                    if (state.webDavDirty && state.webDavSettingsScopeId == settings.accountScopeId) {
                        state.copy(webDavSettingsLoaded = true)
                    } else state.copy(
                        webDavSettingsScopeId = settings.accountScopeId,
                        webDavSettingsLoaded = true,
                        webDavUrl = settings.serverUrl,
                        webDavUsername = settings.username,
                        webDavRemoteDirectory = settings.remoteDirectory,
                        webDavHasSavedPassword = settings.hasSavedPassword,
                        webDavConfigured = settings.isConfigured,
                        webDavDirty = false,
                        webDavPassword = ""
                    )
                }
            }
        }
        viewModelScope.launch {
            s3SettingsStore.settings.collect { settings ->
                _uiState.update { state ->
                    if (state.s3Dirty && state.s3SettingsScopeId == settings.accountScopeId) {
                        state.copy(s3SettingsLoaded = true)
                    } else state.copy(
                        s3SettingsScopeId = settings.accountScopeId,
                        s3SettingsLoaded = true,
                        s3Endpoint = settings.endpoint,
                        s3Bucket = settings.bucket,
                        s3Region = settings.region,
                        s3PathStyle = settings.pathStyle,
                        s3RemoteDirectory = settings.remoteDirectory,
                        s3HasSavedAccessKey = settings.hasSavedAccessKey,
                        s3HasSavedSecretKey = settings.hasSavedSecretKey,
                        s3Configured = settings.isConfigured,
                        s3Dirty = false,
                        s3AccessKeyId = "",
                        s3SecretAccessKey = ""
                    )
                }
            }
        }
    }

    fun toggleModule(module: BackupModule) {
        if (module == BackupModule.BASE || _uiState.value.isBusy) return
        _uiState.update { state ->
            val modules = state.selectedModules.toMutableSet()
            if (!modules.add(module)) modules.remove(module)
            state.copy(selectedModules = modules)
        }
    }

    fun onMaxImportSizeChanged(value: String) {
        if (value.length <= 5 && value.all(Char::isDigit) && !_uiState.value.isBusy) {
            _uiState.update { it.copy(maxImportSizeMb = value) }
        }
    }

    fun exportBackup(destinationUri: String) {
        if (_uiState.value.isBusy) return
        val modules = _uiState.value.selectedModules.mapTo(mutableSetOf(), BackupModule::apiValue)
        _uiState.update {
            it.copy(operation = BackupOperation.EXPORTING, transferredBytes = 0, totalBytes = null)
        }
        startedWorkIds += workController.startExport(destinationUri, modules)
    }

    fun exportBackupToNetwork(provider: NetworkStorageProvider? = null) {
        val state = _uiState.value
        if (state.isBusy || state.isTestingWebDav || state.isTestingS3) return
        val available = state.configuredNetworkProviders
        val selectedProvider = provider ?: available.singleOrNull()
        if (selectedProvider == null || selectedProvider !in available) {
            _events.trySend(BackupEvent.Message("请先保存并测试网络存储设置"))
            return
        }
        val modules = state.selectedModules.mapTo(mutableSetOf(), BackupModule::apiValue)
        _uiState.update {
            it.copy(operation = BackupOperation.EXPORTING, transferredBytes = 0, totalBytes = null)
        }
        startedWorkIds += workController.startNetworkExport(selectedProvider, modules)
    }

    fun onWebDavUrlChanged(value: String) = updateWebDavDraft { copy(webDavUrl = value) }

    fun onWebDavUsernameChanged(value: String) = updateWebDavDraft { copy(webDavUsername = value) }

    fun onWebDavPasswordChanged(value: String) = updateWebDavDraft { copy(webDavPassword = value) }

    fun onWebDavRemoteDirectoryChanged(value: String) =
        updateWebDavDraft { copy(webDavRemoteDirectory = value) }

    fun saveAndTestWebDav() {
        val draft = _uiState.value
        if (draft.isBusy || draft.isTestingWebDav) return
        validateWebDavSettings(
            draft.webDavUrl,
            draft.webDavUsername,
            draft.webDavRemoteDirectory
        )?.let { message ->
            _events.trySend(BackupEvent.Message(message))
            return
        }
        _uiState.update { it.copy(isTestingWebDav = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    webDavSettingsStore.save(
                        serverUrl = draft.webDavUrl,
                        username = draft.webDavUsername,
                        remoteDirectory = draft.webDavRemoteDirectory,
                        password = draft.webDavPassword.takeIf { it.isNotEmpty() },
                        isVerified = false
                    )
                    val connection = WebDavConnection(
                        serverUrl = draft.webDavUrl.trim().trimEnd('/'),
                        username = draft.webDavUsername.trim(),
                        password = draft.webDavPassword.takeIf(String::isNotEmpty)
                            ?: webDavSettingsStore.loadConnection(requireVerified = false)?.password.orEmpty(),
                        remoteDirectory = draft.webDavRemoteDirectory.trim().trim('/')
                    )
                    webDavStorage.testConnection(connection).getOrThrow()
                    webDavSettingsStore.save(
                        serverUrl = connection.serverUrl,
                        username = connection.username,
                        remoteDirectory = connection.remoteDirectory,
                        password = null,
                        isVerified = true
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isTestingWebDav = false,
                    webDavDirty = result.isFailure,
                    webDavConfigured = result.isSuccess,
                    webDavHasSavedPassword = it.webDavHasSavedPassword || draft.webDavPassword.isNotEmpty(),
                    webDavPassword = if (result.isSuccess) "" else it.webDavPassword
                )
            }
            _events.send(
                BackupEvent.Message(
                    if (result.isSuccess) "WebDAV 设置已保存，连接测试成功"
                    else webDavFailureMessage(result.exceptionOrNull(), "WebDAV 连接测试失败")
                )
            )
        }
    }

    fun onS3EndpointChanged(value: String) = updateS3Draft { copy(s3Endpoint = value) }

    fun onS3AccessKeyIdChanged(value: String) = updateS3Draft { copy(s3AccessKeyId = value) }

    fun onS3SecretAccessKeyChanged(value: String) = updateS3Draft { copy(s3SecretAccessKey = value) }

    fun onS3BucketChanged(value: String) = updateS3Draft { copy(s3Bucket = value) }

    fun onS3RegionChanged(value: String) = updateS3Draft { copy(s3Region = value) }

    fun onS3PathStyleChanged(value: Boolean) = updateS3Draft { copy(s3PathStyle = value) }

    fun onS3RemoteDirectoryChanged(value: String) =
        updateS3Draft { copy(s3RemoteDirectory = value) }

    fun saveAndTestS3() {
        val draft = _uiState.value
        if (draft.isBusy || draft.isTestingS3) return
        validateS3Settings(
            draft.s3Endpoint,
            draft.s3Bucket,
            draft.s3Region,
            draft.s3RemoteDirectory
        )?.let { message ->
            _events.trySend(BackupEvent.Message(message))
            return
        }
        if (draft.s3AccessKeyId.isBlank() && !draft.s3HasSavedAccessKey) {
            _events.trySend(BackupEvent.Message("请输入 S3 Access Key ID"))
            return
        }
        if (draft.s3SecretAccessKey.isBlank() && !draft.s3HasSavedSecretKey) {
            _events.trySend(BackupEvent.Message("请输入 S3 Secret Access Key"))
            return
        }
        _uiState.update { it.copy(isTestingS3 = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    s3SettingsStore.save(
                        endpoint = draft.s3Endpoint,
                        accessKeyId = draft.s3AccessKeyId.takeIf(String::isNotEmpty),
                        secretAccessKey = draft.s3SecretAccessKey.takeIf(String::isNotEmpty),
                        bucket = draft.s3Bucket,
                        region = draft.s3Region,
                        pathStyle = draft.s3PathStyle,
                        remoteDirectory = draft.s3RemoteDirectory,
                        isVerified = false
                    )
                    val connection = s3SettingsStore.loadConnection(requireVerified = false)
                        ?: error("S3 访问密钥未配置")
                    s3Storage.testConnection(connection).getOrThrow()
                    s3SettingsStore.save(
                        endpoint = connection.endpoint,
                        accessKeyId = null,
                        secretAccessKey = null,
                        bucket = connection.bucket,
                        region = connection.region,
                        pathStyle = connection.pathStyle,
                        remoteDirectory = connection.remoteDirectory,
                        isVerified = true
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isTestingS3 = false,
                    s3Dirty = result.isFailure,
                    s3Configured = result.isSuccess,
                    s3HasSavedAccessKey = it.s3HasSavedAccessKey || draft.s3AccessKeyId.isNotEmpty(),
                    s3HasSavedSecretKey = it.s3HasSavedSecretKey || draft.s3SecretAccessKey.isNotEmpty(),
                    s3AccessKeyId = if (result.isSuccess) "" else it.s3AccessKeyId,
                    s3SecretAccessKey = if (result.isSuccess) "" else it.s3SecretAccessKey
                )
            }
            _events.send(
                BackupEvent.Message(
                    if (result.isSuccess) "S3 设置已保存，连接测试成功"
                    else s3FailureMessage(result.exceptionOrNull(), "S3 连接测试失败")
                )
            )
        }
    }

    fun importBackup(sourceUri: String, contentLength: Long?) {
        if (_uiState.value.isBusy) return
        val maxBytes = _uiState.value.maxImportSizeMb.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.times(BYTES_PER_MB)
        if (maxBytes == null) {
            _events.trySend(BackupEvent.Message("请输入有效的备份大小上限"))
            return
        }
        if (contentLength != null && contentLength > maxBytes) {
            _events.trySend(
                BackupEvent.Message("备份文件超过 ${_uiState.value.maxImportSizeMb} MB 上限，未开始上传")
            )
            return
        }
        _uiState.update {
            it.copy(
                operation = BackupOperation.VALIDATING_IMPORT,
                transferredBytes = 0,
                totalBytes = contentLength
            )
        }
        startedWorkIds += workController.startImport(sourceUri, contentLength, maxBytes)
    }

    fun loadNetworkBackups(provider: NetworkStorageProvider) {
        val state = _uiState.value
        if (state.isBusy || provider !in state.configuredNetworkProviders) return
        networkListJob?.cancel()
        _uiState.update {
            it.copy(
                showNetworkRestorePicker = true,
                networkRestoreProvider = provider,
                networkBackups = emptyList(),
                isLoadingNetworkBackups = true,
                networkBackupListError = null
            )
        }
        networkListJob = viewModelScope.launch {
            val result = when (provider) {
                NetworkStorageProvider.WEBDAV -> {
                    val connection = webDavSettingsStore.loadConnection()
                    if (connection == null) Result.failure(IllegalStateException("WebDAV 尚未配置"))
                    else webDavStorage.listBackups(connection)
                }
                NetworkStorageProvider.S3 -> {
                    val connection = s3SettingsStore.loadConnection()
                    if (connection == null) Result.failure(IllegalStateException("S3 尚未配置"))
                    else s3Storage.listBackups(connection)
                }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            _uiState.update { current ->
                if (current.networkRestoreProvider != provider) current else current.copy(
                    networkBackups = result.getOrDefault(emptyList()),
                    isLoadingNetworkBackups = false,
                    networkBackupListError = result.exceptionOrNull()?.let { error ->
                        when (provider) {
                            NetworkStorageProvider.WEBDAV -> webDavFailureMessage(error, "读取 WebDAV 备份列表失败")
                            NetworkStorageProvider.S3 -> s3FailureMessage(error, "读取 S3 备份列表失败")
                        }
                    }
                )
            }
        }
    }

    fun dismissNetworkRestorePicker() {
        networkListJob?.cancel()
        networkListJob = null
        _uiState.update {
            it.copy(
                showNetworkRestorePicker = false,
                networkRestoreProvider = null,
                networkBackups = emptyList(),
                isLoadingNetworkBackups = false,
                networkBackupListError = null
            )
        }
    }

    fun importNetworkBackup(backup: NetworkBackupFile) {
        val state = _uiState.value
        if (state.isBusy || backup.provider != state.networkRestoreProvider ||
            backup !in state.networkBackups
        ) return
        val maxBytes = state.maxImportSizeMb.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.times(BYTES_PER_MB)
        if (maxBytes == null) {
            _events.trySend(BackupEvent.Message("请输入有效的备份大小上限"))
            return
        }
        if (backup.sizeBytes != null && backup.sizeBytes > maxBytes) {
            _events.trySend(
                BackupEvent.Message("备份文件超过 ${state.maxImportSizeMb} MB 上限，未开始下载")
            )
            return
        }
        dismissNetworkRestorePicker()
        _uiState.update {
            it.copy(
                operation = BackupOperation.DOWNLOADING_NETWORK,
                transferredBytes = 0,
                totalBytes = backup.sizeBytes
            )
        }
        startedWorkIds += workController.startNetworkImport(
            backup.provider,
            backup.remoteId,
            backup.sizeBytes,
            maxBytes
        )
    }

    fun cancelTransfer() {
        if (_uiState.value.operation?.canCancel == true) workController.cancelTransfer()
    }

    fun dismissRestoreConfirmation() {
        pendingImportWorkId?.let(::markHandled)
        pendingImportWorkId = null
        _uiState.update { it.copy(showRestoreConfirmation = false) }
    }

    fun confirmRestore() {
        if (_uiState.value.isBusy) return
        pendingImportWorkId?.let(::markHandled)
        pendingImportWorkId = null
        _uiState.update {
            it.copy(
                showRestoreConfirmation = false,
                operation = BackupOperation.ACTIVATING_RESTORE,
                healthCheckAttempt = 0,
                transferredBytes = 0,
                totalBytes = null
            )
        }
        startedWorkIds += workController.startRestore()
    }

    private suspend fun applyWorkState(
        transfer: BackupWorkSnapshot?,
        restore: BackupWorkSnapshot?
    ) {
        val active = restore?.takeIf(BackupWorkSnapshot::isActive)
            ?: transfer?.takeIf(BackupWorkSnapshot::isActive)
        if (active != null) {
            activeWorkIds += active.id
            _uiState.update {
                it.copy(
                    operation = active.operation,
                    transferredBytes = active.transferredBytes,
                    totalBytes = active.totalBytes,
                    healthCheckAttempt = active.healthCheckAttempt
                )
            }
        } else {
            _uiState.update {
                it.copy(operation = null, transferredBytes = 0, totalBytes = null, healthCheckAttempt = 0)
            }
        }

        transfer?.takeIf(::isCurrentScreenCompletion)?.let { finished ->
            when (finished.status) {
                BackupWorkStatus.SUCCEEDED -> {
                    if (finished.kind == BackupWorkKind.IMPORT ||
                        finished.kind == BackupWorkKind.NETWORK_IMPORT
                    ) {
                        pendingImportWorkId = finished.id
                        _uiState.update { it.copy(showRestoreConfirmation = true) }
                    } else {
                        markHandled(finished.id)
                        _events.send(BackupEvent.Message(finished.message ?: "备份已保存"))
                    }
                }
                BackupWorkStatus.FAILED -> {
                    markHandled(finished.id)
                    _events.send(BackupEvent.Message(finished.message ?: "备份任务失败"))
                }
                BackupWorkStatus.CANCELLED -> {
                    markHandled(finished.id)
                    _events.send(
                        BackupEvent.Message(
                            when (finished.kind) {
                                BackupWorkKind.EXPORT, BackupWorkKind.NETWORK_EXPORT ->
                                    "导出已取消，未保留不完整文件"
                                BackupWorkKind.NETWORK_IMPORT ->
                                    "网络恢复传输已取消，服务端数据尚未恢复"
                                else -> "上传已取消，服务端数据尚未恢复"
                            }
                        )
                    )
                }
                else -> Unit
            }
        }

        restore?.takeIf(::isCurrentScreenCompletion)?.let { finished ->
            markHandled(finished.id)
            when (finished.status) {
                BackupWorkStatus.SUCCEEDED -> _events.send(BackupEvent.RestoreCompleted)
                BackupWorkStatus.FAILED -> _events.send(
                    BackupEvent.Message(finished.message ?: "恢复备份失败")
                )
                else -> Unit
            }
        }
    }

    private fun markHandled(id: String) {
        handledWorkIds += id
        while (handledWorkIds.size > 12) handledWorkIds.remove(handledWorkIds.first())
    }

    private fun isCurrentScreenCompletion(snapshot: BackupWorkSnapshot): Boolean =
        !snapshot.isActive &&
            snapshot.id !in handledWorkIds &&
            (snapshot.id in startedWorkIds || snapshot.id in activeWorkIds)

    private fun updateWebDavDraft(transform: BackupUiState.() -> BackupUiState) {
        if (_uiState.value.isBusy || _uiState.value.isTestingWebDav || _uiState.value.isTestingS3) return
        _uiState.update { it.transform().copy(webDavDirty = true) }
    }

    private fun updateS3Draft(transform: BackupUiState.() -> BackupUiState) {
        if (_uiState.value.isBusy || _uiState.value.isTestingWebDav || _uiState.value.isTestingS3) return
        _uiState.update { it.transform().copy(s3Dirty = true) }
    }
}
