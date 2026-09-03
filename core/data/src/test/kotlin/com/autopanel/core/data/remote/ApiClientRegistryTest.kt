package com.autopanel.core.data.remote

import com.autopanel.core.data.security.ClientCertificateManager
import com.autopanel.core.data.session.SessionSnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ApiClientRegistryTest {
    private val certificateManager = mockk<ClientCertificateManager>()
    private lateinit var registry: ApiClientRegistry

    @Before
    fun setUp() {
        every { certificateManager.createSslConfig(null, null, null) } returns null
        registry = ApiClientRegistry(
            baseOkHttpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            certificateManager = certificateManager,
            keyFactory = ConnectionProfileKeyFactory()
        )
    }

    @Test
    fun `sixty requests reuse one client and token is not part of key`() {
        val initial = SessionSnapshot(
            host = "https://example.com",
            token = "token-0"
        )

        val clients = (0 until 60).map { index ->
            registry.getOrCreate(
                host = initial.host!!,
                session = initial.copy(token = "token-$index")
            )
        }

        clients.drop(1).forEach { assertSame(clients.first(), it) }
        assertEquals(
            ApiClientBuildMetrics(okHttpBuilds = 1, retrofitBuilds = 1),
            registry.buildMetrics()
        )
    }

    @Test
    fun `parallel first access creates one client`() = runTest {
        val session = SessionSnapshot(host = "https://example.com")

        val clients = withContext(Dispatchers.Default) {
            (0 until 20).map {
                async { registry.getOrCreate(session.host!!, session) }
            }.awaitAll()
        }

        clients.drop(1).forEach { assertSame(clients.first(), it) }
        assertEquals(1, registry.buildMetrics().retrofitBuilds)
    }

    @Test
    fun `host change creates a separate client`() {
        val first = registry.getOrCreate(
            host = "https://one.example.com",
            session = SessionSnapshot(host = "https://one.example.com")
        )
        val second = registry.getOrCreate(
            host = "https://two.example.com",
            session = SessionSnapshot(host = "https://two.example.com", connectionRevision = 1)
        )

        assertNotSame(first, second)
        assertEquals(2, registry.buildMetrics().retrofitBuilds)
    }

    @Test
    fun `connection generation without material change reuses existing key`() {
        val session = SessionSnapshot(host = "https://example.com")
        val first = registry.getOrCreate(session.host!!, session)
        val second = registry.getOrCreate(
            session.host,
            session.copy(connectionRevision = session.connectionRevision + 1)
        )

        assertSame(first, second)
        assertEquals(1, registry.buildMetrics().retrofitBuilds)
    }
}
