package com.autopanel.feature.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.model.BackupModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayList
import javax.inject.Inject

private const val BYTES_PER_MB = 1024L * 1024L
private const val HANDLED_WORK_IDS = "handled_backup_work_ids"

@HiltViewModel
class BackupViewModel @Inject internal constructor(
    private val workController: BackupWorkController,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val handledWorkIds = savedStateHandle
        .get<ArrayList<String>>(HANDLED_WORK_IDS)
        ?.toMutableSet()
        ?: mutableSetOf()
    private var pendingImportWorkId: String? = null

    init {
        viewModelScope.launch {
            combine(workController.transfer, workController.restore, ::Pair)
                .collect { (transfer, restore) -> applyWorkState(transfer, restore) }
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
        workController.startExport(destinationUri, modules)
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
        workController.startImport(sourceUri, contentLength, maxBytes)
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
        workController.startRestore()
    }

    private suspend fun applyWorkState(
        transfer: BackupWorkSnapshot?,
        restore: BackupWorkSnapshot?
    ) {
        val active = restore?.takeIf(BackupWorkSnapshot::isActive)
            ?: transfer?.takeIf(BackupWorkSnapshot::isActive)
        if (active != null) {
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

        transfer?.takeUnless { it.isActive || it.id in handledWorkIds }?.let { finished ->
            when (finished.status) {
                BackupWorkStatus.SUCCEEDED -> {
                    if (finished.kind == BackupWorkKind.IMPORT) {
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
                            if (finished.kind == BackupWorkKind.EXPORT) "导出已取消，未保留不完整文件"
                            else "上传已取消，服务端数据尚未恢复"
                        )
                    )
                }
                else -> Unit
            }
        }

        restore?.takeUnless { it.isActive || it.id in handledWorkIds }?.let { finished ->
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
        savedStateHandle[HANDLED_WORK_IDS] = ArrayList(handledWorkIds)
    }
}
