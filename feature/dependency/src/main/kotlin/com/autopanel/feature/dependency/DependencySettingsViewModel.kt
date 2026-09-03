package com.autopanel.feature.dependency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.model.DependencyCacheType
import com.autopanel.core.model.DependencyMirrorEvent
import com.autopanel.core.model.DependencySetting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DependencySettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DependencySettingsUiState())
    val uiState: StateFlow<DependencySettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<DependencySettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
        observeMirrorTasks()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            configRepository.getSystemConfig()
                .onSuccess { config ->
                    _uiState.update {
                        it.copy(
                            dependenceProxy = config.dependenceProxy.orEmpty(),
                            nodeMirror = config.nodeMirror.orEmpty(),
                            pythonMirror = config.pythonMirror.orEmpty(),
                            linuxMirror = config.linuxMirror.orEmpty(),
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(
                        DependencySettingsEvent.Message(error.userMessage("加载依赖设置失败"))
                    )
                }
        }
    }

    fun onDependenceProxyChanged(value: String) {
        _uiState.update { it.copy(dependenceProxy = value) }
    }

    fun onNodeMirrorChanged(value: String) {
        _uiState.update { it.copy(nodeMirror = value) }
    }

    fun onPythonMirrorChanged(value: String) {
        _uiState.update { it.copy(pythonMirror = value) }
    }

    fun onLinuxMirrorChanged(value: String) {
        _uiState.update { it.copy(linuxMirror = value) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        viewModelScope.launch {
            val values = linkedMapOf(
                DependencySetting.PROXY to state.dependenceProxy.trim(),
                DependencySetting.NODE_MIRROR to state.nodeMirror.trim(),
                DependencySetting.PYTHON_MIRROR to state.pythonMirror.trim(),
                DependencySetting.LINUX_MIRROR to state.linuxMirror.trim()
            )
            _uiState.update {
                it.copy(
                    isSaving = true,
                    settingStates = values.keys.associateWith {
                        DependencySettingSaveState(DependencySettingSaveStatus.IDLE)
                    },
                    taskLog = emptyList()
                )
            }

            var successCount = 0
            values.forEach { (setting, value) ->
                updateSettingState(setting, DependencySettingSaveStatus.SAVING)
                configRepository.updateDependencySetting(setting, value)
                    .onSuccess {
                        successCount++
                        updateSettingState(
                            setting,
                            when (setting) {
                                DependencySetting.NODE_MIRROR,
                                DependencySetting.LINUX_MIRROR ->
                                    DependencySettingSaveStatus.SUBMITTED
                                else -> DependencySettingSaveStatus.SUCCESS
                            }
                        )
                    }
                    .onFailure { error ->
                        updateSettingState(
                            setting,
                            DependencySettingSaveStatus.ERROR,
                            error.userMessage("提交失败")
                        )
                    }
            }
            _uiState.update { it.copy(isSaving = false) }
            _events.send(
                DependencySettingsEvent.Message(
                    if (successCount == values.size) {
                        "4 项设置均已提交；Node.js/Linux 的后台日志显示在下方"
                    } else {
                        "$successCount/${values.size} 项提交成功，请查看每项状态"
                    }
                )
            )
        }
    }

    fun requestCleanCache(type: DependencyCacheType) {
        _uiState.update { it.copy(cacheToClean = type) }
    }

    fun dismissCleanCache() {
        _uiState.update { it.copy(cacheToClean = null) }
    }

    fun confirmCleanCache() {
        val type = _uiState.value.cacheToClean ?: return
        _uiState.update { it.copy(cacheToClean = null) }
        viewModelScope.launch {
            configRepository.cleanDependencyCache(type)
                .onSuccess {
                    _events.send(DependencySettingsEvent.Message("${type.displayName}已清理"))
                }
                .onFailure { error ->
                    _events.send(
                        DependencySettingsEvent.Message(
                            error.userMessage("清理依赖缓存失败")
                        )
                    )
                }
        }
    }

    private fun observeMirrorTasks() {
        viewModelScope.launch {
            configRepository.observeDependencyMirrorTasks()
                .catch { error ->
                    if (error is CancellationException) throw error
                    // ConnectionError already explains loss of live updates.
                }
                .collect { event ->
                    when (event) {
                        is DependencyMirrorEvent.ConnectionError -> {
                            _events.send(DependencySettingsEvent.Message(event.message))
                        }
                        is DependencyMirrorEvent.Task -> {
                            val message = event.message?.trim()?.takeIf(String::isNotEmpty)
                            if (message != null) {
                                _uiState.update {
                                    it.copy(
                                        taskLog = (
                                            it.taskLog + DependencyTaskLogEntry(
                                                setting = event.setting,
                                                message = message
                                            )
                                        ).takeLast(200)
                                    )
                                }
                            }
                            updateSettingState(
                                event.setting,
                                if (event.status == "completed") {
                                    DependencySettingSaveStatus.SUCCESS
                                } else {
                                    DependencySettingSaveStatus.RUNNING
                                },
                                message
                            )
                        }
                    }
                }
        }
    }

    private fun updateSettingState(
        setting: DependencySetting,
        status: DependencySettingSaveStatus,
        detail: String? = null
    ) {
        _uiState.update { state ->
            state.copy(
                settingStates = state.settingStates +
                    (setting to DependencySettingSaveState(status, detail))
            )
        }
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf(String::isNotBlank) ?: fallback
}
