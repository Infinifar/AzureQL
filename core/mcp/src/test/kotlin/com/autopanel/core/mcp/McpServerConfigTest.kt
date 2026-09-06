package com.autopanel.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerConfigTest {
    @Test
    fun `default config remains loopback only`() {
        val config = McpServerConfig(port = 18765)

        assertEquals("http://127.0.0.1:18765/mcp", config.endpoint)
        assertEquals("127.0.0.1", config.bindAddress)
        assertEquals(McpNetworkAccess.LOOPBACK_ONLY, config.networkAccess)
    }

    @Test
    fun `local network config binds all interfaces and formats ipv6 endpoints`() {
        val config = McpServerConfig(
            port = 18765,
            networkAccess = McpNetworkAccess.LOCAL_NETWORK
        )

        assertEquals("0.0.0.0", config.bindAddress)
        assertEquals("http://[fd00::1234]:18765/mcp", config.endpointFor("fd00::1234"))
        assertTrue(config.allowedHostAddresses().contains("127.0.0.1"))
    }
}
