package com.autopanel.core.mcp

data class McpServerConfig(
    val port: Int = DEFAULT_PORT,
    val bindAddress: String = LOOPBACK_ADDRESS
) {
    init {
        require(port in 1024..65535) { "MCP port must be between 1024 and 65535" }
        require(bindAddress == LOOPBACK_ADDRESS) {
            "Phase 0 MCP may only bind to $LOOPBACK_ADDRESS"
        }
    }

    val endpoint: String
        get() = "http://$bindAddress:$port/mcp"

    companion object {
        const val DEFAULT_PORT = 18765
        const val LOOPBACK_ADDRESS = "127.0.0.1"
    }
}
