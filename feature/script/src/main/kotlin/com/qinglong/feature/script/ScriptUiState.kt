package com.qinglong.feature.script

import com.qinglong.core.model.ScriptFile

data class ScriptUiState(
    val scripts: List<ScriptFile> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    // 编辑
    val editingFilename: String = "",
    val editingPath: String = "",
    val editContent: String = "",
    val originalContent: String = "",
    val isEditing: Boolean = false,       // 编辑模式
    val isLoadingContent: Boolean = false,
    val showContent: Boolean = false,     // 是否显示查看/编辑界面
    // 新建文件弹窗
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val newFilePath: String = "",
    // 操作栏（长按弹出）
    val selectedScript: ScriptFile? = null,
    val showActionMenu: Boolean = false,
    // 删除确认
    val showDeleteConfirm: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
