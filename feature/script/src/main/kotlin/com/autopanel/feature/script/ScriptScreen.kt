package com.autopanel.feature.script

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptScreen(
    viewModel: ScriptViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val englishUi = isEnglishUi()
    val pathCopiedMessage = localizedText("脚本路径已复制", "Script path copied")
    val logCopiedMessage = localizedText("订阅日志已复制", "Subscription log copied")
    val openSavedScriptLabel = localizedText("打开", "Open")
    val savedScriptPrefix = localizedText("脚本已保存", "Script saved")
    val importScriptsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importScripts(uris) }
    val downloadScriptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) viewModel.cancelScriptDownloadSelection()
        else viewModel.downloadScript(uri)
    }
    val externalEditorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onExternalEditorReturned()
    }

    val currentEnglishUi by rememberUpdatedState(isEnglishUi())

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is ScriptEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.text, currentEnglishUi)
                )
            }
        }
    }
    LaunchedEffect(state.downloadedScript, englishUi) {
        state.downloadedScript?.let { saved ->
            val result = snackbarHostState.showSnackbar(
                message = "$savedScriptPrefix: ${saved.filename}",
                actionLabel = openSavedScriptLabel
            )
            if (result == SnackbarResult.ActionPerformed) {
                runCatching {
                    val uri = Uri.parse(saved.uri)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, context.contentResolver.getType(uri) ?: "text/plain")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                }
            }
            viewModel.clearDownloadedScript()
        }
    }

    if (state.isDownloadingScript) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(localizedText("正在保存脚本", "Saving script")) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(state.selectedScript?.title ?: localizedText("脚本", "Script"))
                }
            },
            confirmButton = {}
        )
    }

    if (state.showNewFileDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNewFileDialog,
            title = { Text(localizedText("新建脚本", "New script")) },
            text = {
                OutlinedTextField(
                    value = state.newFileName, onValueChange = viewModel::onNewFileNameChanged,
                    label = { Text(localizedText("文件名", "File name")) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::createNewFile, enabled = state.newFileName.isNotBlank()) {
                    Text(localizedText("创建", "Create"))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissNewFileDialog) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirm,
            title = { Text(localizedText("确认删除", "Delete script?")) },
            text = {
                Text(
                    localizedText(
                        "确定要删除「${state.selectedScript?.title}」吗？",
                        "Delete “${state.selectedScript?.title}”?"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(localizedText("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirm) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    if (state.showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOverwriteDraft,
            title = { Text(localizedText("服务端脚本已变化", "Server script changed")) },
            text = {
                Text(
                    localizedText(
                        "下载本地副本后，服务端脚本又被修改。继续会以本地版本覆盖服务端内容。",
                        "The server script changed after this local copy was downloaded. Continuing will overwrite it with the local version."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOverwriteDraft) {
                    Text(localizedText("仍然覆盖", "Overwrite anyway"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOverwriteDraft) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    if (state.showDiscardDraftConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscardDraftConfirm,
            title = { Text(localizedText("放弃本地修改？", "Discard local changes?")) },
            text = {
                Text(
                    localizedText(
                        "尚未回传的本地修改会被删除，服务端脚本不会改变。",
                        "Local changes that have not been uploaded will be deleted. The server script will not change."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDiscardDraft) {
                    Text(localizedText("放弃修改", "Discard"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDiscardDraftConfirm) {
                    Text(localizedText("继续编辑", "Keep editing"))
                }
            }
        )
    }

    if (state.showContent) {
        ScriptContentDialog(
            state = state,
            viewModel = viewModel,
            onOpenExternalEditor = {
                state.draft?.let { draft ->
                    val uri = Uri.parse(draft.editorUri)
                    val intent = Intent(Intent.ACTION_EDIT).apply {
                        setDataAndType(uri, "text/plain")
                        clipData = ClipData.newRawUri(draft.filename, uri)
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    try {
                        externalEditorLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.onExternalEditorUnavailable()
                    }
                }
            }
        )
    }

    if (state.showSubscriptionEditor) {
        SubscriptionEditorDialog(
            draft = state.subscriptionDraft,
            isSaving = state.isSavingSubscription,
            onDraftChange = viewModel::onSubscriptionDraftChanged,
            onSave = viewModel::saveSubscription,
            onDismiss = viewModel::dismissSubscriptionEditor
        )
    }

    state.subscriptionLog?.let { logState ->
        SubscriptionLogSheet(
            state = logState,
            onDismiss = viewModel::closeSubscriptionLog,
            onRetry = viewModel::retrySubscriptionLog,
            onLoadOlder = viewModel::loadOlderSubscriptionLog,
            onCopy = { content ->
                clipboardManager.setPrimaryClip(ClipData.newPlainText("subscription_log", content))
                Toast.makeText(context, logCopiedMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }

    state.pendingDeleteSubscription?.let { subscription ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteSubscription,
            title = { Text(localizedText("删除订阅", "Delete subscription")) },
            text = {
                Text(
                    localizedText(
                        "确定删除订阅「${subscription.name ?: subscription.alias}」吗？关联任务和脚本将保留。",
                        "Delete “${subscription.name ?: subscription.alias}”? Related tasks and scripts will be kept."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteSubscription) {
                    Text(localizedText("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteSubscription) {
                    Text(localizedText("取消", "Cancel"))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(localizedText("脚本管理", "Scripts")) },
                    actions = {
                        if (state.section == ScriptSection.SCRIPTS) {
                            IconButton(
                                onClick = {
                                    importScriptsLauncher.launch(
                                        arrayOf(
                                            "text/*",
                                            "application/javascript",
                                            "application/json",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                enabled = !state.isImportingScripts
                            ) {
                                if (state.isImportingScripts) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.UploadFile, localizedText("导入现有脚本", "Import scripts"))
                                }
                            }
                            IconButton(onClick = { viewModel.showNewFileDialog() }) {
                                Icon(Icons.Default.Add, localizedText("新建脚本", "New script"))
                            }
                        } else {
                            IconButton(onClick = viewModel::showNewSubscription) {
                                Icon(Icons.Default.Add, localizedText("新建订阅", "New subscription"))
                            }
                        }
                    }
                )
                PrimaryTabRow(
                    selectedTabIndex = if (state.section == ScriptSection.SCRIPTS) 0 else 1
                ) {
                    Tab(
                        selected = state.section == ScriptSection.SCRIPTS,
                        onClick = { viewModel.selectSection(ScriptSection.SCRIPTS) },
                        text = { Text(localizedText("脚本", "Scripts")) }
                    )
                    Tab(
                        selected = state.section == ScriptSection.SUBSCRIPTIONS,
                        onClick = { viewModel.selectSection(ScriptSection.SUBSCRIPTIONS) },
                        text = { Text(localizedText("订阅", "Subscriptions")) }
                    )
                }
            }
        }
    ) { padding ->
        if (state.section == ScriptSection.SCRIPTS) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding)
            ) {
                if (state.scripts.isEmpty() && !state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(localizedText("暂无脚本", "No scripts"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.scripts, key = { it.key ?: it.hashCode().toString() }) { file ->
                        ScriptTreeItem(
                            file = file,
                            depth = 0,
                            onClick = { f ->
                                if (!f.isDirectory) {
                                    viewModel.loadContent(f)
                                }
                            },
                            onLongClick = { file ->
                                if (file.isDirectory) {
                                    viewModel.showActionMenu(file)
                                } else {
                                    clipboardManager.setPrimaryClip(
                                        ClipData.newPlainText("script_path", file.currentScriptPath())
                                    )
                                    Toast.makeText(context, pathCopiedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            actions = { script ->
                                ScriptActionMenu(
                                    file = script,
                                    expanded = state.showActionMenu &&
                                        state.selectedScript?.scriptActionKey() == script.scriptActionKey(),
                                    onOpen = { viewModel.showActionMenu(script) },
                                    onDismiss = viewModel::dismissActionMenu,
                                    onDownload = {
                                        viewModel.prepareScriptDownload()
                                        downloadScriptLauncher.launch(script.title ?: "script.txt")
                                    },
                                    onCreateFile = {
                                        viewModel.dismissActionMenu()
                                        val directory = if (script.isDirectory) {
                                            script.key.orEmpty()
                                        } else {
                                            script.parent.orEmpty()
                                        }
                                        viewModel.showNewFileDialog(directory)
                                    },
                                    onDelete = viewModel::showDeleteConfirm
                                )
                            }
                        )
                    }
                }
            }
        } else {
            SubscriptionsContent(
                subscriptions = state.subscriptions,
                isLoading = state.isLoadingSubscriptions,
                isRefreshing = state.isRefreshingSubscriptions,
                busyIds = state.busySubscriptionIds,
                onRefresh = viewModel::refresh,
                onEdit = viewModel::showEditSubscription,
                onDelete = viewModel::requestDeleteSubscription,
                onToggleEnabled = viewModel::toggleSubscriptionEnabled,
                onRunOrStop = viewModel::runOrStopSubscription,
                onOpenLog = viewModel::openSubscriptionLog,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ScriptContentDialog(
    state: ScriptUiState,
    viewModel: ScriptViewModel,
    onOpenExternalEditor: () -> Unit
) {
    val englishUi = isEnglishUi()
    Dialog(
        onDismissRequest = viewModel::closeContent,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::closeContent) {
                        Icon(Icons.Default.Close, localizedText("关闭", "Close"))
                    }
                    Text(
                        state.editingFilename,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!state.isLoadingContent && !state.contentLoadFailed) {
                        when {
                            state.isSavingContent -> CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            state.contentMode == ScriptContentMode.PAGED && !state.isContentReadOnly -> {
                                TextButton(onClick = onOpenExternalEditor) {
                                    Text(localizedText("本地编辑", "Edit locally"))
                                }
                                if (state.hasLocalDraftChanges) {
                                    TextButton(onClick = viewModel::saveContent) {
                                        Text(localizedText("上传修改", "Upload changes"))
                                    }
                                }
                            }
                            !state.isEditing -> {
                                if (!state.isContentReadOnly) {
                                    TextButton(onClick = viewModel::enterEditMode) {
                                        Text(localizedText("编辑", "Edit"))
                                    }
                                }
                            }
                            else -> {
                                TextButton(
                                    onClick = viewModel::cancelEdit,
                                    enabled = !state.isSavingContent
                                ) { Text(localizedText("取消", "Cancel")) }
                                TextButton(
                                    onClick = viewModel::saveContent,
                                    enabled = !state.isSavingContent
                                ) {
                                    Text(
                                        localizedText("保存", "Save"),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                when {
                    state.isLoadingContent -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.contentLoadFailed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(localizedText("脚本内容加载失败", "Failed to load script"), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::retryContent) { Text(localizedText("重试", "Retry")) }
                        }
                    }
                    state.isEditing -> OutlinedTextField(
                        value = state.editContent,
                        onValueChange = viewModel::onContentChanged,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    state.contentMode == ScriptContentMode.UNAVAILABLE -> Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            localizedMessage(state.contentWarning.orEmpty(), englishUi),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        state.contentWarning?.let { warning ->
                            Text(
                                localizedMessage(warning, englishUi),
                                color = if (state.isContentReadOnly) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            )
                            HorizontalDivider()
                        }
                        if (state.contentMode == ScriptContentMode.PAGED) {
                            val page = state.previewPage
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = viewModel::previousPreviewPage,
                                    enabled = !state.isLoadingPreviewPage && (page?.index ?: 0) > 0
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.NavigateBefore,
                                        localizedText("上一段", "Previous section")
                                    )
                                }
                                Text(
                                    localizedText(
                                        "第 ${(page?.index ?: 0) + 1} / ${page?.totalPages ?: 1} 段",
                                        "Section ${(page?.index ?: 0) + 1} / ${page?.totalPages ?: 1}"
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::nextPreviewPage,
                                    enabled = !state.isLoadingPreviewPage &&
                                        (page?.index ?: 0) + 1 < (page?.totalPages ?: 1)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.NavigateNext,
                                        localizedText("下一段", "Next section")
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                        if (state.isLoadingPreviewPage) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            SelectionContainer {
                                Text(
                                    text = state.editContent.ifEmpty { localizedText("（空文件）", "(empty file)") },
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScriptTreeItem(
    file: ScriptFile,
    depth: Int,
    modifier: Modifier = Modifier,
    onClick: (ScriptFile) -> Unit,
    onLongClick: (ScriptFile) -> Unit,
    actions: @Composable (ScriptFile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isDir = file.isDirectory
    val indent = (depth * 24).dp

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isDir) expanded = !expanded
                        else onClick(file)
                    },
                    onLongClick = { onLongClick(file) }
                )
                .padding(start = 16.dp + indent, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDir) {
                Icon(
                    if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(file.title ?: "--", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    file.title ?: "--", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            actions(file)
        }
        val children = file.children
        if (isDir && !children.isNullOrEmpty()) {
            AnimatedVisibility(expanded) {
                Column {
                    val sorted = children.sortedWith(
                        compareByDescending<ScriptFile> { it.isDirectory }.thenBy { it.title }
                    )
                    sorted.forEach { child ->
                        ScriptTreeItem(
                            file = child,
                            depth = depth + 1,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            actions = actions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptActionMenu(
    file: ScriptFile,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCreateFile: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = onOpen) {
            Icon(Icons.Default.MoreVert, localizedText("更多操作", "More actions"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            if (!file.isDirectory) {
                DropdownMenuItem(
                    text = { Text(localizedText("下载", "Download")) },
                    leadingIcon = { Icon(Icons.Default.Download, null) },
                    onClick = onDownload
                )
            }
            DropdownMenuItem(
                text = { Text(localizedText("新建文件", "New file")) },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = onCreateFile
            )
            DropdownMenuItem(
                text = { Text(localizedText("删除", "Delete")) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = onDelete
            )
        }
    }
}

internal fun ScriptFile.currentScriptPath(): String {
    val rawPath = key?.takeIf(String::isNotBlank)
        ?: listOfNotNull(parent?.takeIf(String::isNotBlank), title?.takeIf(String::isNotBlank))
            .joinToString("/")
    return rawPath
        .replace('\\', '/')
        .replace(Regex("/+"), "/")
        .removePrefix("./")
}

internal fun ScriptFile.scriptActionKey(): String =
    "${if (isDirectory) "directory" else "file"}:${currentScriptPath()}"
