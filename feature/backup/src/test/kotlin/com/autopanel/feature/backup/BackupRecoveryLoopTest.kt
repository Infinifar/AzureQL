package com.autopanel.feature.backup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRecoveryLoopTest {

    @Test
    fun `health polling reports each attempt and returns first success`() = runTest {
        val reported = mutableListOf<Int>()
        var checks = 0

        val recoveredAt = awaitHealthyService(
            attempts = 30,
            delayMillis = 2_000,
            healthCheck = { ++checks == 3 },
            onAttempt = reported::add
        )

        assertEquals(3, recoveredAt)
        assertEquals(listOf(1, 2, 3), reported)
        assertEquals(6_000L, currentTime)
    }

    @Test
    fun `health polling returns null after bounded timeout`() = runTest {
        val reported = mutableListOf<Int>()

        val recoveredAt = awaitHealthyService(
            attempts = 3,
            delayMillis = 2_000,
            healthCheck = { false },
            onAttempt = reported::add
        )

        assertNull(recoveredAt)
        assertEquals(listOf(1, 2, 3), reported)
        assertEquals(6_000L, currentTime)
    }
}
