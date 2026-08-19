package com.autopanel.feature.dependency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.model.DependencyCacheType
import com.autopanel.core.model.SystemConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                    _events.send(DependencySettingsEvent.Message(error.userMessage("加载依赖设置失败")))
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
            _uiState.update { it.copy(isSaving = true) }
            val config = SystemConfig(
                dependenceProxy = state.dependenceProxy.trim(),
                nodeMirror = state.nodeMirror.trim(),
                pythonMirror = state.pythonMirror.trim(),
                linuxMirror = state.linuxMirror.trim()
            )
            configRepository.updateDependencySettings(config)
                .onSuccess {
                    _events.send(
                        DependencySettingsEvent.Message(
                            "依赖设置已保存，Node.js/Linux 镜像由服务端后台更新"
                        )
                    )
                }
                .onFailure { error ->
                    _events.send(DependencySettingsEvent.Message(error.userMessage("保存依赖设置失败")))
                }
            _uiState.update { it.copy(isSaving = false) }
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
                    _events.send(DependencySettingsEvent.Message(error.userMessage("清理依赖缓存失败")))
                }
        }
    }

    private fun Throwable.userMessage(fallback: String): String = message?.takeIf(String::isNotBlank) ?: fallback
}
