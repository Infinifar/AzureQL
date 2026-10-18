package com.autopanel.core.data.repository

import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.script.ScriptDraftStore
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptDraftUploadResult
import com.autopanel.core.domain.ScriptHistoryConflictException
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.ScriptDirectoryRequest
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.ScriptHistoryDetail
import com.autopanel.core.model.ScriptHistoryList
import com.autopanel.core.model.ScriptHistoryVersion
import com.autopanel.core.model.ScriptMutationResult
import com.autopanel.core.model.ScriptUpdateRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import javax.inject.Provider

class ScriptRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val apiProvider = mockk<Provider<AutoPanelApiService>>()
    private val responseCache = mockk<ResponseCache>()
    private val draftStore = mockk<ScriptDraftStore>()
    private val draft = scriptDraft()
    private lateinit var repository: ScriptRepositoryImpl

    @Before
    fun setUp() {
        every { apiProvider.get() } returns api
        coEvery { draftStore.refresh(draft) } returns draft
        coEvery { draftStore.readText(draft, 10L * 1024L * 1024L) } returns "updated"
        coEvery { responseCache.invalidate(any()) } returns Unit
        coEvery { api.getScripts() } returns ApiResponse(code = 200, data = listOf(matchingServerScript()))
        repository = ScriptRepositoryImpl(
            apiProvider,
            responseCache,
            draftStore,
            Json { ignoreUnknownKeys = true }
        )
    }

    @Test
    fun `create directory sends QingLong 2_23 payload and invalidates cache`() = runTest {
        coEvery { api.createScriptDirectory(any()) } returns ApiResponse(code = 200)

        val result = repository.createScriptDirectory("daily", "jobs")

        assertTrue(result.isSuccess)
        coVerify {
            api.createScriptDirectory(
                ScriptDirectoryRequest(filename = "daily", path = "jobs", directory = "daily")
            )
        }
        coVerify { responseCache.invalidate(ResponseCache.SCRIPTS) }
    }

    @Test
    fun `draft upload saves JSON so server history is recorded`() = runTest {
        val request = slot<ScriptUpdateRequest>()
        coEvery { api.updateScript(capture(request)) } returns successfulMutation()

        val result = repository.uploadDraft(draft).getOrThrow()

        assertEquals(ScriptDraftUploadResult.SAVED, result)
        assertEquals("task.py", request.captured.filename)
        assertEquals("jobs", request.captured.path)
        assertEquals("updated", request.captured.content)
        assertEquals(null, request.captured.skipHistory)
        coVerify(exactly = 1) { api.updateScript(any()) }
    }

    @Test
    fun `draft upload preserves UTF 8 BOM in JSON content`() = runTest {
        val bomDraft = draft.copy(hasUtf8Bom = true)
        val request = slot<ScriptUpdateRequest>()
        coEvery { draftStore.refresh(bomDraft) } returns bomDraft
        coEvery { draftStore.readText(bomDraft, 10L * 1024L * 1024L) } returns "updated"
        coEvery { api.updateScript(capture(request)) } returns successfulMutation()

        assertEquals(ScriptDraftUploadResult.SAVED, repository.uploadDraft(bomDraft).getOrThrow())
        assertEquals("\uFEFFupdated", request.captured.content)
    }

    @Test
    fun `oversize history response retries atomically without history`() = runTest {
        val requests = mutableListOf<ScriptUpdateRequest>()
        coEvery { api.updateScript(capture(requests)) } returnsMany listOf(
            Response.error(
                413,
                """{"code":413,"message":"too large","historyUnavailable":true,"currentHash":"server-hash"}"""
                    .toResponseBody(JSON_MEDIA_TYPE)
            ),
            successfulMutation(historyRecorded = false)
        )

        val result = repository.uploadDraft(draft).getOrThrow()

        assertEquals(ScriptDraftUploadResult.SAVED_WITHOUT_HISTORY, result)
        assertEquals(2, requests.size)
        assertEquals(true, requests[1].skipHistory)
        assertEquals("server-hash", requests[1].expectedHash)
    }

    @Test
    fun `oversize retry returns conflict when server hash changed`() = runTest {
        coEvery { api.updateScript(any()) } returnsMany listOf(
            Response.error(
                413,
                """{"code":413,"historyUnavailable":true,"currentHash":"server-hash"}"""
                    .toResponseBody(JSON_MEDIA_TYPE)
            ),
            Response.error(409, """{"code":409,"message":"conflict"}""".toResponseBody(JSON_MEDIA_TYPE))
        )

        assertEquals(ScriptDraftUploadResult.CONFLICT, repository.uploadDraft(draft).getOrThrow())
    }

    @Test
    fun `history cleanup pending is surfaced to caller`() = runTest {
        coEvery { api.updateScript(any()) } returns successfulMutation(cleanupPending = true)

        assertEquals(
            ScriptDraftUploadResult.SAVED_CLEANUP_PENDING,
            repository.uploadDraft(draft).getOrThrow()
        )
    }

    @Test
    fun `draft upload returns conflict before save when server changed`() = runTest {
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(matchingServerScript().copy(size = 99, mtime = 2.0))
        )

        assertEquals(ScriptDraftUploadResult.CONFLICT, repository.uploadDraft(draft).getOrThrow())
        coVerify(exactly = 0) { api.updateScript(any()) }
    }

    @Test
    fun `network failure keeps upload as failure`() = runTest {
        coEvery { api.updateScript(any()) } throws IOException("no network")

        val result = repository.uploadDraft(draft)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("保存脚本"))
    }

    @Test
    fun `history list and detail use 2_23 endpoints`() = runTest {
        val version = historyVersion()
        val list = ScriptHistoryList(listOf(version), currentHash = "current", limit = 20)
        val detail = ScriptHistoryDetail(version.copy(content = "old"), "current text", "current")
        coEvery { api.getScriptHistory("task.py", "jobs") } returns
            Response.success(ApiResponse(code = 200, data = list))
        coEvery { api.getScriptHistoryDetail("task.py", "jobs", "v1") } returns
            Response.success(ApiResponse(code = 200, data = detail))

        assertEquals(list, repository.getScriptHistory("task.py", "jobs").getOrThrow())
        assertEquals(detail, repository.getScriptHistoryDetail("task.py", "jobs", "v1").getOrThrow())
    }

    @Test
    fun `history restore uses expected hash and maps 409 to conflict`() = runTest {
        coEvery { api.restoreScriptHistory(any()) } returns Response.error(
            409,
            """{"code":409,"message":"script changed"}""".toResponseBody(JSON_MEDIA_TYPE)
        )

        val result = repository.restoreScriptHistory("task.py", "jobs", "v1", "current")

        assertTrue(result.exceptionOrNull() is ScriptHistoryConflictException)
        coVerify {
            api.restoreScriptHistory(match {
                it.id == "v1" && it.expectedHash == "current" && it.filename == "task.py"
            })
        }
    }

    @Test
    fun `prepareDraft reuses persisted cache when server version is unchanged`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns false

        assertEquals(draft, repository.prepareDraft(scriptFile()).getOrThrow())
        coVerify(exactly = 0) { api.downloadScript(any()) }
    }

    @Test
    fun `prepareDraft reuses dirty cache even when server changed`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns true
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(matchingServerScript().copy(size = 99, mtime = 2.0))
        )

        assertEquals(draft, repository.prepareDraft(scriptFile()).getOrThrow())
        coVerify(exactly = 0) { api.downloadScript(any()) }
    }

    @Test
    fun `prepareDraft redownloads when clean cached version changed`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns false
        coEvery { draftStore.create(any(), any(), any()) } returns draft
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(matchingServerScript().copy(size = 99, mtime = 2.0))
        )
        coEvery { api.downloadScript(any()) } returns Response.success(
            "updated".toResponseBody("application/octet-stream".toMediaType()),
            Headers.headersOf("Content-Disposition", "attachment; filename=\"task.py\"")
        )

        assertEquals(draft, repository.prepareDraft(scriptFile()).getOrThrow())
        coVerify(exactly = 1) { api.downloadScript(any()) }
        coVerify(exactly = 1) { draftStore.create(any(), any(), any()) }
    }

    @Test
    fun `snapshot and restore delegate to draft store`() = runTest {
        coEvery { draftStore.snapshot(draft) } returns 123L
        coEvery { draftStore.restoreSnapshot(draft) } returns Unit

        assertEquals(123L, repository.snapshotDraft(draft).getOrThrow())
        assertTrue(repository.restoreDraftSnapshot(draft).isSuccess)
    }

    private fun successfulMutation(
        historyRecorded: Boolean = true,
        cleanupPending: Boolean = false
    ): Response<ApiResponse<ScriptMutationResult>> = Response.success(
        ApiResponse(
            code = 200,
            data = ScriptMutationResult(
                content = "updated",
                changed = true,
                historyRecorded = historyRecorded,
                cleanupPending = cleanupPending
            )
        )
    )

    private fun scriptFile() = ScriptFile(
        title = draft.filename,
        key = draft.sourceKey,
        type = "file",
        parent = draft.path
    )

    private fun matchingServerScript() = ScriptFile(
        title = draft.filename,
        key = draft.sourceKey,
        type = "file",
        parent = draft.path,
        size = draft.sourceSizeBytes,
        mtime = draft.sourceModifiedTime
    )

    private fun historyVersion() = ScriptHistoryVersion(
        id = "v1",
        createdAt = "2026-10-18T08:00:00.000Z",
        source = "save",
        hash = "old"
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

private fun scriptDraft() = ScriptDraft(
    cacheToken = "0123456789abcdef01234567-task.py",
    filename = "task.py",
    path = "jobs",
    sourceKey = "jobs/task.py",
    sizeBytes = 7,
    characterCount = 7,
    pageCount = 1,
    hasUtf8Bom = false,
    isUtf8Valid = true,
    editorUri = "content://com.autopanel.test.script-files/task.py",
    sourceSizeBytes = 7,
    sourceModifiedTime = 1.0,
    originalSha256 = "original"
)
