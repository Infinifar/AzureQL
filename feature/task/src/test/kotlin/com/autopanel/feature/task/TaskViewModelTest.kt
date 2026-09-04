package com.autopanel.feature.task

import android.content.Context
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskLogChunk
import com.autopanel.core.model.TaskScheduleType
import com.autopanel.core.model.TaskStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
class TaskViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>()
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getCachedTasks(any(), any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pin button optimistically updates task and calls pin endpoint`() = runTest(dispatcher) {
        val original = TaskInfo(id = 9, name = "daily", isPinned = 0)
        val pinned = original.copy(isPinned = 1)
        coEvery { repository.getTasks(any(), any(), any(), any()) } returnsMany listOf(
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
        coEvery { repository.getTasks(any(), any(), any(), any()) } returns Result.success(emptyList<TaskInfo>() to 0)
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
        coEvery { repository.getTasks(any(), any(), any(), any()) } returns Result.success(emptyList<TaskInfo>() to 0)
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

    @Test
    fun `label selection reloads server page and keeps discovered labels`() = runTest(dispatcher) {
        val all = listOf(
            TaskInfo(id = 1, name = "daily", labels = listOf("daily", "reward")),
            TaskInfo(id = 2, name = "news", labels = listOf("news"))
        )
        val filtered = listOf(all.first())
        coEvery { repository.getTasks("", 1, 50, emptySet()) } returns Result.success(all to 2)
        coEvery { repository.getTasks("", 1, 50, setOf("daily")) } returns Result.success(filtered to 1)
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.toggleLabelFilter("daily")
        advanceUntilIdle()

        assertEquals(setOf("daily"), viewModel.uiState.value.selectedLabels)
        assertEquals(listOf("daily", "news", "reward"), viewModel.uiState.value.availableLabels)
        assertEquals(listOf(1), viewModel.uiState.value.tasks.mapNotNull(TaskInfo::id))
        coVerify(exactly = 1) { repository.getTasks("", 1, 50, setOf("daily")) }
    }

    @Test
    fun `label manager blocks deleting a referenced label`() = runTest(dispatcher) {
        val tasks = listOf(
            TaskInfo(id = 1, name = "daily", labels = listOf("used")),
            TaskInfo(id = 2, name = "weekly", labels = listOf("used", "other"))
        )
        coEvery { repository.getTasks(any(), any(), any(), any()) } returns
            Result.success(tasks to tasks.size)
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.loadLabelSummaries()
        advanceUntilIdle()
        viewModel.deleteUnusedLabel("used")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.labelSummaries.first { it.name == "used" }.referenceCount)
        coVerify(exactly = 0) { repository.updateTask(any()) }
    }

    @Test
    fun `label manager removes an unreferenced discovered label`() = runTest(dispatcher) {
        val staleTask = TaskInfo(id = 1, name = "old", labels = listOf("orphan"))
        coEvery { repository.getTasks("", 1, 50, emptySet()) } returns
            Result.success(listOf(staleTask) to 1)
        coEvery { repository.getTasks("", 1, 100, emptySet()) } returns
            Result.success(emptyList<TaskInfo>() to 0)
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.loadLabelSummaries()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.labelSummaries.single().referenceCount)

        viewModel.deleteUnusedLabel("orphan")
        advanceUntilIdle()

        assertFalse("orphan" in viewModel.uiState.value.availableLabels)
    }

    @Test
    fun `renaming a label updates every referenced task`() = runTest(dispatcher) {
        val oldTask = TaskInfo(id = 8, name = "daily", labels = listOf("old", "keep"))
        val renamedTask = oldTask.copy(labels = listOf("new", "keep"))
        coEvery { repository.getTasks("", 1, 50, emptySet()) } returnsMany listOf(
            Result.success(listOf(oldTask) to 1),
            Result.success(listOf(renamedTask) to 1)
        )
        coEvery { repository.getTasks("", 1, 100, emptySet()) } returns
            Result.success(listOf(oldTask) to 1)
        coEvery { repository.updateTask(any()) } returns Result.success(Unit)
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.renameLabel("old", "new")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.updateTask(match { it.id == 8 && it.labels == listOf("new", "keep") })
        }
        assertFalse("old" in viewModel.uiState.value.availableLabels)
        assertTrue("new" in viewModel.uiState.value.availableLabels)
    }

    @Test
    fun `dismissing log retains rendered payload through exit transition`() = runTest(dispatcher) {
        coEvery { repository.getTasks(any(), any(), any(), any()) } returns
            Result.success(emptyList<TaskInfo>() to 0)
        coEvery {
            repository.getTaskLogChunk(7, null, 65_536, true)
        } returns Result.success(
            TaskLogChunk(
                content = "completed without errors",
                offset = 0,
                nextOffset = 23,
                total = 23,
                truncated = false
            )
        )
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.showLog(TaskInfo(id = 7, name = "daily"))
        advanceUntilIdle()
        viewModel.dismissLog()

        val state = viewModel.uiState.value
        assertFalse(state.showLogSheet)
        assertEquals("completed without errors", state.logContent)
        assertFalse(state.logTruncated)
        assertEquals(null, state.logError)
    }

    @Test
    fun `running task log appends incremental chunks until task is idle`() = runTest(dispatcher) {
        val runningTask = TaskInfo(id = 7, status = 0.0)
        coEvery { repository.getTasks(any(), any(), any(), any()) } returns
            Result.success(listOf(runningTask) to 1)
        coEvery {
            repository.getTaskLogChunk(7, null, 65_536, true)
        } returns Result.success(TaskLogChunk("first line\n", 0, 11, 11, false))
        coEvery {
            repository.getTaskLogChunk(7, 11, 65_536, false)
        } returns Result.success(TaskLogChunk("second line\n", 11, 23, 23, false))
        coEvery {
            repository.getTaskLogChunk(7, 23, 65_536, false)
        } returns Result.success(TaskLogChunk("final line\n", 23, 34, 34, false))
        coEvery { repository.getTask(7) } returnsMany listOf(
            Result.success(TaskInfo(id = 7, status = 0.0)),
            Result.success(TaskInfo(id = 7, status = 1.0))
        )
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.showLog(runningTask)
        runCurrent()
        assertEquals("first line\n", viewModel.uiState.value.logContent)
        assertTrue(viewModel.uiState.value.logStreaming)

        advanceTimeBy(2_000)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals("first line\nsecond line\nfinal line\n", viewModel.uiState.value.logContent)
        assertFalse(viewModel.uiState.value.logStreaming)
        assertEquals(TaskStatus.IDLE, viewModel.uiState.value.tasks.single().statusCode)
        coVerify(exactly = 1) { repository.getTaskLogChunk(7, 11, 65_536, false) }
        coVerify(exactly = 1) { repository.getTaskLogChunk(7, 23, 65_536, false) }
        coVerify(exactly = 2) { repository.getTask(7) }
    }

    @Test
    fun `dismissing running task log cancels future polling`() = runTest(dispatcher) {
        coEvery { repository.getTasks(any(), any(), any(), any()) } returns
            Result.success(emptyList<TaskInfo>() to 0)
        coEvery {
            repository.getTaskLogChunk(7, null, 65_536, true)
        } returns Result.success(TaskLogChunk("running\n", 0, 8, 8, false))
        coEvery { repository.getTask(7) } returns Result.success(TaskInfo(id = 7, status = 0.0))
        val viewModel = TaskViewModel(repository, context)
        advanceUntilIdle()

        viewModel.showLog(TaskInfo(id = 7, status = 0.0))
        runCurrent()
        viewModel.dismissLog()
        advanceTimeBy(6_000)
        runCurrent()

        coVerify(exactly = 1) { repository.getTaskLogChunk(7, null, 65_536, true) }
        coVerify(exactly = 0) { repository.getTask(7) }
        assertFalse(viewModel.uiState.value.showLogSheet)
    }
}
