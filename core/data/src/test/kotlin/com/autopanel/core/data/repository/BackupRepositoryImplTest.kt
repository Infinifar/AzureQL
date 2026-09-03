package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.BackupExportRequest
import com.autopanel.core.model.BackupModule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Provider

class BackupRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val repository = BackupRepositoryImpl(Provider { api })

    @Test
    fun `export streams gzip response and always requests base module`() = runTest {
        val request = slot<BackupExportRequest>()
        val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3, 4)
        coEvery { api.exportData(capture(request)) } returns Response.success(
            bytes.toResponseBody("application/gzip".toMediaType())
        )
        val destination = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long?>>()

        val result = repository.exportBackup(setOf(BackupModule.SCRIPTS), destination) { sent, total ->
            progress += sent to total
        }

        assertTrue(result.isSuccess)
        assertArrayEquals(bytes, destination.toByteArray())
        assertTrue(BackupModule.BASE.apiValue in request.captured.type)
        assertTrue(BackupModule.SCRIPTS.apiValue in request.captured.type)
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), progress.last())
    }

    @Test
    fun `export rejects a successful non gzip response`() = runTest {
        coEvery { api.exportData(any()) } returns Response.success(
            "not-a-backup".toResponseBody("text/plain".toMediaType())
        )

        val result = repository.exportBackup(emptySet(), ByteArrayOutputStream())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Gzip"))
    }

    @Test
    fun `import uses data multipart field and accepts string response data`() = runTest {
        val part = slot<MultipartBody.Part>()
        val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 9, 8, 7)
        val uploaded = Buffer()
        coEvery { api.importData(capture(part)) } coAnswers {
            part.captured.body.writeTo(uploaded)
            ApiResponse<JsonElement>(
                code = 200,
                data = JsonPrimitive("data/db/backup/database.sqlite")
            )
        }
        val progress = mutableListOf<Pair<Long, Long?>>()

        val result = repository.importBackup(ByteArrayInputStream(bytes), bytes.size.toLong()) { sent, total ->
            progress += sent to total
        }

        assertTrue(result.isSuccess)
        assertTrue(part.captured.headers?.get("Content-Disposition").orEmpty().contains("name=\"data\""))
        assertArrayEquals(bytes, uploaded.readByteArray())
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), progress.last())
    }

    @Test
    fun `import accepts successful response without data`() = runTest {
        coEvery { api.importData(any()) } returns ApiResponse<JsonElement>(code = 200)
        val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 1)

        val result = repository.importBackup(ByteArrayInputStream(bytes), bytes.size.toLong())

        assertTrue(result.isSuccess)
    }

    @Test
    fun `invalid import is rejected before network request`() = runTest {
        val result = repository.importBackup(
            ByteArrayInputStream("invalid".encodeToByteArray()),
            contentLength = 7
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { api.importData(any()) }
    }

    @Test
    fun `import network failure is returned for worker classification`() = runTest {
        coEvery { api.importData(any()) } throws IOException("connection reset")
        val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 1)

        val result = repository.importBackup(ByteArrayInputStream(bytes), bytes.size.toLong())

        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `import cancellation is rethrown`() = runTest {
        coEvery { api.importData(any()) } throws CancellationException("cancelled")
        val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 1)

        var thrown: Throwable? = null
        try {
            repository.importBackup(ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (error: Throwable) {
            thrown = error
        }

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `activation preserves business failure for safe presentation layer mapping`() = runTest {
        coEvery { api.activateImportedData() } returns ApiResponse(code = 500, message = "restore rejected")

        val result = repository.activateImportedBackup()

        assertTrue(result.isFailure)
        assertEquals("restore rejected", result.exceptionOrNull()?.message)
    }

    @Test
    fun `health check reports unavailable service and successful recovery`() = runTest {
        coEvery { api.healthCheck() } returnsMany listOf(
            ApiResponse(code = 503, message = "starting"),
            ApiResponse(code = 200)
        )

        val unavailable = repository.healthCheck()
        val recovered = repository.healthCheck()

        assertTrue(unavailable.isFailure)
        assertEquals("starting", unavailable.exceptionOrNull()?.message)
        assertTrue(recovered.isSuccess)
    }
}
