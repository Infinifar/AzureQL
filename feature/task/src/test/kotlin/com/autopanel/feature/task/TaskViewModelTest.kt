package com.autopanel.feature.task

import android.content.Context
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.TaskInfo
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>()
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getCachedTasks(any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pin button optimistically updates task and calls pin endpoint`() = runTest(dispatcher) {
        val original = TaskInfo(id = 9, name = "daily", isPinned = 0)
        val pinned = original.copy(isPinned = 1)
        coEvery { repository.getTasks(any(), any(), any()) } returnsMany listOf(
            Result.success(listOf(original) to 1),
            Result.success(listOf(pinned) to 1)
        )
        coEvery { repository.pinTasks(listOf(9)) } returns Result.success(Unit)
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.togglePin(original)
        assertTrue(viewModel.uiState.value.tasks.single().pinned)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.pinTasks(listOf(9)) }
        assertTrue(viewModel.uiState.value.tasks.single().pinned)
    }
}
