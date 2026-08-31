package com.autopanel.feature.mcp

import com.autopanel.core.mcp.McpServerConfig
import com.autopanel.core.mcp.McpServerEngine
import com.autopanel.core.mcp.McpServerState
import com.autopanel.core.mcp.McpAgent
import com.autopanel.core.mcp.McpAgentId
import com.autopanel.core.mcp.McpAgentManager
import com.autopanel.core.mcp.McpAgentStore
import com.autopanel.core.mcp.McpIssuedCredential
import com.autopanel.core.mcp.McpScope
import com.autopanel.core.domain.ActiveAccountIdentity
import com.autopanel.core.domain.ActiveAccountIdentityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSettingsViewModelTest {
    @Test
    fun `screen actions delegate to service controller and expose engine state`() {
        val engine = FakeEngine()
        val controller = FakeController()
        val viewModel = McpSettingsViewModel(
            engine,
            controller,
            McpAgentManager(FakeAgentStore(), FakeAccountProvider())
        )

        engine.mutableState.value = McpServerState.Running("http://127.0.0.1:18765/mcp")
        viewModel.startService()
        viewModel.stopService()

        assertEquals(engine.mutableState.value, viewModel.state.value)
        assertTrue(controller.started)
        assertTrue(controller.stopped)
    }
}

private class FakeAgentStore : McpAgentStore {
    private val mutableAgents = MutableStateFlow<List<McpAgent>>(emptyList())
    override val agents: StateFlow<List<McpAgent>> = mutableAgents
    override suspend fun issue(
        name: String,
        scopes: Set<McpScope>,
        accountIds: Set<String>
    ): McpIssuedCredential = error("not used")
    override suspend fun authenticate(token: String): McpAgent? = null
    override suspend fun rename(agentId: McpAgentId, name: String): McpAgent = error("not used")
    override suspend fun revoke(agentId: McpAgentId) = Unit
}

private class FakeAccountProvider : ActiveAccountIdentityProvider {
    override suspend fun current() = ActiveAccountIdentity("account", "Account")
}

private class FakeController : McpServiceController {
    var started = false
    var stopped = false

    override fun start() {
        started = true
    }

    override fun stop() {
        stopped = true
    }
}

private class FakeEngine : McpServerEngine {
    val mutableState = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    override val state: StateFlow<McpServerState> = mutableState

    override suspend fun start(config: McpServerConfig) = Unit
    override suspend fun stop() = Unit
}
