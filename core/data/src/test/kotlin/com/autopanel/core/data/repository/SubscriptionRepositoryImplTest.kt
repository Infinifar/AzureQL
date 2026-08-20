package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.SubscriptionDraft
import com.autopanel.core.model.SubscriptionLogResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Provider

class SubscriptionRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val provider = mockk<Provider<AutoPanelApiService>> {
        every { get() } returns api
    }
    private val repository = SubscriptionRepositoryImpl(provider)

    @Test
    fun `create subscription accepts official object response and sends typed payload`() = runTest {
        coEvery { api.addSubscription(any()) } returns
            ApiResponse<JsonElement>(code = 200, data = JsonNull)

        val result = repository.addSubscription(
            SubscriptionDraft(
                name = "daily scripts",
                url = "https://github.com/owner/repo.git",
                schedule = "0 0 * * *",
                alias = "owner_repo",
                autoAddCron = true,
                autoDelCron = false
            )
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            api.addSubscription(match { payload ->
                payload["name"]?.jsonPrimitive?.content == "daily scripts" &&
                    payload["autoAddCron"]?.jsonPrimitive?.content == "true" &&
                    payload["autoDelCron"]?.jsonPrimitive?.content == "false" &&
                    "id" !in payload
            })
        }
    }

    @Test
    fun `interval subscription sends nested interval schedule`() = runTest {
        coEvery { api.updateSubscription(any()) } returns
            ApiResponse<JsonElement>(code = 200, data = JsonNull)

        val result = repository.updateSubscription(
            SubscriptionDraft(
                id = 8,
                name = "hourly",
                url = "https://example.com/task.py",
                type = "file",
                scheduleType = "interval",
                intervalType = "hours",
                intervalValue = 2,
                alias = "hourly"
            )
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            api.updateSubscription(match { payload ->
                val interval = payload["interval_schedule"]?.jsonObject
                payload["id"]?.jsonPrimitive?.content == "8" &&
                    interval?.get("type")?.jsonPrimitive?.content == "hours" &&
                    interval["value"]?.jsonPrimitive?.content == "2" &&
                    "schedule" !in payload
            })
        }
    }

    @Test
    fun `subscription log keeps chunk metadata`() = runTest {
        coEvery { api.getSubscriptionLog(9, 128, 4096, false) } returns
            SubscriptionLogResponse(
                code = 200,
                data = "next lines",
                content = "next lines",
                offset = 128,
                nextOffset = 138,
                total = 512,
                truncated = true
            )

        val result = repository.getSubscriptionLog(9, offset = 128, limit = 4096)

        assertTrue(result.isSuccess)
        val chunk = result.getOrThrow()
        assertEquals("next lines", chunk.content)
        assertEquals(128L, chunk.offset)
        assertEquals(138L, chunk.nextOffset)
        assertEquals(512L, chunk.total)
        assertTrue(chunk.truncated)
    }
}
