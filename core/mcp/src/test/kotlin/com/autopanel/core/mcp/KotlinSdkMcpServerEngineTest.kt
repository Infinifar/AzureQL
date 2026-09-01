package com.autopanel.core.mcp

import com.autopanel.core.domain.ActiveAccountIdentity
import com.autopanel.core.domain.ActiveAccountIdentityProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.mockk.every
import io.mockk.mockk
import java.net.ServerSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinSdkMcpServerEngineTest {
    @Test
    fun `authorized official client discovers and calls only visible tool`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val port = availablePort()
            val config = McpServerConfig(port = port)
            val agent = testAgent()
            val store = FakeAgentStore(agent, TOKEN)
            val audit = RecordingAuditLogger()
            val security = McpHttpSecurity(
                agentStore = store,
                accountIdentityProvider = FakeAccountProvider(),
                limiter = McpRequestLimiter(),
                auditLogger = audit
            )
            val tool = object : AzureQlMcpTool {
                override val definition = McpToolDefinition(
                    name = "server_status",
                    description = "test",
                    requiredScopes = setOf(McpScope.STATUS_READ),
                    riskLevel = McpRiskLevel.LOW_READ
                )
                override suspend fun invoke(
                    context: McpCallContext,
                    arguments: JsonObject
                ): McpToolOutcome = McpToolOutcome.Success(buildJsonObject { put("ok", true) })
            }
            val registry = mockk<McpToolRegistry>()
            every { registry.visibleTo(any()) } returns listOf(tool)
            val engine = KotlinSdkMcpServerEngine(security, registry, audit)
            val httpClient = HttpClient(CIO) { install(SSE) }
            val client = Client(Implementation(name = "azureql-test", version = "1"))

            try {
                engine.start(config)
                assertEquals(McpServerState.Running(config.endpoint), engine.state.value)
                client.connect(
                    StreamableHttpClientTransport(httpClient, config.endpoint) {
                        header(HttpHeaders.Authorization, "Bearer $TOKEN")
                    }
                )
                assertEquals(listOf("server_status"), client.listTools().tools.map { it.name })
                val result = client.callTool("server_status", emptyMap())
                assertTrue(result.content.filterIsInstance<TextContent>().single().text.contains("\"ok\":true"))
            } finally {
                client.close()
                httpClient.close()
                engine.stop()
            }

            assertEquals(McpServerState.Stopped, engine.state.value)
            assertTrue(audit.events.any { it.tool == "server_status" && it.outcome == "SUCCESS" })
            ServerSocket(port).use { socket -> assertEquals(port, socket.localPort) }
        }
    }

    private fun availablePort(): Int = ServerSocket(0).use { it.localPort }

    companion object {
        private const val TOKEN = "azql_mcp_v1_test-token"
        private const val TEST_TIMEOUT_MS = 30_000L
    }
}

private class FakeAgentStore(private val agent: McpAgent, private val token: String) : McpAgentStore {
    private val state = MutableStateFlow(listOf(agent))
    override val agents: StateFlow<List<McpAgent>> = state
    override suspend fun issue(
        name: String,
        scopes: Set<McpScope>,
        accountIds: Set<String>
    ): McpIssuedCredential = error("not used")
    override suspend fun authenticate(token: String): McpAgent? = agent.takeIf { token == this.token }
    override suspend fun rename(agentId: McpAgentId, name: String): McpAgent = agent.copy(name = name)
    override suspend fun updateScopes(agentId: McpAgentId, scopes: Set<McpScope>): McpAgent =
        agent.copy(scopes = scopes)
    override suspend fun revoke(agentId: McpAgentId) = Unit
}

private class FakeAccountProvider : ActiveAccountIdentityProvider {
    override suspend fun current() = ActiveAccountIdentity("account-1", "Test")
}

private class RecordingAuditLogger : McpAuditLogger {
    val events = mutableListOf<McpAuditEvent>()
    override suspend fun record(event: McpAuditEvent) { events += event }
}

private fun testAgent() = McpAgent(
    id = McpAgentId("agent-1"),
    name = "Test Agent",
    scopes = setOf(McpScope.STATUS_READ),
    allowedAccountIds = setOf("account-1"),
    createdAtEpochMs = 1L
)
