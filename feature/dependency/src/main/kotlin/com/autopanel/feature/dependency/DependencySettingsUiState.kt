package com.autopanel.feature.dependency

import com.autopanel.core.model.DependencyCacheType

data class DependencySettingsUiState(
    val dependenceProxy: String = "",
    val nodeMirror: String = "",
    val pythonMirror: String = "",
    val linuxMirror: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val cacheToClean: DependencyCacheType? = null
)

sealed interface DependencySettingsEvent {
    data class Message(val value: String) : DependencySettingsEvent
}
