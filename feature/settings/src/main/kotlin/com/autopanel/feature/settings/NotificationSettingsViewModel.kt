package com.autopanel.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.domain.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val selectedType: String = "closed",
    val values: Map<String, String> = emptyMap(),
    val fieldErrors: Set<String> = emptySet(),
    val unsupportedType: String? = null,
    val loadError: String? = null
)

sealed interface NotificationSettingsEvent {
    data class Message(val text: String) : NotificationSettingsEvent
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()
    private val _events = Channel<NotificationSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var original = JsonObject(emptyMap())
    private var originalType = "closed"

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            repository.getConfig()
                .onSuccess { config ->
                    original = config
                    originalType = config.notificationType()
                    val supported = NotificationProviderRegistry.find(originalType)
                    _uiState.value = NotificationSettingsUiState(
                        isLoading = false,
                        selectedType = supported?.type ?: originalType,
                        values = notificationFields(config),
                        unsupportedType = if (supported == null) originalType else null
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, loadError = error.message ?: "获取通知设置失败")
                    }
                }
        }
    }

    fun selectProvider(type: String) {
        val provider = NotificationProviderRegistry.find(type) ?: return
        val sameProvider = NotificationProviderRegistry.find(originalType)?.type == provider.type
        _uiState.update {
            it.copy(
                selectedType = provider.type,
                values = if (sameProvider) notificationFields(original) else emptyMap(),
                fieldErrors = emptySet(),
                unsupportedType = null
            )
        }
    }

    fun updateField(key: String, value: String) {
        _uiState.update {
            it.copy(values = it.values + (key to value), fieldErrors = it.fieldErrors - key)
        }
    }

    fun testAndSave() {
        val state = _uiState.value
        val provider = NotificationProviderRegistry.find(state.selectedType) ?: return
        val errors = provider.fields.filter { it.required && state.values[it.key].isNullOrBlank() }
            .mapTo(mutableSetOf()) { it.key }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            _events.trySend(NotificationSettingsEvent.Message("请填写所有必填项"))
            return
        }
        val payload = buildNotificationPayload(original, originalType, state.selectedType, state.values)
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            repository.testAndSave(payload)
                .onSuccess {
                    original = payload
                    originalType = state.selectedType
                    _uiState.update { it.copy(isSaving = false) }
                    _events.trySend(NotificationSettingsEvent.Message("测试通知发送成功，设置已保存"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.trySend(NotificationSettingsEvent.Message(error.message ?: "测试通知失败，设置未保存"))
                }
        }
    }
}
