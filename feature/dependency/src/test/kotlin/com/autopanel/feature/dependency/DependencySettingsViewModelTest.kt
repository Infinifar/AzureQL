package com.autopanel.feature.dependency

import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.model.DependencyMirrorEvent
import com.autopanel.core.model.DependencySetting
import com.autopanel.core.model.SystemConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DependencySettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ConfigRepository>()
    private val mirrorEvents = MutableSharedFlow<DependencyMirrorEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getSystemConfig() } returns Result.success(SystemConfig())
        every { repository.observeDependencyMirrorTasks() } returns mirrorEvents
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save keeps each setting result when one request fails`() = runTest(dispatcher) {
        DependencySetting.entries.forEach { setting ->
            coEvery { repository.updateDependencySetting(setting, any()) } returns
                if (setting == DependencySetting.PYTHON_MIRROR) {
                    Result.failure(Exception("Python mirror rejected"))
                } else {
                    Result.success(Unit)
                }
        }
        val viewModel = DependencySettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertEquals(
            DependencySettingSaveStatus.ERROR,
            state.settingStates.getValue(DependencySetting.PYTHON_MIRROR).status
        )
        assertEquals(
            DependencySettingSaveStatus.SUBMITTED,
            state.settingStates.getValue(DependencySetting.NODE_MIRROR).status
        )
        assertEquals(
            DependencySettingSaveStatus.SUCCESS,
            state.settingStates.getValue(DependencySetting.PROXY).status
        )
    }

    @Test
    fun `completed mirror event updates status and appends log`() = runTest(dispatcher) {
        val viewModel = DependencySettingsViewModel(repository)
        runCurrent()

        mirrorEvents.emit(
            DependencyMirrorEvent.Task(
                setting = DependencySetting.NODE_MIRROR,
                message = "update node mirror end",
                status = "completed"
            )
        )
        runCurrent()

        assertEquals(
            DependencySettingSaveStatus.SUCCESS,
            viewModel.uiState.value.settingStates
                .getValue(DependencySetting.NODE_MIRROR).status
        )
        assertEquals(
            listOf("Node.js 镜像: update node mirror end"),
            viewModel.uiState.value.taskLog
        )
    }
}
