package com.autopanel.feature.script

import android.app.Activity
import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.io.IOException

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
        coEvery { repository.hasDraftChanges(any()) } returns Result.success(false)
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
    fun `copy script path writes normalized server path and triggers confirmation`() {
        val script = ScriptFile(title = "daily.py", key = ".\\jobs\\daily.py", type = "file")
        var copied: String? = null
        var confirmations = 0

        copyScriptPath(
            file = script,
            setClipboard = { copied = it },
            showConfirmation = { confirmations += 1 }
        )

        assertEquals("jobs/daily.py", copied)
        assertEquals(1, confirmations)
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
    fun `large script preparation failure exposes retryable failure state`() = runTest(dispatcher) {
        coEvery { repository.prepareDraft(any()) } returns
            Result.failure(IllegalStateException("download interrupted"))
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("large.py", "jobs")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showContent)
        assertTrue(viewModel.uiState.value.contentLoadFailed)
        assertFalse(viewModel.uiState.value.isLoadingContent)
        assertEquals(
            ScriptEvent.Message("download interrupted"),
            viewModel.events.first()
        )
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
    fun `opening a script with local changes restores pending upload state`() = runTest(dispatcher) {
        val draft = scriptDraft()
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftText(draft, 512L * 1024L) } returns Result.success("print(1)")
        coEvery { repository.hasDraftChanges(draft) } returns Result.success(true)
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("task.py", "")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPendingUpload)
        assertTrue(viewModel.uiState.value.hasLocalDraftChanges)
        assertTrue(viewModel.uiState.value.showContent)
    }

    @Test
    fun `unconfirmed upload keeps draft and marks it pending`() = runTest(dispatcher) {
        val draft = scriptDraft()
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftText(draft, 512L * 1024L) } returns Result.success("print(1)")
        coEvery { repository.replaceDraftText(draft, "print(2)", false) } returns Result.success(draft)
        coEvery { repository.uploadDraft(draft, false) } returns
            Result.success(ScriptDraftUploadResult.PENDING_UPLOAD)
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("task.py", "")
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.onContentChanged("print(2)")
        viewModel.saveContent()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPendingUpload)
        assertTrue(viewModel.uiState.value.hasLocalDraftChanges)
        assertTrue(viewModel.uiState.value.showContent)
        coVerify(exactly = 0) { repository.discardDraft(any()) }
    }

    @Test
    fun `upload failure keeps draft and marks it pending`() = runTest(dispatcher) {
        val draft = scriptDraft()
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftText(draft, 512L * 1024L) } returns Result.success("print(1)")
        coEvery { repository.replaceDraftText(draft, "print(2)", false) } returns Result.success(draft)
        coEvery { repository.uploadDraft(draft, false) } returns
            Result.failure(IOException("no network"))
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("task.py", "")
        advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.onContentChanged("print(2)")
        viewModel.saveContent()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPendingUpload)
        assertTrue(viewModel.uiState.value.hasLocalDraftChanges)
        assertTrue(viewModel.uiState.value.showContent)
        coVerify(exactly = 0) { repository.discardDraft(any()) }
        val event = viewModel.events.first() as ScriptEvent.Message
        assertTrue(event.text.contains("no network"))
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
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, 23, 65_536, false)
        } returns Result.success(
            SubscriptionLogChunk("", 23, 23, 23, truncated = false)
        )
        coEvery { subscriptionRepository.getSubscriptions() } returns Result.success(listOf(subscription))
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

    @Test
    fun `running subscription appends final log chunk before polling stops`() = runTest(dispatcher) {
        val running = SubscriptionInfo(id = 7, name = "daily", status = 0)
        val finished = running.copy(status = 1)
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, null, 65_536, true)
        } returns Result.success(
            SubscriptionLogChunk("start", 0, 5, 5, truncated = false)
        )
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, 5, 65_536, false)
        } returns Result.success(
            SubscriptionLogChunk("\nfinished", 5, 14, 14, truncated = false)
        )
        coEvery { subscriptionRepository.getSubscriptions() } returns Result.success(listOf(finished))
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.openSubscriptionLog(running)
        advanceUntilIdle()

        val log = viewModel.uiState.value.subscriptionLog
        assertEquals("start\nfinished", log?.content)
        assertEquals(14L, log?.nextOffset)
        assertFalse(log?.isStreaming ?: true)
        coVerify(exactly = 1) {
            subscriptionRepository.getSubscriptionLog(7, 5, 65_536, false)
        }
    }

    @Test
    fun `running subscription does not duplicate overlapping full log response`() = runTest(dispatcher) {
        val running = SubscriptionInfo(id = 7, name = "daily", status = 0)
        val finished = running.copy(status = 1)
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, null, 65_536, true)
        } returns Result.success(
            SubscriptionLogChunk("开始", 0, 6, 6, truncated = false)
        )
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, 6, 65_536, false)
        } returns Result.success(
            SubscriptionLogChunk("开始\n完成", 0, 13, 13, truncated = false)
        )
        coEvery { subscriptionRepository.getSubscriptions() } returns Result.success(listOf(finished))
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.openSubscriptionLog(running)
        advanceUntilIdle()

        val log = viewModel.uiState.value.subscriptionLog
        assertEquals("开始\n完成", log?.content)
        assertEquals(13L, log?.nextOffset)
        assertFalse(log?.isStreaming ?: true)
    }

    @Test
    fun `running subscription treats response without cursor metadata as full snapshot`() = runTest(dispatcher) {
        val running = SubscriptionInfo(id = 7, name = "daily", status = 0)
        val finished = running.copy(status = 1)
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, null, 65_536, true)
        } returns Result.success(
            SubscriptionLogChunk("开始", 0, 0, 0, truncated = false)
        )
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, 6, 65_536, false)
        } returns Result.success(
            SubscriptionLogChunk("开始\n完成", 0, 0, 0, truncated = false)
        )
        coEvery { subscriptionRepository.getSubscriptions() } returns Result.success(listOf(finished))
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.openSubscriptionLog(running)
        advanceUntilIdle()

        val log = viewModel.uiState.value.subscriptionLog
        assertEquals("开始\n完成", log?.content)
        assertEquals(13L, log?.nextOffset)
        assertEquals(13L, log?.total)
        assertFalse(log?.isStreaming ?: true)
    }

    @Test
    fun `stale idle subscription status still enters live polling`() = runTest(dispatcher) {
        val idle = SubscriptionInfo(id = 7, name = "daily", status = 1)
        val running = idle.copy(status = 3)
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, null, 65_536, true)
        } returns Result.success(
            SubscriptionLogChunk("start", 0, 5, 5, truncated = false)
        )
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, 5, 65_536, false)
        } returns Result.success(
            SubscriptionLogChunk("\nrunning", 5, 13, 13, truncated = false)
        )
        coEvery {
            subscriptionRepository.getSubscriptionLog(7, 13, 65_536, false)
        } returns Result.success(
            SubscriptionLogChunk("\ndone", 13, 18, 18, truncated = false)
        )
        coEvery { subscriptionRepository.getSubscriptions() } returnsMany listOf(
            Result.success(listOf(running)),
            Result.success(listOf(idle))
        )
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.openSubscriptionLog(idle)
        advanceUntilIdle()

        val log = viewModel.uiState.value.subscriptionLog
        assertEquals("start\nrunning\ndone", log?.content)
        assertFalse(log?.isStreaming ?: true)
    }

    @Test
    fun `external editor launch snapshots draft and records pending path`() = runTest(dispatcher) {
        val draft = scriptDraft(filename = "large.py", path = "jobs", sizeBytes = 600L * 1024L, pageCount = 19)
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftPage(draft, 0) } returns
            Result.success(ScriptDraftPage(0, 19, "first"))
        coEvery { repository.snapshotDraft(draft) } returns Result.success(123L)
        val savedState = SavedStateHandle()
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context, savedState)
        advanceUntilIdle()

        viewModel.loadContent("large.py", "jobs")
        advanceUntilIdle()

        var launched: ScriptDraft? = null
        viewModel.launchExternalEditor { launched = it }
        advanceUntilIdle()

        assertEquals(draft, launched)
        assertEquals(123L, viewModel.uiState.value.externalEditorSnapshotBytes)
        assertEquals("large.py", savedState.get<String>("externalEditorFilename"))
        assertEquals("jobs", savedState.get<String>("externalEditorPath"))
        coVerify(exactly = 1) { repository.snapshotDraft(draft) }
    }

    @Test
    fun `external editor ok return detects local changes`() = runTest(dispatcher) {
        val draft = scriptDraft(filename = "large.py", path = "jobs", sizeBytes = 600L * 1024L, pageCount = 19)
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftPage(draft, 0) } returns
            Result.success(ScriptDraftPage(0, 19, "first"))
        coEvery { repository.snapshotDraft(draft) } returns Result.success(123L)
        coEvery { repository.refreshDraft(draft) } returns Result.success(draft)
        coEvery { repository.hasDraftChanges(draft) } returnsMany listOf(
            Result.success(false),
            Result.success(true)
        )
        val savedState = SavedStateHandle()
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context, savedState)
        advanceUntilIdle()

        viewModel.loadContent("large.py", "jobs")
        advanceUntilIdle()
        viewModel.launchExternalEditor { }
        advanceUntilIdle()
        viewModel.onExternalEditorReturned(Activity.RESULT_OK)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasLocalDraftChanges)
        assertNull(viewModel.uiState.value.externalEditorSnapshotBytes)
        assertNull(savedState.get<String>("externalEditorFilename"))
        coVerify(exactly = 0) { repository.restoreDraftSnapshot(any()) }
    }

    @Test
    fun `cancel return with truncated file restores snapshot`() = runTest(dispatcher) {
        val draft = scriptDraft(filename = "large.py", path = "jobs", sizeBytes = 600L * 1024L, pageCount = 19)
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftPage(draft, 0) } returns
            Result.success(ScriptDraftPage(0, 19, "first"))
        coEvery { repository.snapshotDraft(draft) } returns Result.success(123L)
        val truncated = draft.copy(sizeBytes = 0L, characterCount = 0L, pageCount = 1)
        val restored = draft.copy(sizeBytes = 123L, characterCount = 123L)
        coEvery { repository.refreshDraft(draft) } returns Result.success(truncated)
        coEvery { repository.restoreDraftSnapshot(truncated) } returns Result.success(Unit)
        coEvery { repository.refreshDraft(truncated) } returns Result.success(restored)
        coEvery { repository.hasDraftChanges(restored) } returns Result.success(false)
        coEvery { repository.readDraftPage(restored, 0) } returns
            Result.success(ScriptDraftPage(0, 19, "first"))
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context)
        advanceUntilIdle()

        viewModel.loadContent("large.py", "jobs")
        advanceUntilIdle()
        viewModel.launchExternalEditor { }
        advanceUntilIdle()
        viewModel.onExternalEditorReturned(Activity.RESULT_CANCELED)
        advanceTimeBy(350)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.restoreDraftSnapshot(truncated) }
        assertFalse(viewModel.uiState.value.hasLocalDraftChanges)
    }

    @Test
    fun `script tree load reopens externally edited script after recreation`() = runTest(dispatcher) {
        val draft = scriptDraft(filename = "large.py", path = "jobs", sizeBytes = 600L * 1024L, pageCount = 19)
        coEvery { repository.prepareDraft(any()) } returns Result.success(draft)
        coEvery { repository.readDraftPage(draft, 0) } returns
            Result.success(ScriptDraftPage(0, 19, "first"))
        val savedState = SavedStateHandle(
            mapOf("externalEditorFilename" to "large.py", "externalEditorPath" to "jobs")
        )
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context, savedState)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showContent)
        assertEquals("large.py", viewModel.uiState.value.editingFilename)
        assertEquals("jobs", viewModel.uiState.value.editingPath)
        assertNull(savedState.get<String>("externalEditorFilename"))
        coVerify(exactly = 1) {
            repository.prepareDraft(match { it.title == "large.py" && it.parent == "jobs" })
        }
    }

    @Test
    fun `external return without loaded draft keeps recreation keys`() = runTest(dispatcher) {
        coEvery { repository.getScripts() } coAnswers { awaitCancellation() }
        val savedState = SavedStateHandle(
            mapOf("externalEditorFilename" to "large.py", "externalEditorPath" to "jobs")
        )
        val viewModel = ScriptViewModel(repository, subscriptionRepository, context, savedState)
        advanceUntilIdle()

        viewModel.onExternalEditorReturned(Activity.RESULT_OK)
        advanceUntilIdle()

        assertEquals("large.py", savedState.get<String>("externalEditorFilename"))
        assertEquals("jobs", savedState.get<String>("externalEditorPath"))
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
