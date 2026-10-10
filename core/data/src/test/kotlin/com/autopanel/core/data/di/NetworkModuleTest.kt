package com.autopanel.core.data.di

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.SessionSnapshot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `authenticated http 401 expires local token`() {
        val sessionManager = mockk<SessionManager>()
        val chain = mockk<Interceptor.Chain>()
        val sentRequest = slot<Request>()
        val original = Request.Builder().url("https://ql.example/api/crons").build()
        every { sessionManager.currentSession } returns SessionSnapshot(token = "expired-token")
        every { sessionManager.expireSessionFromNetwork() } just runs
        every { chain.request() } returns original
        every { chain.proceed(capture(sentRequest)) } answers {
            Response.Builder()
                .request(sentRequest.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build()
        }

        NetworkModule.provideOkHttpClient(sessionManager).interceptors.first().intercept(chain)

        assertEquals("Bearer expired-token", sentRequest.captured.header("Authorization"))
        verify(exactly = 1) { sessionManager.expireSessionFromNetwork() }
    }

    @Test
    fun `no auth http 401 does not expire an existing account session`() {
        val sessionManager = mockk<SessionManager>()
        val chain = mockk<Interceptor.Chain>()
        val sentRequest = slot<Request>()
        val original = Request.Builder()
            .url("https://ql.example/api/user/login")
            .header(AutoPanelApiService.NO_AUTH_HEADER, "true")
            .build()
        every { sessionManager.currentSession } returns SessionSnapshot(token = "existing-token")
        every { chain.request() } returns original
        every { chain.proceed(capture(sentRequest)) } answers {
            Response.Builder()
                .request(sentRequest.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build()
        }

        NetworkModule.provideOkHttpClient(sessionManager).interceptors.first().intercept(chain)

        assertNull(sentRequest.captured.header("Authorization"))
        assertNull(sentRequest.captured.header(AutoPanelApiService.NO_AUTH_HEADER))
        verify(exactly = 0) { sessionManager.expireSessionFromNetwork() }
    }
}
