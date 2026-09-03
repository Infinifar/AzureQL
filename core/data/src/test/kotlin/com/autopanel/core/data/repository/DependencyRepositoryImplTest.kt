package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.DependencyCreateRequest
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.inject.Provider

class DependencyRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val repository = DependencyRepositoryImpl(Provider { api })

    @Test
    fun `add maps linux type to official numeric code and returns queued record`() = runTest {
        val body = slot<List<DependencyCreateRequest>>()
        val queued = DependencyInfo(id = 9, name = "tesseract-ocr", type = 2, status = DependencyStatus.QUEUED)
        coEvery { api.addDependencies(capture(body)) } returns ApiResponse(code = 200, data = listOf(queued))

        val result = repository.addDependency("tesseract-ocr", "linux")

        assertEquals(listOf(queued), result.getOrThrow())
        assertEquals(listOf(DependencyCreateRequest("tesseract-ocr", 2)), body.captured)
    }

    @Test
    fun `reinstall accepts array response and forwards exact ids`() = runTest {
        val queued = DependencyInfo(id = 4, name = "axios", status = DependencyStatus.QUEUED)
        coEvery { api.reinstallDependencies(listOf(4)) } returns ApiResponse(code = 200, data = listOf(queued))

        assertEquals(listOf(queued), repository.reinstallDependencies(listOf(4)).getOrThrow())
        coVerify(exactly = 1) { api.reinstallDependencies(listOf(4)) }
    }

    @Test
    fun `force delete accepts array or empty data`() = runTest {
        val deleting = DependencyInfo(id = 5, name = "requests", status = DependencyStatus.UNINSTALLING)
        coEvery { api.deleteDependencies(listOf(5)) } returns ApiResponse(code = 200, data = listOf(deleting))
        coEvery { api.deleteDependencies(listOf(6)) } returns ApiResponse(code = 200)

        assertEquals(listOf(deleting), repository.deleteDependencies(listOf(5)).getOrThrow())
        assertTrue(repository.deleteDependencies(listOf(6)).getOrThrow().isEmpty())
    }

    @Test
    fun `mutation business error is preserved`() = runTest {
        coEvery { api.reinstallDependencies(any()) } returns ApiResponse(code = 500, message = "queue rejected")

        assertEquals("queue rejected", repository.reinstallDependencies(listOf(8)).exceptionOrNull()?.message)
    }

    @Test
    fun `cancellation is never converted to dependency failure`() = runTest {
        val cancellation = CancellationException("cancel test")
        coEvery { api.getDependencies(any(), any()) } throws cancellation

        try {
            repository.getDependencies("", "")
            fail("Expected CancellationException")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
