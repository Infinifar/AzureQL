package com.autopanel.feature.script

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
fun ScriptEditorScreen(
    filename: String,
    path: String,
    onBack: () -> Unit,
    viewModel: ScriptViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentEnglishUi by rememberUpdatedState(isEnglishUi())

    LaunchedEffect(filename, path) {
        viewModel.loadContent(filename, path)
    }
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is ScriptEvent.Message -> snackbarHostState.showSnackbar(
                    localizedMessage(event.text, currentEnglishUi)
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(filename) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, localizedText("返回", "Back"))
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::saveContent) {
                            Icon(Icons.Default.Save, localizedText("保存", "Save"))
                        }
                    } else {
                        IconButton(onClick = viewModel::enterEditMode) {
                            Icon(Icons.Default.Edit, localizedText("编辑", "Edit"))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalDivider()
            if (state.isLoadingContent) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.isEditing) {
                OutlinedTextField(
                    value = state.editContent,
                    onValueChange = viewModel::onContentChanged,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
                ) {
                    Text(
                        state.editContent.ifEmpty { localizedText("（空文件）", "(empty file)") },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
