package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.EnvCreateRequest
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.model.EnvUpdateRequest
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

class EnvRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val repository = EnvRepositoryImpl(Provider { api })

    @Test
    fun `add sends only create fields and returns server records`() = runTest {
        val body = slot<List<EnvCreateRequest>>()
        val created = EnvInfo(id = 41, name = "NEW_VALUE", value = "secret", remarks = "note")
        coEvery { api.addEnvs(capture(body)) } returns ApiResponse(code = 200, data = listOf(created))

        val result = repository.addEnvs(listOf(Triple("NEW_VALUE", "secret", "note")))

        assertEquals(listOf(created), result.getOrThrow())
        assertEquals(listOf(EnvCreateRequest("NEW_VALUE", "secret", "note")), body.captured)
    }

    @Test
    fun `update sends the explicit id and editable fields`() = runTest {
        val body = slot<EnvUpdateRequest>()
        coEvery { api.updateEnv(capture(body)) } returns ApiResponse(code = 200, data = EnvInfo(id = 7))

        val result = repository.updateEnv(7, "NAME", "value", "remark")

        assertTrue(result.isSuccess)
        assertEquals(EnvUpdateRequest(7, "NAME", "value", "remark"), body.captured)
    }

    @Test
    fun `pin and unpin forward exact ids to official endpoints`() = runTest {
        coEvery { api.pinEnvs(listOf(3, 5)) } returns ApiResponse(code = 200)
        coEvery { api.unpinEnvs(listOf(3, 5)) } returns ApiResponse(code = 200)

        assertTrue(repository.pinEnvs(listOf(3, 5)).isSuccess)
        assertTrue(repository.unpinEnvs(listOf(3, 5)).isSuccess)

        coVerify(exactly = 1) { api.pinEnvs(listOf(3, 5)) }
        coVerify(exactly = 1) { api.unpinEnvs(listOf(3, 5)) }
    }

    @Test
    fun `business error remains readable without exposing values from the request`() = runTest {
        coEvery { api.addEnvs(any()) } returns ApiResponse(code = 500, message = "duplicate name")

        val error = repository.addEnvs(listOf(Triple("NAME", "sensitive-value", null))).exceptionOrNull()

        assertEquals("duplicate name", error?.message)
        assertTrue(error?.message.orEmpty().contains("sensitive-value").not())
    }

    @Test
    fun `cancellation is never converted to repository failure`() = runTest {
        val cancellation = CancellationException("cancel test")
        coEvery { api.getEnvs(any()) } throws cancellation

        try {
            repository.getEnvs("")
            fail("Expected CancellationException")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
