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

    @Test
    fun `local network mode accepts only an enumerated interface host and matching origin`() = runBlocking {
        val fixture = securityFixture()
        val allowed = fixture.security.authorize(
            authorization = "Bearer token",
            host = "192.168.1.45:18765",
            origin = "http://192.168.1.45:3000",
            peer = "192.168.1.20",
            contentLength = null,
            networkAccess = McpNetworkAccess.LOCAL_NETWORK,
            allowedHosts = setOf("192.168.1.45")
        )
        assertTrue(allowed is McpAuthorizationResult.Allowed)
        fixture.security.release((allowed as McpAuthorizationResult.Allowed).context)

        val unboundHost = fixture.security.authorize(
            authorization = "Bearer token",
            host = "192.168.1.99:18765",
            origin = null,
            peer = "192.168.1.20",
            contentLength = null,
            networkAccess = McpNetworkAccess.LOCAL_NETWORK,
            allowedHosts = setOf("192.168.1.45")
        )
        assertEquals("HOST_OR_ORIGIN_REJECTED", (unboundHost as McpAuthorizationResult.Rejected).code)

        val mismatchedOrigin = fixture.security.authorize(
            authorization = "Bearer token",
            host = "192.168.1.45:18765",
            origin = "http://192.168.1.99:3000",
            peer = "192.168.1.20",
            contentLength = null,
            networkAccess = McpNetworkAccess.LOCAL_NETWORK,
            allowedHosts = setOf("192.168.1.45", "192.168.1.99")
        )
        assertEquals("HOST_OR_ORIGIN_REJECTED", (mismatchedOrigin as McpAuthorizationResult.Rejected).code)
    }

    @Test
    fun `agent manager trims and persists a valid renamed agent`() = runBlocking {
        val fixture = securityFixture()
        val manager = McpAgentManager(fixture.store, object : ActiveAccountIdentityProvider {
            override suspend fun current() = ActiveAccountIdentity("account", "Account")
        })
        val renamed = manager.rename(McpAgentId("agent"), "  Build Agent  ").getOrThrow()
        assertEquals("Build Agent", renamed.name)
        assertEquals("Build Agent", fixture.store.agents.value.single().name)
        assertTrue(manager.rename(McpAgentId("agent"), "   ").isFailure)
    }

    @Test
    fun `phase 2 permission changes preserve read scopes and agent identity`() = runBlocking {
        val fixture = securityFixture()
        val manager = McpAgentManager(fixture.store, object : ActiveAccountIdentityProvider {
            override suspend fun current() = ActiveAccountIdentity("account", "Account")
        })
        val enabled = manager.setPhase2Access(McpAgentId("agent"), true).getOrThrow()
        assertEquals("agent", enabled.id.value)
        assertTrue(enabled.scopes.containsAll(McpAgentManager.DEFAULT_READ_SCOPES))
        assertTrue(enabled.hasPhase2Access())

        val disabled = manager.setPhase2Access(McpAgentId("agent"), false).getOrThrow()
        assertTrue(disabled.scopes.containsAll(McpAgentManager.DEFAULT_READ_SCOPES))
        assertFalse(disabled.hasPhase2Access())
    }

    @Test
    fun `deleting an account removes its MCP agent access`() = runBlocking {
        val fixture = securityFixture()
        val manager = McpAgentManager(fixture.store, object : ActiveAccountIdentityProvider {
            override suspend fun current() = ActiveAccountIdentity("account", "Account")
        })

        manager.removeAccountAccess("account")

        assertTrue(fixture.store.agents.value.isEmpty())
    }

    @Test
    fun `silent approval requires phase 2 and is revoked when phase 2 is disabled`() = runBlocking {
        val fixture = securityFixture()
        val manager = McpAgentManager(fixture.store, object : ActiveAccountIdentityProvider {
            override suspend fun current() = ActiveAccountIdentity("account", "Account")
        })

        assertTrue(manager.setSilentWriteApproval(McpAgentId("agent"), true).isFailure)
        manager.setPhase2Access(McpAgentId("agent"), true).getOrThrow()
        val enabled = manager.setSilentWriteApproval(McpAgentId("agent"), true).getOrThrow()
        assertTrue(enabled.hasSilentWriteApproval())

        val downgraded = manager.setPhase2Access(McpAgentId("agent"), false).getOrThrow()
        assertFalse(downgraded.hasSilentWriteApproval())
        assertEquals(McpWriteApprovalMode.PER_OPERATION, downgraded.writeApprovalMode)
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

private class SecurityTestAgentStore(agent: McpAgent) : McpAgentStore {
    private val mutableAgents = MutableStateFlow(listOf(agent))
    override val agents: StateFlow<List<McpAgent>> = mutableAgents
    var authenticationCalls = 0
    override suspend fun issue(
        name: String,
        scopes: Set<McpScope>,
        accountIds: Set<String>
    ): McpIssuedCredential = error("not used")
    override suspend fun authenticate(token: String): McpAgent? {
        authenticationCalls++
        return mutableAgents.value.single().takeIf { token == "token" }
    }
    override suspend fun rename(agentId: McpAgentId, name: String): McpAgent {
        val updated = mutableAgents.value.single().copy(name = name)
        mutableAgents.value = listOf(updated)
        return updated
    }
    override suspend fun updateScopes(agentId: McpAgentId, scopes: Set<McpScope>): McpAgent {
        val current = mutableAgents.value.single()
        val updated = current.copy(
            scopes = scopes,
            writeApprovalMode = if (scopes.containsAll(McpAgentManager.PHASE_2_SCOPES)) {
                current.writeApprovalMode
            } else {
                McpWriteApprovalMode.PER_OPERATION
            }
        )
        mutableAgents.value = listOf(updated)
        return updated
    }
    override suspend fun updateWriteApprovalMode(
        agentId: McpAgentId,
        mode: McpWriteApprovalMode
    ): McpAgent {
        val updated = mutableAgents.value.single().copy(writeApprovalMode = mode)
        mutableAgents.value = listOf(updated)
        return updated
    }
    override suspend fun removeAccountAccess(accountId: String) {
        mutableAgents.value = mutableAgents.value.mapNotNull { agent ->
            val remaining = agent.allowedAccountIds - accountId
            agent.copy(allowedAccountIds = remaining).takeIf { remaining.isNotEmpty() }
        }
    }
    override suspend fun revoke(agentId: McpAgentId) = Unit
}
