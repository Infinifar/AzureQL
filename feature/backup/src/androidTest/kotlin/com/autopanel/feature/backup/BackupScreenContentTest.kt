package com.autopanel.feature.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.autopanel.core.model.BackupModule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BackupScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun baseModuleIsAlwaysSelectedAndDisabled() {
        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onToggleModule = {},
                    onExport = {},
                    onImport = {}
                )
            }
        }

        composeRule.onNodeWithTag("backup_module_base")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun moduleAndExportActionsAreForwarded() {
        var toggled: BackupModule? = null
        var exportClicks = 0
        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onToggleModule = { toggled = it },
                    onExport = { exportClicks += 1 },
                    onImport = {}
                )
            }
        }

        composeRule.onNodeWithTag("backup_module_log").performClick()
        composeRule.onNodeWithText("导出到文件").performClick()

        assertEquals(BackupModule.LOGS, toggled)
        assertEquals(1, exportClicks)
    }

    @Test
    fun progressOverlayIsVisibleWithoutScrolling() {
        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(
                        operation = BackupOperation.IMPORTING,
                        transferredBytes = 512,
                        totalBytes = 1024
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onToggleModule = {},
                    onExport = {},
                    onImport = {}
                )
            }
        }

        composeRule.onNodeWithTag("backup_progress_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("正在上传备份…").assertIsDisplayed()
    }
}
