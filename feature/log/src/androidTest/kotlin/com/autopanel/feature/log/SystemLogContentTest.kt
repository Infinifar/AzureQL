package com.autopanel.feature.log

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SystemLogContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun daySelectionIsForwardedWithoutLoadingAllDays() {
        var selected: String? = null
        composeRule.setContent {
            MaterialTheme {
                SystemLogContent(
                    state = SystemLogUiState(
                        days = listOf("2026-09-09", "2026-09-08"),
                        isInitializing = false
                    ),
                    onBack = {},
                    onDayClick = { selected = it },
                    onRefresh = {},
                    onDismissLog = {},
                    snackbarHostState = remember { SnackbarHostState() }
                )
            }
        }

        composeRule.onNodeWithText("2026-09-08").assertIsDisplayed().performClick()
        assertEquals("2026-09-08", selected)
    }

    @Test
    fun truncatedResponseShowsTotalAndContent() {
        composeRule.setContent {
            MaterialTheme {
                SystemLogContent(
                    state = SystemLogUiState(
                        days = listOf("2026-09-09"),
                        isInitializing = false,
                        selectedDay = "2026-09-09",
                        content = "server started",
                        totalBytes = 2_097_152,
                        truncated = true,
                        showLogSheet = true
                    ),
                    onBack = {}, onDayClick = {}, onRefresh = {}, onDismissLog = {}
                )
            }
        }

        composeRule.onNodeWithText("server started").assertIsDisplayed()
        composeRule.onNodeWithText("2.0 MiB", substring = true).assertIsDisplayed()
    }
}
