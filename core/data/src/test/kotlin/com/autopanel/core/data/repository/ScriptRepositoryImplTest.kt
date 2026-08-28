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

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
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
