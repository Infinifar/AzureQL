package com.autopanel.feature.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.mcp.McpAgent
import com.autopanel.core.mcp.McpAgentId
import com.autopanel.core.mcp.McpAgentManager
import com.autopanel.core.mcp.McpIssuedCredential
import com.autopanel.core.mcp.McpServerEngine
import com.autopanel.core.mcp.McpServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class McpSettingsViewModel @Inject constructor(
    engine: McpServerEngine,
    private val serviceController: McpServiceController,
    private val agentManager: McpAgentManager
) : ViewModel() {
    val state: StateFlow<McpServerState> = engine.state
    val agents: StateFlow<List<McpAgent>> = agentManager.agents

    private val mutableCredential = MutableStateFlow<McpIssuedCredential?>(null)
    val credential: StateFlow<McpIssuedCredential?> = mutableCredential.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()
    private val mutableAgentOperationInProgress = MutableStateFlow(false)
    val agentOperationInProgress: StateFlow<Boolean> = mutableAgentOperationInProgress.asStateFlow()

    fun startService() = serviceController.start()
    fun stopService() = serviceController.stop()

    fun createReadOnlyAgent() {
        if (mutableAgentOperationInProgress.value) return
        viewModelScope.launch {
            mutableAgentOperationInProgress.value = true
            mutableError.value = null
            agentManager.issueReadOnlyAgent("Local AI agent")
                .onSuccess { mutableCredential.value = it }
                .onFailure { mutableError.value = it.message ?: "Unable to create MCP Agent" }
            mutableAgentOperationInProgress.value = false
        }
    }

    fun revokeAgent(agentId: McpAgentId) {
        viewModelScope.launch {
            agentManager.revoke(agentId)
            if (agents.value.isEmpty()) stopService()
        }
    }

    fun dismissCredential() {
        mutableCredential.value = null
    }

    fun dismissError() {
        mutableError.value = null
    }
}
