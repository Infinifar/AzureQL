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

    @Test
    fun busyStateDisablesNewTransfers() {
        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(operation = BackupOperation.EXPORTING),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onToggleModule = {},
                    onExport = {},
                    onImport = {}
                )
            }
        }

        composeRule.onNodeWithText("导出到文件").assertIsNotEnabled()
        composeRule.onNodeWithText("选择备份文件").assertIsNotEnabled()
    }

    @Test
    fun activationCannotBeCancelledButCanContinueInBackground() {
        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(operation = BackupOperation.ACTIVATING_RESTORE),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onToggleModule = {},
                    onExport = {},
                    onImport = {}
                )
            }
        }

        composeRule.onNodeWithText("正在激活恢复数据…").assertIsDisplayed()
        composeRule.onNodeWithText("取消传输").assertDoesNotExist()
        composeRule.onNodeWithText("在后台继续").assertIsDisplayed()
    }

    @Test
    fun waitingStageShowsHealthCheckAttempt() {
        composeRule.setContent {
            MaterialTheme {
                BackupScreenContent(
                    state = BackupUiState(
                        operation = BackupOperation.WAITING_FOR_SERVICE,
                        healthCheckAttempt = 3
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onToggleModule = {},
                    onExport = {},
                    onImport = {}
                )
            }
        }

        composeRule.onNodeWithText("正在等待青龙服务恢复… 3/30").assertIsDisplayed()
        composeRule.onNodeWithText("取消传输").assertDoesNotExist()
    }
}
