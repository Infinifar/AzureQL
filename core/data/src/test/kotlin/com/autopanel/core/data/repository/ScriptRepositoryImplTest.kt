package com.autopanel.core.data.repository

import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.script.ScriptDraftStore
import com.autopanel.core.data.script.ScriptDraftUpload
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptDraftUploadResult
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.ScriptFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Provider

class ScriptRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val apiProvider = mockk<Provider<AutoPanelApiService>>()
    private val responseCache = mockk<ResponseCache>()
    private val draftStore = mockk<ScriptDraftStore>()
    private val draft = scriptDraft()
    private val uploadPart = MultipartBody.Part.createFormData(
        "file",
        "azureql-test.upload",
        "updated".toRequestBody("application/octet-stream".toMediaType())
    )
    private lateinit var repository: ScriptRepositoryImpl

    @Before
    fun setUp() {
        every { apiProvider.get() } returns api
        coEvery { draftStore.refresh(draft) } returns draft
        coEvery { draftStore.createUpload(draft) } returns
            ScriptDraftUpload("azureql-test.upload", uploadPart)
        coEvery { responseCache.invalidate(any()) } returns Unit
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(
                ScriptFile(
                    title = draft.filename,
                    key = draft.sourceKey,
                    type = "file",
                    parent = draft.path,
                    size = draft.sourceSizeBytes,
                    mtime = draft.sourceModifiedTime
                )
            )
        )
        repository = ScriptRepositoryImpl(apiProvider, responseCache, draftStore)
    }

    @Test
    fun `draft upload streams multipart file with target filename and path`() = runTest {
        val filename = slot<RequestBody>()
        val path = slot<RequestBody>()
        coEvery {
            api.uploadScriptFile(uploadPart, capture(filename), capture(path))
        } returns Response.success("{\"code\":200}".toResponseBody(JSON_MEDIA_TYPE))

        val result = repository.uploadDraft(draft).getOrThrow()

        assertEquals(ScriptDraftUploadResult.SAVED, result)
        assertEquals("task.py", filename.captured.readUtf8())
        assertEquals("jobs", path.captured.readUtf8())
        coVerify(exactly = 1) { api.uploadScriptFile(uploadPart, any(), any()) }
        coVerify(exactly = 0) { api.updateScript(any()) }
    }

    @Test
    fun `multipart server error keeps response detail`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } returns Response.error(
            500,
            "{\"code\":500,\"message\":\"EBUSY while replacing script\"}"
                .toResponseBody(JSON_MEDIA_TYPE)
        )
        coEvery { api.deleteScript(any()) } returns ApiResponse(code = 200)

        val error = repository.uploadDraft(draft).exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("EBUSY while replacing script"))
        coVerify(exactly = 1) {
            api.deleteScript(match { it.filename == "azureql-test.upload" && it.path.isEmpty() })
        }
    }

    @Test
    fun `prepareDraft reuses persisted cache when server version is unchanged`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns false

        val result = repository.prepareDraft(scriptFile())

        assertEquals(draft, result.getOrThrow())
        coVerify(exactly = 0) { api.downloadScript(any()) }
        coVerify(exactly = 0) { draftStore.create(any(), any(), any()) }
    }

    @Test
    fun `prepareDraft restores dirty persisted draft without re-downloading`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns true
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(
                ScriptFile(
                    title = draft.filename,
                    key = draft.sourceKey,
                    type = "file",
                    parent = draft.path,
                    size = 99,
                    mtime = 2.0
                )
            )
        )

        val result = repository.prepareDraft(scriptFile())

        assertEquals(draft, result.getOrThrow())
        coVerify(exactly = 0) { api.downloadScript(any()) }
        coVerify(exactly = 0) { draftStore.create(any(), any(), any()) }
    }

    @Test
    fun `prepareDraft re-downloads when persisted version changed`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns false
        coEvery { draftStore.create(any(), any(), any()) } returns draft
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(
                ScriptFile(
                    title = draft.filename,
                    key = draft.sourceKey,
                    type = "file",
                    parent = draft.path,
                    size = 99,
                    mtime = 2.0
                )
            )
        )
        coEvery { api.downloadScript(any()) } returns Response.success(
            "updated".toResponseBody("application/octet-stream".toMediaType()),
            Headers.headersOf("Content-Disposition", "attachment; filename=\"task.py\"")
        )

        val result = repository.prepareDraft(scriptFile())

        assertEquals(draft, result.getOrThrow())
        coVerify(exactly = 1) { api.downloadScript(any()) }
        coVerify(exactly = 1) { draftStore.create(any(), any(), any()) }
        coVerify(exactly = 0) { draftStore.discard(any()) }
    }

    @Test
    fun `prepareDraft reuses clean cache when server list is unavailable`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns false
        coEvery { api.getScripts() } throws IOException("no network")

        val result = repository.prepareDraft(scriptFile())

        assertEquals(draft, result.getOrThrow())
        coVerify(exactly = 0) { api.downloadScript(any()) }
        coVerify(exactly = 0) { draftStore.discard(any()) }
        coVerify(exactly = 0) { draftStore.create(any(), any(), any()) }
    }

    @Test
    fun `prepareDraft refreshes cache when server list confirms the file is missing`() = runTest {
        coEvery { draftStore.findPersisted(any()) } returns draft
        coEvery { draftStore.hasChanges(draft) } returns false
        coEvery { api.getScripts() } returns ApiResponse(code = 200, data = emptyList())
        coEvery { api.downloadScript(any()) } returns Response.success(
            "updated".toResponseBody("application/octet-stream".toMediaType()),
            Headers.headersOf("Content-Disposition", "attachment; filename=\"task.py\"")
        )
        coEvery { draftStore.create(any(), any(), any()) } returns draft

        val result = repository.prepareDraft(scriptFile())

        assertEquals(draft, result.getOrThrow())
        coVerify(exactly = 1) { api.downloadScript(any()) }
        coVerify(exactly = 1) { draftStore.create(any(), any(), any()) }
    }

    @Test
    fun `uploadDraft stays pending when server version is not confirmed after upload`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } returns Response.success("{\"code\":200}".toResponseBody(JSON_MEDIA_TYPE))
        coEvery { api.getScripts() } returnsMany listOf(
            ApiResponse(
                code = 200,
                data = listOf(
                    ScriptFile(
                        title = draft.filename,
                        key = draft.sourceKey,
                        type = "file",
                        parent = draft.path,
                        size = draft.sourceSizeBytes,
                        mtime = draft.sourceModifiedTime
                    )
                )
            ),
            ApiResponse(
                code = 200,
                data = listOf(
                    ScriptFile(
                        title = draft.filename,
                        key = draft.sourceKey,
                        type = "file",
                        parent = draft.path,
                        size = 999,
                        mtime = 9.0
                    )
                )
            )
        )

        val result = repository.uploadDraft(draft)

        assertEquals(ScriptDraftUploadResult.PENDING_UPLOAD, result.getOrThrow())
        coVerify(exactly = 1) { api.uploadScriptFile(uploadPart, any(), any()) }
    }

    @Test
    fun `uploadDraft is saved when server confirms uploaded size`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } returns Response.success("{\"code\":200}".toResponseBody(JSON_MEDIA_TYPE))

        val result = repository.uploadDraft(draft)

        assertEquals(ScriptDraftUploadResult.SAVED, result.getOrThrow())
        coVerify(exactly = 1) { api.uploadScriptFile(uploadPart, any(), any()) }
    }

    @Test
    fun `uploadDraft returns conflict when server changed since download`() = runTest {
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(
                ScriptFile(
                    title = draft.filename,
                    key = draft.sourceKey,
                    type = "file",
                    parent = draft.path,
                    size = 99,
                    mtime = 2.0
                )
            )
        )

        val result = repository.uploadDraft(draft)

        assertEquals(ScriptDraftUploadResult.CONFLICT, result.getOrThrow())
        coVerify(exactly = 0) { api.uploadScriptFile(any(), any(), any()) }
    }

    @Test
    fun `uploadDraft offline during upload returns failure and cleans up`() = runTest {
        coEvery { api.uploadScriptFile(uploadPart, any(), any()) } throws IOException("no network")
        coEvery { api.deleteScript(any()) } returns ApiResponse(code = 200)

        val result = repository.uploadDraft(draft)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("保存脚本"))
        coVerify(exactly = 1) {
            api.deleteScript(match { it.filename == "azureql-test.upload" && it.path.isEmpty() })
        }
    }

    @Test
    fun `uploadDraft timeout during upload returns failure`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } throws SocketTimeoutException("connect timed out")
        coEvery { api.deleteScript(any()) } returns ApiResponse(code = 200)

        val result = repository.uploadDraft(draft)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("保存脚本"))
    }

    @Test
    fun `uploadDraft http 4xx during upload returns failure and cleans up`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } returns Response.error(
            400,
            "{\"code\":400,\"message\":\"bad request\"}".toResponseBody(JSON_MEDIA_TYPE)
        )
        coEvery { api.deleteScript(any()) } returns ApiResponse(code = 200)

        val result = repository.uploadDraft(draft)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("bad request"))
        coVerify(exactly = 1) { api.deleteScript(any()) }
    }

    @Test
    fun `uploadDraft offline during verification stays pending`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } returns Response.success("{\"code\":200}".toResponseBody(JSON_MEDIA_TYPE))
        coEvery { api.getScripts() } returns ApiResponse(
            code = 200,
            data = listOf(matchingServerScript())
        ) andThenThrows IOException("no network")

        val result = repository.uploadDraft(draft)

        assertEquals(ScriptDraftUploadResult.PENDING_UPLOAD, result.getOrThrow())
        coVerify(exactly = 1) { api.uploadScriptFile(uploadPart, any(), any()) }
    }

    @Test
    fun `uploadDraft server 5xx during verification stays pending`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } returns Response.success("{\"code\":200}".toResponseBody(JSON_MEDIA_TYPE))
        coEvery { api.getScripts() } returnsMany listOf(
            ApiResponse(code = 200, data = listOf(matchingServerScript())),
            ApiResponse(code = 500, data = null)
        )

        val result = repository.uploadDraft(draft)

        assertEquals(ScriptDraftUploadResult.PENDING_UPLOAD, result.getOrThrow())
    }

    @Test
    fun `uploadDraft server 4xx during verification stays pending`() = runTest {
        coEvery {
            api.uploadScriptFile(uploadPart, any(), any())
        } returns Response.success("{\"code\":200}".toResponseBody(JSON_MEDIA_TYPE))
        coEvery { api.getScripts() } returnsMany listOf(
            ApiResponse(code = 200, data = listOf(matchingServerScript())),
            ApiResponse(code = 404, data = null)
        )

        val result = repository.uploadDraft(draft)

        assertEquals(ScriptDraftUploadResult.PENDING_UPLOAD, result.getOrThrow())
    }

    @Test
    fun `snapshot and restore delegate to draft store`() = runTest {
        coEvery { draftStore.snapshot(draft) } returns 123L
        coEvery { draftStore.restoreSnapshot(draft) } returns Unit

        assertEquals(123L, repository.snapshotDraft(draft).getOrThrow())
        assertTrue(repository.restoreDraftSnapshot(draft).isSuccess)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

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
}

private fun RequestBody.readUtf8(): String = Buffer().also(::writeTo).readUtf8()

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
