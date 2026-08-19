package com.autopanel.app.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val configRepo: ConfigRepository
) : ViewModel() {

    private val _content = MutableStateFlow<String?>(null)
    val content = _content.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing = _isEditing.asStateFlow()

    private val _editContent = MutableStateFlow("")
    val editContent = _editContent.asStateFlow()

    init { loadConfig() }

    fun loadConfig(name: String = "config.sh") {
        viewModelScope.launch {
            _loading.value = true
            configRepo.getConfigContent(name)
                .onSuccess { c ->
                    _content.value = c
                    _editContent.value = c
                }
                .onFailure { _content.value = "加载失败: ${it.message}" }
            _loading.value = false
        }
    }

    fun enterEditMode() {
        _editContent.value = _content.value ?: ""
        _isEditing.value = true
    }

    fun onContentChanged(v: String) { _editContent.value = v }

    fun saveContent(name: String = "config.sh") {
        viewModelScope.launch {
            configRepo.saveConfig(name, _editContent.value)
                .onSuccess {
                    _content.value = _editContent.value
                    _isEditing.value = false
                }
        }
    }

    fun cancelEdit() { _isEditing.value = false }
}
