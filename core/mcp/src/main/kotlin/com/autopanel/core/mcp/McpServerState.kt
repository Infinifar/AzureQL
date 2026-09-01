package com.autopanel.core.mcp

sealed interface McpServerState {
    data object Stopped : McpServerState
    data object Starting : McpServerState
    data class Running(val endpoint: String) : McpServerState
    data object Stopping : McpServerState
    data class Failed(val message: String) : McpServerState
}
