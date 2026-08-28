package com.autopanel.core.data.cache

import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.SessionSnapshot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ResponseCacheTest {
    private val dao = mockk<CacheEntryDao>()
    private val sessionManager = mockk<SessionManager>()
    private val cipher = mockk<CachePayloadCipher>()
    private lateinit var cache: ResponseCache

    @Before
    fun setUp() {
        coEvery { sessionManager.getSession() } returns SessionSnapshot(
            host = "https://ql.example.com",
            username = "admin"
        )
        cache = ResponseCache(dao, sessionManager, Json, cipher)
    }

    @Test
    fun `prune deletes entries older than eight days and trims overflow`() = runTest {
        val nowMillis = 1_800_000_000_000L
        coEvery { dao.deleteOlderThan(any()) } returns 2
        coEvery { dao.trimToNewest(any()) } returns 1

        assertEquals(3, cache.prune(nowMillis))

        coVerify(exactly = 1) {
            dao.deleteOlderThan(nowMillis - TimeUnit.DAYS.toMillis(8))
            dao.trimToNewest(ResponseCache.MAX_ENTRIES)
        }
    }

    @Test
    fun `read removes expired entry without decrypting it`() = runTest {
        coEvery { dao.find(any(), ResponseCache.DASHBOARD_OVERVIEW) } returns CacheEntry(
            scope = "stored-scope",
            cacheKey = ResponseCache.DASHBOARD_OVERVIEW,
            encryptedPayload = byteArrayOf(1, 2, 3),
            updatedAtMillis = 0L
        )
        coEvery { dao.delete(any(), ResponseCache.DASHBOARD_OVERVIEW) } just Runs
        every { cipher.decrypt(any()) } returns "should-not-be-read"

        val result = cache.read(ResponseCache.DASHBOARD_OVERVIEW, String.serializer())

        assertNull(result)
        coVerify(exactly = 1) { dao.delete(any(), ResponseCache.DASHBOARD_OVERVIEW) }
        verify(exactly = 0) { cipher.decrypt(any()) }
    }
}
