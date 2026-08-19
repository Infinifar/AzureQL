package com.autopanel.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.BackupRepository
import com.autopanel.core.model.BackupModule
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun toggleModule(module: BackupModule) {
        if (module == BackupModule.BASE || _uiState.value.isBusy) return
        _uiState.update { state ->
            val modules = state.selectedModules.toMutableSet()
            if (!modules.add(module)) modules.remove(module)
            state.copy(selectedModules = modules)
        }
    }

    fun exportBackup(destination: OutputStream) {
        if (_uiState.value.isBusy) {
            runCatching { destination.close() }
            return
        }
        val modules = _uiState.value.selectedModules
        viewModelScope.launch {
            _uiState.update { it.copy(operation = BackupOperation.EXPORTING) }
            try {
                destination.use { output ->
                    backupRepository.exportBackup(modules, output)
                        .onSuccess { _events.send(BackupEvent.Message("备份已保存")) }
                        .onFailure { error ->
                            _events.send(BackupEvent.Message(error.userMessage("导出备份失败")))
                        }
                }
            } finally {
                _uiState.update { it.copy(operation = null) }
            }
        }
    }

    fun importBackup(source: InputStream, contentLength: Long?) {
        if (_uiState.value.isBusy) {
            runCatching { source.close() }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(operation = BackupOperation.IMPORTING) }
            try {
                source.use { input ->
                    backupRepository.importBackup(input, contentLength)
                        .onSuccess {
                            _uiState.update { it.copy(showRestoreConfirmation = true) }
                        }
                        .onFailure { error ->
                            _events.send(BackupEvent.Message(error.userMessage("上传备份失败")))
                        }
                }
            } finally {
                _uiState.update { it.copy(operation = null) }
            }
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
                healthCheckAttempt = 0
            )
        }
        viewModelScope.launch {
            backupRepository.activateImportedBackup()
                .onFailure { error ->
                    _uiState.update { it.copy(operation = null) }
                    _events.send(BackupEvent.Message(error.userMessage("启动数据恢复失败")))
                    return@launch
                }

            repeat(HEALTH_CHECK_ATTEMPTS) { attempt ->
                delay(HEALTH_CHECK_DELAY_MS)
                _uiState.update { it.copy(healthCheckAttempt = attempt + 1) }
                if (backupRepository.healthCheck().isSuccess) {
                    _uiState.update { it.copy(operation = null) }
                    _events.send(BackupEvent.RestoreCompleted)
                    return@launch
                }
            }

            _uiState.update { it.copy(operation = null) }
            _events.send(
                BackupEvent.Message(
                    "60 秒内未检测到服务恢复；请检查容器状态，必要时执行 ql reload data"
                )
            )
        }
    }

    private fun Throwable.userMessage(fallback: String): String = message?.takeIf(String::isNotBlank) ?: fallback
}
