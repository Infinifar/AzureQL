package com.autopanel.core.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.PUT

class AutoPanelApiServiceContractTest {
    @Test
    fun `config detail uses QingLong 2_23 path query contract`() {
        val method = AutoPanelApiService::class.java.methods.single { it.name == "getConfigContent" }

        assertEquals(
            "api/configs/detail",
            requireNotNull(method.getAnnotation(GET::class.java)).value
        )
        assertTrue(
            method.parameterAnnotations
                .flatten()
                .filterIsInstance<Query>()
                .any { it.value == "path" }
        )
    }

    @Test
    fun `dashboard result detail endpoints match QingLong 2_23`() {
        val successes = AutoPanelApiService::class.java.methods.single { it.name == "getDashboardSuccesses" }
        val failures = AutoPanelApiService::class.java.methods.single { it.name == "getDashboardFailures" }

        assertEquals(
            "api/dashboard/successes",
            requireNotNull(successes.getAnnotation(GET::class.java)).value
        )
        assertEquals(
            "api/dashboard/failures",
            requireNotNull(failures.getAnnotation(GET::class.java)).value
        )
    }

    @Test
    fun `script save and history endpoints match QingLong 2_23`() {
        val save = AutoPanelApiService::class.java.methods.single { it.name == "updateScript" }
        val history = AutoPanelApiService::class.java.methods.single { it.name == "getScriptHistory" }
        val detail = AutoPanelApiService::class.java.methods.single { it.name == "getScriptHistoryDetail" }
        val restore = AutoPanelApiService::class.java.methods.single { it.name == "restoreScriptHistory" }

        assertEquals("api/scripts", requireNotNull(save.getAnnotation(PUT::class.java)).value)
        assertEquals("api/scripts/history", requireNotNull(history.getAnnotation(GET::class.java)).value)
        assertEquals(
            "api/scripts/history/detail",
            requireNotNull(detail.getAnnotation(GET::class.java)).value
        )
        assertEquals(
            "api/scripts/history/restore",
            requireNotNull(restore.getAnnotation(PUT::class.java)).value
        )
    }
}
