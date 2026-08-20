package com.autopanel.feature.script

import android.content.Context
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.model.ScriptFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ScriptRepository>()
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getScripts() } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `current script path prefers key and normalizes separators`() {
        val script = ScriptFile(
            title = "task.py",
            key = ".\\nested\\task.py",
            parent = "ignored"
        )

        assertEquals("nested/task.py", script.currentScriptPath())
    }

    @Test
    fun `current script path falls back to parent and title`() {
        val script = ScriptFile(title = "task.py", parent = "nested/jobs")

        assertEquals("nested/jobs/task.py", script.currentScriptPath())
    }

    @Test
    fun `utf8 bom is hidden in editor and restored when saving`() = runTest(dispatcher) {
        coEvery { repository.getScriptContent("task.py", "jobs") } returns
            Result.success("\uFEFF你好")
        coEvery { repository.updateScript("task.py", "jobs", "\uFEFF你好") } returns
            Result.success(Unit)
        val viewModel = ScriptViewModel(repository, context)
        advanceUntilIdle()

        viewModel.loadContent("task.py", "jobs")
        advanceUntilIdle()

        assertEquals("你好", viewModel.uiState.value.editContent)
        assertTrue(viewModel.uiState.value.hasUtf8Bom)
        assertFalse(viewModel.uiState.value.isContentReadOnly)

        viewModel.enterEditMode()
        viewModel.saveContent()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.updateScript("task.py", "jobs", "\uFEFF你好")
        }
    }

    @Test
    fun `replacement characters make content read only`() = runTest(dispatcher) {
        coEvery { repository.getScriptContent("legacy.py", "") } returns
            Result.success("broken\uFFFDtext")
        val viewModel = ScriptViewModel(repository, context)
        advanceUntilIdle()

        viewModel.loadContent("legacy.py", "")
        advanceUntilIdle()
        viewModel.enterEditMode()

        assertTrue(viewModel.uiState.value.isContentReadOnly)
        assertFalse(viewModel.uiState.value.isEditing)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("UTF-8"))
    }
}
