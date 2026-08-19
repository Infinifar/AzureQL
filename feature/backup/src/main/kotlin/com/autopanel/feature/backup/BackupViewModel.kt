package com.autopanel.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.BackupRepository
import com.autopanel.core.model.BackupModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

private const val HEALTH_CHECK_ATTEMPTS = 30
private const val HEALTH_CHECK_DELAY_MS = 2_000L
private const val BYTES_PER_MB = 1024L * 1024L
private const val PROGRESS_UPDATE_STEP = 256L * 1024L

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var operationJob: Job? = null

    fun toggleModule(module: BackupModule) {
        if (module == BackupModule.BASE || _uiState.value.isBusy) return
        _uiState.update { state ->
            val modules = state.selectedModules.toMutableSet()
            if (!modules.add(module)) modules.remove(module)
            state.copy(selectedModules = modules)
        }
    }

    fun onMaxImportSizeChanged(value: String) {
        if (value.length <= 5 && value.all { it.isDigit() } && !_uiState.value.isBusy) {
            _uiState.update { it.copy(maxImportSizeMb = value) }
        }
    }

    fun exportBackup(destination: OutputStream) {
        if (_uiState.value.isBusy) {
            runCatching { destination.close() }
            return
        }
        val modules = _uiState.value.selectedModules
        operationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operation = BackupOperation.EXPORTING,
                    transferredBytes = 0,
                    totalBytes = null
                )
            }
            try {
                destination.use { output ->
                    backupRepository.exportBackup(modules, output, ::updateProgress)
                        .onSuccess { _events.send(BackupEvent.Message("备份已保存")) }
                        .onFailure { error ->
                            _events.send(
                                BackupEvent.Message(error.userMessage("导出备份失败"))
                            )
                        }
                }
            } catch (e: CancellationException) {
                _events.send(BackupEvent.Message("导出已取消；目标位置可能留有不完整文件"))
                throw e
            } finally {
                finishOperation()
            }
        }
    }

    fun importBackup(source: InputStream, contentLength: Long?) {
        if (_uiState.value.isBusy) {
            runCatching { source.close() }
            return
        }
        val maxBytes = _uiState.value.maxImportSizeMb.toLongOrNull()?.takeIf { it > 0 }
            ?.times(BYTES_PER_MB)
        if (maxBytes == null) {
            runCatching { source.close() }
            _events.trySend(BackupEvent.Message("请输入有效的备份大小上限"))
            return
        }
        if (contentLength != null && contentLength > maxBytes) {
            runCatching { source.close() }
            _events.trySend(
                BackupEvent.Message(
                    "备份文件超过 ${_uiState.value.maxImportSizeMb} MB 上限，未开始上传"
                )
            )
            return
        }

        operationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    operation = BackupOperation.IMPORTING,
                    transferredBytes = 0,
                    totalBytes = contentLength
                )
            }
            try {
                source.use { input ->
                    backupRepository.importBackup(input, contentLength) { transferred, total ->
                        if (transferred > maxBytes) {
                            throw IllegalArgumentException(
                                "备份数据超过 ${_uiState.value.maxImportSizeMb} MB 上限，上传已中止"
                            )
                        }
                        updateProgress(transferred, total)
                    }
                        .onSuccess {
                            _uiState.update { it.copy(showRestoreConfirmation = true) }
                        }
                        .onFailure { error ->
                            _events.send(
                                BackupEvent.Message(error.userMessage("上传备份失败"))
                            )
                        }
                }
            } catch (e: CancellationException) {
                _events.send(BackupEvent.Message("上传已取消，服务端数据尚未恢复"))
                throw e
            } finally {
                finishOperation()
            }
        }
    }

    fun cancelTransfer() {
        when (_uiState.value.operation) {
            BackupOperation.EXPORTING,
            BackupOperation.IMPORTING -> operationJob?.cancel()
            else -> Unit
        }
    }

    fun dismissRestoreConfirmation() {
        _uiState.update { it.copy(showRestoreConfirmation = false) }
    }

    fun confirmRestore() {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                showRestoreConfirmation = false,
                operation = BackupOperation.RESTORING,
                healthCheckAttempt = 0,
                transferredBytes = 0,
                totalBytes = null
            )
        }
        operationJob = viewModelScope.launch {
            try {
                backupRepository.activateImportedBackup()
                    .onFailure { error ->
                        _events.send(
                            BackupEvent.Message(error.userMessage("启动数据恢复失败"))
                        )
                        return@launch
                    }

                repeat(HEALTH_CHECK_ATTEMPTS) { attempt ->
                    delay(HEALTH_CHECK_DELAY_MS)
                    _uiState.update { it.copy(healthCheckAttempt = attempt + 1) }
                    if (backupRepository.healthCheck().isSuccess) {
                        _events.send(BackupEvent.RestoreCompleted)
                        return@launch
                    }
                }

                _events.send(
                    BackupEvent.Message(
                        "60 秒内未检测到服务恢复；请检查容器状态，必要时执行 ql reload data"
                    )
                )
            } finally {
                finishOperation()
            }
        }
    }

    private fun updateProgress(transferred: Long, total: Long?) {
        val previous = _uiState.value.transferredBytes
        if (transferred - previous < PROGRESS_UPDATE_STEP && transferred != total) return
        _uiState.update { it.copy(transferredBytes = transferred, totalBytes = total) }
    }

    private fun finishOperation() {
        operationJob = null
        _uiState.update {
            it.copy(operation = null, transferredBytes = 0, totalBytes = null)
        }
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf(String::isNotBlank) ?: fallback
}
