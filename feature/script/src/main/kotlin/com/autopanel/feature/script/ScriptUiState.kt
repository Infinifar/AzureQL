package com.autopanel.feature.script

import com.autopanel.core.model.ScriptFile
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptDraftPage
import com.autopanel.core.model.SubscriptionDraft
import com.autopanel.core.model.SubscriptionInfo

enum class ScriptSection { SCRIPTS, SUBSCRIPTIONS }
enum class ScriptContentMode { INLINE, PAGED, UNAVAILABLE }

data class SavedScriptDocument(val uri: String, val filename: String)

data class SubscriptionLogUiState(
    val subscription: SubscriptionInfo,
    val content: String = "",
    val offset: Long = 0,
    val nextOffset: Long = 0,
    val total: Long = 0,
    val truncated: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val error: String? = null
) {
    val canLoadOlder: Boolean get() = offset > 0
    val canLoadNewer: Boolean get() = nextOffset < total
}

data class ScriptUiState(
    val section: ScriptSection = ScriptSection.SCRIPTS,
    val scripts: List<ScriptFile> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isImportingScripts: Boolean = false,
    // 编辑
    val editingFilename: String = "",
    val editingPath: String = "",
    val editContent: String = "",
    val originalContent: String = "",
    val isEditing: Boolean = false,       // 编辑模式
    val isLoadingContent: Boolean = false,
    val isSavingContent: Boolean = false,
    val contentLoadFailed: Boolean = false,
    val isContentReadOnly: Boolean = false,
    val contentWarning: String? = null,
    val hasUtf8Bom: Boolean = false,
    val showContent: Boolean = false,     // 是否显示查看/编辑界面
    val contentMode: ScriptContentMode = ScriptContentMode.INLINE,
    val draft: ScriptDraft? = null,
    val previewPage: ScriptDraftPage? = null,
    val isLoadingPreviewPage: Boolean = false,
    val hasLocalDraftChanges: Boolean = false,
    val isPendingUpload: Boolean = false,
    val externalEditorSnapshotBytes: Long? = null,
    val showOverwriteConfirm: Boolean = false,
    val showDiscardDraftConfirm: Boolean = false,
    // 新建文件弹窗
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val newFilePath: String = "",
    // 操作栏（长按弹出）
    val selectedScript: ScriptFile? = null,
    val showActionMenu: Boolean = false,
    val isDownloadingScript: Boolean = false,
    val downloadedScript: SavedScriptDocument? = null,
    // 删除确认
    val showDeleteConfirm: Boolean = false,
    // 订阅管理
    val subscriptions: List<SubscriptionInfo> = emptyList(),
    val hasLoadedSubscriptions: Boolean = false,
    val isLoadingSubscriptions: Boolean = false,
    val isRefreshingSubscriptions: Boolean = false,
    val showSubscriptionEditor: Boolean = false,
    val subscriptionDraft: SubscriptionDraft = SubscriptionDraft(),
    val isSavingSubscription: Boolean = false,
    val pendingDeleteSubscription: SubscriptionInfo? = null,
    val busySubscriptionIds: Set<Int> = emptySet(),
    val subscriptionLog: SubscriptionLogUiState? = null
)

sealed interface ScriptEvent {
    data class Message(val text: String) : ScriptEvent
}
