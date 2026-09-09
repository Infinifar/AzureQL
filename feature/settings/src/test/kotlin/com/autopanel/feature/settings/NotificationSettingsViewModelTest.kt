package com.autopanel.feature.settings

import com.autopanel.core.domain.NotificationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<NotificationRepository>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `valid config sends server test and preserves unknown fields`() = runTest(dispatcher) {
        val original = JsonObject(mapOf(
            "type" to JsonPrimitive("gotify"),
            "gotifyUrl" to JsonPrimitive("https://old"),
            "gotifyToken" to JsonPrimitive("old-token"),
            "futureOption" to JsonPrimitive("keep")
        ))
        val payload = slot<JsonObject>()
        coEvery { repository.getConfig() } returns Result.success(original)
        coEvery { repository.testAndSave(capture(payload)) } returns Result.success(Unit)
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateField("gotifyUrl", "https://new")
        viewModel.testAndSave()
        advanceUntilIdle()

        assertEquals(JsonPrimitive("https://new"), payload.captured["gotifyUrl"])
        assertEquals(JsonPrimitive("keep"), payload.captured["futureOption"])
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `missing required field does not call server`() = runTest(dispatcher) {
        coEvery { repository.getConfig() } returns Result.success(JsonObject(mapOf("type" to JsonPrimitive("gotify"))))
        val viewModel = NotificationSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.testAndSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.testAndSave(any()) }
        assertTrue("gotifyUrl" in viewModel.uiState.value.fieldErrors)
        assertTrue("gotifyToken" in viewModel.uiState.value.fieldErrors)
    }
}
