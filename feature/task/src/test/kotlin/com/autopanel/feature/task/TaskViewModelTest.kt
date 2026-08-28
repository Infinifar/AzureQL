package com.autopanel.feature.task

import android.content.Context
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskScheduleType
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

    @Test
    fun `submit edit sends advanced task fields to repository`() = runTest(dispatcher) {
        coEvery { repository.getTasks(any(), any(), any()) } returns Result.success(emptyList<TaskInfo>() to 0)
        coEvery { repository.addTask(any()) } returns Result.success(Unit)
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()
        val draft = TaskDraft(
            name = "boot task",
            command = "python3 boot.py",
            scheduleType = TaskScheduleType.BOOT,
            labels = listOf("system"),
            allowMultipleInstances = true,
            logName = "boot_task",
            workDir = "boot",
            taskBefore = "echo before",
            taskAfter = "echo after"
        )

        viewModel.submitEdit(draft)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.addTask(match {
                it.scheduleType == TaskScheduleType.BOOT &&
                    it.labels == listOf("system") &&
                    it.allowMultipleInstances &&
                    it.logName == "boot_task" &&
                    it.workDir == "boot" &&
                    it.taskBefore == "echo before" &&
                    it.taskAfter == "echo after"
            })
        }
    }

    @Test
    fun `submit edit rejects task command in before hook`() = runTest(dispatcher) {
        coEvery { repository.getTasks(any(), any(), any()) } returns Result.success(emptyList<TaskInfo>() to 0)
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.submitEdit(
            TaskDraft(
                name = "unsafe",
                command = "python3 safe.py",
                schedule = "0 0 * * *",
                taskBefore = "echo prepare; task unsafe.py"
            )
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.addTask(any()) }
    }
}
