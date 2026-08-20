package com.autopanel.app.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autopanel.core.ui.i18n.localizedText
import com.autopanel.core.ui.i18n.isEnglishUi
import com.autopanel.core.ui.i18n.localizedMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onBack: () -> Unit, viewModel: ConfigViewModel = hiltViewModel()) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val editing by viewModel.isEditing.collectAsStateWithLifecycle()
    val editContent by viewModel.editContent.collectAsStateWithLifecycle()
    val englishUi = isEnglishUi()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localizedText("配置文件", "Configuration")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                    }
                },
                actions = {
                    if (editing) {
                        IconButton(onClick = { viewModel.saveContent() }) {
                            Icon(Icons.Default.Save, localizedText("保存", "Save"))
                        }
                    } else {
                        IconButton(onClick = { viewModel.enterEditMode() }) {
                            Icon(Icons.Default.Edit, localizedText("编辑", "Edit"))
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { viewModel.loadConfig() },
            modifier = Modifier.padding(padding)
        ) {
            if (content == null && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(localizedText("暂无配置内容", "No configuration content"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(
                Modifier.fillMaxSize().padding(16.dp)
            ) {
                if (editing) {
                    Text("config.sh", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = viewModel::onContentChanged,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("config.sh", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            content?.let { localizedMessage(it, englishUi) }
                                ?: localizedText("加载中...", "Loading…"),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
