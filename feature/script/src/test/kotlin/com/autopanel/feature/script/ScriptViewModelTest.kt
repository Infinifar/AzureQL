package com.autopanel.feature.script

import android.content.Context
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.domain.SubscriptionRepository
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.SubscriptionInfo
import com.autopanel.core.model.SubscriptionLogChunk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getCachedScripts() } returns null
        coEvery { repository.getScripts() } returns Result.success(emptyList())
        coEvery { subscriptionRepository.getSubscriptions() } returns Result.success(emptyList())
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
    fun `script action key distinguishes a file from a directory at the same path`() {
        val file = ScriptFile(key = "jobs/daily", type = "file")
        val directory = ScriptFile(key = "jobs/daily", type = "directory")

        assertEquals("file:jobs/daily", file.scriptActionKey())
        assertEquals("directory:jobs/daily", directory.scriptActionKey())
    }

    @Test
    fun `utf8 bom is hidden in editor and restored when saving`() = runTest(dispatcher) {
        coEvery { repository.getScriptContent("task.py", "jobs") } returns
            Result.success("\uFEFF你好")
        coEvery { repository.updateScript("task.py", "jobs", "\uFEFF你好") } returns
            Result.success(Unit)
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
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
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("legacy.py", "")
        advanceUntilIdle()
        viewModel.enterEditMode()

        assertTrue(viewModel.uiState.value.isContentReadOnly)
        assertFalse(viewModel.uiState.value.isEditing)
        assertTrue((viewModel.events.first() as ScriptEvent.Message).text.contains("UTF-8"))
    }

    @Test
    fun `selecting subscriptions loads official subscription list`() = runTest(dispatcher) {
        val subscription = SubscriptionInfo(id = 7, name = "daily", alias = "daily")
        coEvery { subscriptionRepository.getSubscriptions() } returns Result.success(listOf(subscription))
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.selectSection(ScriptSection.SUBSCRIPTIONS)
        advanceUntilIdle()

        assertEquals(ScriptSection.SUBSCRIPTIONS, viewModel.uiState.value.section)
        assertEquals(listOf(subscription), viewModel.uiState.value.subscriptions)
        coVerify(exactly = 1) { subscriptionRepository.getSubscriptions() }
    }

    @Test
    fun `subscription alias is generated from repository url and branch`() {
        assertEquals(
            "owner_repo_develop",
            defaultSubscriptionAlias("https://github.com/owner/repo.git", "develop", "fallback")
        )
    }

    @Test
    fun `opening idle subscription shows latest log chunk`() = runTest(dispatcher) {
        val subscription = SubscriptionInfo(id = 7, name = "daily", status = 1)
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, null, 65_536, true)
        } returns Result.success(
            SubscriptionLogChunk(
                content = "pull complete",
                offset = 10,
                nextOffset = 23,
                total = 23,
                truncated = true
            )
        )
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.openSubscriptionLog(subscription)
        advanceUntilIdle()

        val log = viewModel.uiState.value.subscriptionLog
        assertEquals("pull complete", log?.content)
        assertEquals(10L, log?.offset)
        assertEquals(23L, log?.nextOffset)
        assertFalse(log?.isLoading ?: true)
    }
}
