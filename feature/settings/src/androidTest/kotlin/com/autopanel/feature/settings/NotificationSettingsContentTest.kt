package com.autopanel.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationSettingsContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun providerSelectionIsForwarded() {
        var selected: String? = null
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsContent(
                    state = NotificationSettingsUiState(isLoading = false, selectedType = "closed"),
                    onBack = {},
                    onProviderSelected = { selected = it },
                    onFieldChanged = { _, _ -> },
                    onSave = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText("关闭通知").performClick()
        composeRule.onNodeWithText("Gotify").assertIsDisplayed().performClick()
        assertEquals("gotify", selected)
    }

    @Test
    fun unsupportedProviderIsShownWithoutEditableFields() {
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsContent(
                    state = NotificationSettingsUiState(
                        isLoading = false,
                        selectedType = "future-provider",
                        unsupportedType = "future-provider"
                    ),
                    onBack = {}, onProviderSelected = {}, onFieldChanged = { _, _ -> }, onSave = {}, onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText("future-provider").assertIsDisplayed()
        composeRule.onNodeWithText("发送测试通知并保存").assertDoesNotExist()
    }

    @Test
    fun savingIndicatorIsSquareAndContainedByDisabledButton() {
        composeRule.setContent {
            MaterialTheme {
                NotificationSettingsContent(
                    state = NotificationSettingsUiState(
                        isLoading = false,
                        isSaving = true,
                        selectedType = "closed"
                    ),
                    onBack = {}, onProviderSelected = {}, onFieldChanged = { _, _ -> }, onSave = {}, onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("notification_test_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("notification_save_progress")
            .assertWidthIsEqualTo(20.dp)
            .assertHeightIsEqualTo(20.dp)
    }
}
