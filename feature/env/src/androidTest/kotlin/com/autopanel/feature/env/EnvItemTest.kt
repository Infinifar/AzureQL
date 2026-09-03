package com.autopanel.feature.env

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.model.EnvStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EnvItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pinnedItemExposesUnpinAndForwardsAction() {
        var pinClicks = 0
        setItem(
            env = EnvInfo(id = 1, name = "TOKEN", status = EnvStatus.ENABLED, isPinned = 1),
            onTogglePin = { pinClicks += 1 }
        )

        composeRule.onNodeWithContentDescription("取消置顶").performClick()

        assertEquals(1, pinClicks)
    }

    @Test
    fun batchModeShowsDisabledStateAndForwardsSelection() {
        var selections = 0
        setItem(
            env = EnvInfo(id = 2, name = "DISABLED_TOKEN", status = EnvStatus.DISABLED),
            isBatchMode = true,
            onToggleSelection = { selections += 1 }
        )

        composeRule.onNodeWithText("已禁用").assertIsDisplayed()
        composeRule.onNodeWithText("DISABLED_TOKEN").performClick()

        assertEquals(1, selections)
    }

    @Test
    fun longPressForwardsEditWithoutChangingStatus() {
        var longPresses = 0
        var statusChanges = 0
        setItem(
            env = EnvInfo(id = 3, name = "EDIT_ME", status = EnvStatus.ENABLED),
            onLongPress = { longPresses += 1 },
            onToggleStatus = { statusChanges += 1 }
        )

        composeRule.onNodeWithText("EDIT_ME").performTouchInput { longClick() }

        assertEquals(1, longPresses)
        assertEquals(0, statusChanges)
    }

    private fun setItem(
        env: EnvInfo,
        isBatchMode: Boolean = false,
        onToggleSelection: () -> Unit = {},
        onToggleStatus: () -> Unit = {},
        onTogglePin: () -> Unit = {},
        onLongPress: () -> Unit = {}
    ) {
        composeRule.setContent {
            MaterialTheme {
                EnvItem(
                    env = env,
                    isBatchMode = isBatchMode,
                    isSelected = false,
                    onToggleSelection = onToggleSelection,
                    onToggleStatus = onToggleStatus,
                    onTogglePin = onTogglePin,
                    onLongPress = onLongPress
                )
            }
        }
    }
}
