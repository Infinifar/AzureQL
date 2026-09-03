package com.autopanel.feature.dependency

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DepItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun queuedDependencyShowsNeutralTerminalAwareLabelAndBlocksRepeatAction() {
        var reinstalls = 0
        setItem(
            dependency = DependencyInfo(id = 1, name = "queued-package", status = DependencyStatus.QUEUED),
            onReinstall = { reinstalls += 1 }
        )

        composeRule.onNodeWithText("队列中").assertIsDisplayed()
        composeRule.onNodeWithText("queued-package").assertIsNotEnabled()

        assertEquals(0, reinstalls)
    }

    @Test
    fun installingDependencyBlocksRepeatAction() {
        var reinstalls = 0
        setItem(
            dependency = DependencyInfo(id = 2, name = "installing-package", status = DependencyStatus.INSTALLING),
            onReinstall = { reinstalls += 1 }
        )

        composeRule.onNodeWithText("安装中").assertIsDisplayed()
        composeRule.onNodeWithText("installing-package").assertIsNotEnabled()

        assertEquals(0, reinstalls)
    }

    @Test
    fun installedDependencyCanRequestReinstall() {
        var reinstalls = 0
        setItem(
            dependency = DependencyInfo(id = 3, name = "installed-package", status = DependencyStatus.INSTALLED),
            onReinstall = { reinstalls += 1 }
        )

        composeRule.onNodeWithText("已安装").assertIsDisplayed()
        composeRule.onNodeWithText("installed-package").performClick()

        assertEquals(1, reinstalls)
    }

    @Test
    fun cancelledDependencyIsShownAsTerminalAndCanBeRetried() {
        var reinstalls = 0
        setItem(
            dependency = DependencyInfo(id = 4, name = "cancelled-package", status = DependencyStatus.CANCELLED),
            onReinstall = { reinstalls += 1 }
        )

        composeRule.onNodeWithText("已取消").assertIsDisplayed()
        composeRule.onNodeWithText("cancelled-package").performClick()

        assertEquals(1, reinstalls)
    }

    private fun setItem(
        dependency: DependencyInfo,
        onReinstall: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                DepItem(
                    dep = dependency,
                    isBatchMode = false,
                    isSelected = false,
                    onToggleSelection = {},
                    onReinstall = onReinstall,
                    onDelete = {},
                    onShowLog = {}
                )
            }
        }
    }
}
