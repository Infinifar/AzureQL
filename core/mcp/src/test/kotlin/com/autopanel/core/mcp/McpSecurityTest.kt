package com.autopanel.core.mcp

import com.autopanel.core.domain.ActiveAccountIdentity
import com.autopanel.core.domain.ActiveAccountIdentityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSecurityTest {
    @Test
    fun `limiter caps concurrent requests at four and releases permits`() {
        val limiter = McpRequestLimiter()
        val id = McpAgentId("agent")
        repeat(4) { assertTrue(limiter.acquire(id, now = 1L)) }
        assertFalse(limiter.acquire(id, now = 1L))
        limiter.release(id)
        assertTrue(limiter.acquire(id, now = 1L))
    }

    @Test
    fun `http security rejects non-loopback origin before token lookup`() = runBlocking {
        val fixture = securityFixture()
        val result = fixture.security.authorize(
            authorization = "Bearer token",
            host = "127.0.0.1:18765",
            origin = "https://attacker.example",
            peer = "loopback",
            contentLength = 10
        )
        assertEquals(403, (result as McpAuthorizationResult.Rejected).statusCode)
        assertEquals(0, fixture.store.authenticationCalls)
    }

    @Test
    fun `http security rejects valid agent after account switch`() = runBlocking {
        val fixture = securityFixture(currentAccountId = "different-account")
        val result = fixture.security.authorize(
            authorization = "Bearer token",
            host = "localhost:18765",
            origin = null,
            peer = "loopback",
            contentLength = null
        )
        assertEquals("ACCOUNT_NOT_ALLOWED", (result as McpAuthorizationResult.Rejected).code)
    }
}

private data class SecurityFixture(val security: McpHttpSecurity, val store: SecurityTestAgentStore)

private fun securityFixture(currentAccountId: String = "account"): SecurityFixture {
    val agent = McpAgent(
        id = McpAgentId("agent"),
        name = "Agent",
        scopes = McpAgentManager.DEFAULT_READ_SCOPES,
        allowedAccountIds = setOf("account"),
        createdAtEpochMs = 1L
    )
    val store = SecurityTestAgentStore(agent)
    val provider = object : ActiveAccountIdentityProvider {
        override suspend fun current() = ActiveAccountIdentity(currentAccountId, "Account")
    }
    return SecurityFixture(
        McpHttpSecurity(store, provider, McpRequestLimiter(), object : McpAuditLogger {
            override suspend fun record(event: McpAuditEvent) = Unit
        }),
        store
    )
}

private class SecurityTestAgentStore(private val agent: McpAgent) : McpAgentStore {
    override val agents: StateFlow<List<McpAgent>> = MutableStateFlow(listOf(agent))
    var authenticationCalls = 0
    override suspend fun issue(
        name: String,
        scopes: Set<McpScope>,
        accountIds: Set<String>
    ): McpIssuedCredential = error("not used")
    override suspend fun authenticate(token: String): McpAgent? {
        authenticationCalls++
        return agent.takeIf { token == "token" }
    }
    override suspend fun revoke(agentId: McpAgentId) = Unit
}
