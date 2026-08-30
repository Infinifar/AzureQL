package com.autopanel.core.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinSdkMcpServerEngineTest {
    @Test
    fun `official client can discover and call hello then port is released`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val port = availablePort()
            val config = McpServerConfig(port = port)
            val engine = KotlinSdkMcpServerEngine()
            val httpClient = HttpClient(CIO) { install(SSE) }
            val client = Client(Implementation(name = "azureql-test", version = "1"))

            try {
                engine.start(config)
                assertEquals(McpServerState.Running(config.endpoint), engine.state.value)

                client.connect(StreamableHttpClientTransport(httpClient, config.endpoint))
                assertTrue(client.listTools().tools.any { it.name == "hello" })

                repeat(HELLO_CALL_COUNT) { index ->
                    val name = "Codex-$index"
                    val result = client.callTool(
                        name = "hello",
                        arguments = mapOf("name" to JsonPrimitive(name))
                    )
                    val text = result.content.filterIsInstance<TextContent>().single().text
                    assertEquals("AzureQL MCP is running, $name.", text)
                }
            } finally {
                client.close()
                httpClient.close()
                engine.stop()
            }

            assertEquals(McpServerState.Stopped, engine.state.value)
            ServerSocket(port).use { socket -> assertEquals(port, socket.localPort) }
        }
    }

    private fun availablePort(): Int = ServerSocket(0).use { it.localPort }

    companion object {
        private const val HELLO_CALL_COUNT = 100
        private const val TEST_TIMEOUT_MS = 30_000L
    }
}
