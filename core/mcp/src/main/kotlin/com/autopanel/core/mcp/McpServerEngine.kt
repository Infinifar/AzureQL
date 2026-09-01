package com.autopanel.core.mcp

import kotlinx.coroutines.flow.StateFlow

interface McpServerEngine {
    val state: StateFlow<McpServerState>

    suspend fun start(config: McpServerConfig = McpServerConfig())

    suspend fun stop()
}
