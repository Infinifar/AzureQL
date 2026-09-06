package com.autopanel.feature.script

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.SubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ScriptTreeItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fileLongPressForwardsTheExactScriptWithoutOpeningIt() {
        val script = ScriptFile(title = "daily.py", key = "jobs/daily.py", type = "file")
        var opened: ScriptFile? = null
        var longPressed: ScriptFile? = null
        setItem(script, onClick = { opened = it }, onLongClick = { longPressed = it })

        composeRule.onNodeWithText("daily.py").performTouchInput { longClick() }

        assertEquals(script, longPressed)
        assertNull(opened)
    }

    @Test
    fun fileClickOpensWithoutTriggeringLongPress() {
        val script = ScriptFile(title = "daily.py", key = "jobs/daily.py", type = "file")
        var opened: ScriptFile? = null
        var longPressed: ScriptFile? = null
        setItem(script, onClick = { opened = it }, onLongClick = { longPressed = it })

        composeRule.onNodeWithText("daily.py").performClick()

        assertEquals(script, opened)
        assertNull(longPressed)
    }

    @Test
    fun subscriptionCardHidesRepositoryUrlAndKeepsPrimaryDetails() {
        composeRule.setContent {
            MaterialTheme {
                SubscriptionsContent(
                    subscriptions = listOf(
                        SubscriptionInfo(
                            id = 7,
                            name = "Daily scripts",
                            url = "https://example.invalid/private/repository",
                            schedule = "0 0 * * *",
                            scheduleType = "crontab",
                            status = 1
                        )
                    ),
                    isLoading = false,
                    isRefreshing = false,
                    busyIds = emptySet(),
                    onRefresh = {},
                    onEdit = {},
                    onDelete = {},
                    onToggleEnabled = {},
                    onRunOrStop = {},
                    onOpenLog = {}
                )
            }
        }

        composeRule.onNodeWithText("Daily scripts").assertIsDisplayed()
        composeRule.onNodeWithText("0 0 * * *").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.invalid/private/repository").assertDoesNotExist()
    }

    private fun setItem(
        script: ScriptFile,
        onClick: (ScriptFile) -> Unit,
        onLongClick: (ScriptFile) -> Unit
    ) {
        composeRule.setContent {
            MaterialTheme {
                ScriptTreeItem(
                    file = script,
                    depth = 0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    actions = EmptyActions
                )
            }
        }
    }

    private companion object {
        val EmptyActions: @Composable (ScriptFile) -> Unit = {}
    }
}
