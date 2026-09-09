package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.model.ApiResponse
import io.mockk.coEvery
import io.mockk.mockk
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryImplTest {
    @Test
    fun `dynamic notification fields are returned unchanged`() = runTest {
        val api = mockk<AutoPanelApiService>()
        val config = JsonObject(mapOf("type" to JsonPrimitive("future"), "newField" to JsonPrimitive("value")))
        coEvery { api.getNotificationConfig() } returns ApiResponse(code = 200, data = config)

        val result = NotificationRepositoryImpl(Provider { api }).getConfig().getOrThrow()

        assertEquals(config, result)
    }

    @Test
    fun `failed server test does not report save success`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.updateNotificationConfig(any()) } returns ApiResponse(code = 400, message = "test failed")

        val result = NotificationRepositoryImpl(Provider { api }).testAndSave(JsonObject(emptyMap()))

        assertTrue(result.isFailure)
        assertEquals("test failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `notification request preserves caller cancellation`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.getNotificationConfig() } throws CancellationException("cancelled")
        val repository = NotificationRepositoryImpl(Provider { api })

        var propagated = false
        try {
            repository.getConfig()
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
    }

    @Test
    fun `http 500 is classified without exposing server detail`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.updateNotificationConfig(any()) } throws HttpException(
            Response.error<Any>(
                500,
                """{"message":"getaddrinfo ENOTFOUND private.example?token=secret"}""".toResponseBody()
            )
        )

        val error = NotificationRepositoryImpl(Provider { api })
            .testAndSave(JsonObject(emptyMap())).exceptionOrNull()

        assertEquals("通知服务地址无法解析，请检查服务地址和青龙容器网络", error?.message)
        assertTrue(error?.message?.contains("secret") == false)
    }

    @Test
    fun `generic http 500 explains that notification configuration was not saved`() = runTest {
        val api = mockk<AutoPanelApiService>()
        coEvery { api.updateNotificationConfig(any()) } throws HttpException(
            Response.error<Any>(500, "Internal Server Error".toResponseBody())
        )

        val error = NotificationRepositoryImpl(Provider { api })
            .testAndSave(JsonObject(emptyMap())).exceptionOrNull()

        assertEquals(
            "青龙服务端测试通知失败（HTTP 500），设置未保存；请检查通知渠道配置、青龙容器网络和系统日志",
            error?.message
        )
    }
}
