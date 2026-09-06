package com.autopanel.core.mcp

sealed interface McpServerState {
    data object Stopped : McpServerState
    data object Starting : McpServerState
    data class Running(
        val endpoint: String,
        val networkAccess: McpNetworkAccess = McpNetworkAccess.LOOPBACK_ONLY,
        val accessibleEndpoints: List<String> = listOf(endpoint)
    ) : McpServerState
    data object Stopping : McpServerState
    data class Failed(val message: String) : McpServerState
}
