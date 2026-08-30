package com.autopanel.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerConfigTest {
    @Test
    fun `phase zero config is loopback only`() {
        val config = McpServerConfig(port = 18765)

        assertEquals("http://127.0.0.1:18765/mcp", config.endpoint)
        val error = runCatching { McpServerConfig(bindAddress = "0.0.0.0") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
