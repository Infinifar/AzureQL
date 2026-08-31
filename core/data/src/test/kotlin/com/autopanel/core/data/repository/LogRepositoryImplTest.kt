package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import io.mockk.coEvery
import io.mockk.mockk
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRepositoryImplTest {
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
