package com.autopanel.feature.script

import android.content.Context
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptDraftPage
import com.autopanel.core.domain.ScriptDraftUploadResult
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
import org.junit.Assert.assertNull
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
        coEvery { repository.discardDraft(any()) } returns Unit
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
    fun `find script path resolves nested tree and preserves actual parent`() {
        val scripts = listOf(
            ScriptFile(
                title = "jobs",
                type = "directory",
                children = listOf(ScriptFile(title = "daily.py", type = "file"))
            )
        )

        val script = findScriptByPath(scripts, "jobs/daily.py")

        assertEquals("daily.py", script?.title)
        assertEquals("jobs", script?.parent)
        assertNull(findScriptByPath(scripts, "jobs/missing.py"))
    }

    @Test
    fun `open script request waits for script tree and opens actual file`() = runTest(dispatcher) {
        val tree = listOf(
            ScriptFile(
                title = "jobs",
                type = "directory",
                children = listOf(ScriptFile(title = "daily.py", type = "file"))
            )
        )
        val draft = scriptDraft(filename = "daily.py", path = "jobs")
        coEvery { repository.getScripts() } returns Result.success(tree)
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftText(draft, 512L * 1024L) } returns Result.success("print(1)")
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)

        viewModel.openScriptPath("jobs/daily.py", requestId = 1L)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showContent)
        assertEquals("daily.py", viewModel.uiState.value.editingFilename)
        assertEquals("jobs", viewModel.uiState.value.editingPath)
        coVerify(exactly = 1) {
            repository.prepareDraft(match { it.title == "daily.py" && it.parent == "jobs" })
        }
    }

    @Test
    fun `utf8 bom is hidden in editor and restored when saving`() = runTest(dispatcher) {
        val draft = scriptDraft(filename = "task.py", path = "jobs", hasUtf8Bom = true)
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftText(draft, 512L * 1024L) } returns Result.success("你好")
        coEvery { repository.replaceDraftText(draft, "你好", true) } returns Result.success(draft)
        coEvery { repository.uploadDraft(draft, false) } returns
            Result.success(ScriptDraftUploadResult.SAVED)
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
            repository.replaceDraftText(draft, "你好", true)
        }
        coVerify(exactly = 1) { repository.uploadDraft(draft, false) }
    }

    @Test
    fun `invalid utf8 draft is read only`() = runTest(dispatcher) {
        val draft = scriptDraft(filename = "legacy.py", isUtf8Valid = false)
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
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
    fun `large script uses bounded paged preview`() = runTest(dispatcher) {
        val draft = scriptDraft(sizeBytes = 600L * 1024L, pageCount = 19)
        val page = ScriptDraftPage(index = 0, totalPages = 19, content = "first segment")
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftPage(draft, 0) } returns Result.success(page)
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("large.py", "jobs")
        advanceUntilIdle()

        assertEquals(ScriptContentMode.PAGED, viewModel.uiState.value.contentMode)
        assertEquals("first segment", viewModel.uiState.value.editContent)
        assertEquals(page, viewModel.uiState.value.previewPage)
        assertFalse(viewModel.uiState.value.isContentReadOnly)
        coVerify(exactly = 0) { repository.getScriptContent(any(), any()) }
    }

    @Test
    fun `server change asks before overwriting local draft`() = runTest(dispatcher) {
        val draft = scriptDraft()
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftText(draft, 512L * 1024L) } returns Result.success("print(1)")
        coEvery { repository.replaceDraftText(draft, "print(2)", false) } returns Result.success(draft)
        coEvery { repository.uploadDraft(draft, false) } returns
            Result.success(ScriptDraftUploadResult.CONFLICT)
        coEvery { repository.uploadDraft(draft, true) } returns
            Result.success(ScriptDraftUploadResult.SAVED)
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("task.py", "")
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.onContentChanged("print(2)")
        viewModel.saveContent()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showOverwriteConfirm)
        assertTrue(viewModel.uiState.value.showContent)

        viewModel.confirmOverwriteDraft()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.uploadDraft(draft, true) }
        assertFalse(viewModel.uiState.value.showContent)
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

private fun scriptDraft(
    filename: String = "task.py",
    path: String = "",
    sizeBytes: Long = 32L,
    pageCount: Int = 1,
    hasUtf8Bom: Boolean = false,
    isUtf8Valid: Boolean = true
) = ScriptDraft(
    cacheToken = "0123456789abcdef01234567-$filename",
    filename = filename,
    path = path,
    sourceKey = listOf(path, filename).filter(String::isNotBlank).joinToString("/"),
    sizeBytes = sizeBytes,
    characterCount = sizeBytes,
    pageCount = pageCount,
    hasUtf8Bom = hasUtf8Bom,
    isUtf8Valid = isUtf8Valid,
    editorUri = "content://com.autopanel.test.script-files/$filename",
    sourceSizeBytes = sizeBytes,
    sourceModifiedTime = 1.0,
    originalSha256 = "original"
)
