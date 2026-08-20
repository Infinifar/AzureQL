package com.autopanel.feature.dependency

import com.autopanel.core.model.DependencyCacheType
import com.autopanel.core.model.DependencySetting

enum class DependencySettingSaveStatus(val displayName: String) {
    IDLE(""),
    SAVING("正在提交"),
    SUBMITTED("已提交后台任务"),
    RUNNING("后台执行中"),
    SUCCESS("已完成"),
    ERROR("失败")
}

data class DependencySettingSaveState(
    val status: DependencySettingSaveStatus = DependencySettingSaveStatus.IDLE,
    val detail: String? = null
)

data class DependencySettingsUiState(
    val dependenceProxy: String = "",
    val nodeMirror: String = "",
    val pythonMirror: String = "",
    val linuxMirror: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val cacheToClean: DependencyCacheType? = null,
    val settingStates: Map<DependencySetting, DependencySettingSaveState> = emptyMap(),
    val taskLog: List<String> = emptyList()
)

sealed interface DependencySettingsEvent {
    data class Message(val value: String) : DependencySettingsEvent
}
