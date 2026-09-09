package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import io.mockk.coEvery
import io.mockk.mockk
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRepositoryImplTest {
    @Test
    fun `system log reads truncation metadata and content`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.getSystemLog("2026-09-09", "2026-09-09", 1_048_576) } returns Response.success(
            "hello".toResponseBody(),
            Headers.headersOf("X-QL-Log-Total", "2000000", "X-QL-Log-Truncated", "true")
        )
        val result = LogRepositoryImpl(Provider { api }).getSystemLog("2026-09-09").getOrThrow()

        assertEquals("hello", result.content)
        assertEquals(2_000_000, result.totalBytes)
        assertTrue(result.truncated)
    }

    @Test
    fun `system log accepts an empty successful response`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.getSystemLog("2026-09-08", "2026-09-08", 1_048_576) } returns
            Response.success("".toResponseBody())

        val result = LogRepositoryImpl(Provider { api }).getSystemLog("2026-09-08").getOrThrow()

        assertEquals("", result.content)
        assertFalse(result.truncated)
    }

    @Test
    fun `system log enforces client byte limit when server ignores it`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.getSystemLog("2026-09-07", "2026-09-07", 4) } returns
            Response.success("abcdef".toResponseBody())

        val result = LogRepositoryImpl(Provider { api }).getSystemLog("2026-09-07", limit = 4).getOrThrow()

        assertEquals("abcd", result.content)
        assertTrue(result.truncated)
    }

    @Test
    fun `system log retries without limit for older QingLong versions`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.getSystemLog("2026-09-06", "2026-09-06", 1_048_576) } returns
            Response.error(400, "unsupported query".toResponseBody())
        coEvery { api.getSystemLog("2026-09-06", "2026-09-06", null) } returns
            Response.success("legacy log".toResponseBody())

        val result = LogRepositoryImpl(Provider { api }).getSystemLog("2026-09-06").getOrThrow()

        assertEquals("legacy log", result.content)
        assertFalse(result.truncated)
    }

    @Test
    fun `system log preserves caller cancellation`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.getSystemLog(any(), any(), any()) } throws CancellationException("cancelled")
        val repository = LogRepositoryImpl(Provider { api })

        var propagated = false
        try {
            repository.getSystemLog("2026-09-09")
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
    }

    @Test
    fun `log reads preserve caller cancellation`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.getLogFiles() } throws CancellationException("cancelled")
        val repository = LogRepositoryImpl(Provider { api })

        var cancellationPropagated = false
        try {
            repository.getLogFiles()
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
    }
}
