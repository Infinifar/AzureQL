package com.autopanel.core.mcp

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Singleton
class KotlinSdkMcpServerEngine @Inject constructor() : McpServerEngine {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    override val state: StateFlow<McpServerState> = mutableState.asStateFlow()

    private var transportServer:
        EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var protocolServer: Server? = null

    override suspend fun start(config: McpServerConfig) {
        lifecycleMutex.withLock {
            if (
                mutableState.value is McpServerState.Running ||
                mutableState.value == McpServerState.Starting
            ) return@withLock

            mutableState.value = McpServerState.Starting
            try {
                val sdkServer = createProtocolServer()
                val ktorServer = embeddedServer(
                    factory = Netty,
                    host = config.bindAddress,
                    port = config.port
                ) {
                    mcpStatelessStreamableHttp { sdkServer }
                }
                withContext(Dispatchers.IO) { ktorServer.start(wait = false) }
                protocolServer = sdkServer
                transportServer = ktorServer
                mutableState.value = McpServerState.Running(config.endpoint)
            } catch (cancelled: CancellationException) {
                resetAfterFailedStart()
                throw cancelled
            } catch (error: Exception) {
                resetAfterFailedStart()
                mutableState.value = McpServerState.Failed(error.message ?: "Unable to start MCP server")
                throw error
            }
        }
    }

    override suspend fun stop() {
        lifecycleMutex.withLock {
            if (
                mutableState.value == McpServerState.Stopped ||
                mutableState.value == McpServerState.Stopping
            ) return@withLock

            mutableState.value = McpServerState.Stopping
            val currentProtocolServer = protocolServer
            val currentTransportServer = transportServer
            protocolServer = null
            transportServer = null
            try {
                currentProtocolServer?.close()
                withContext(Dispatchers.IO) {
                    currentTransportServer?.stop(
                        gracePeriodMillis = SHUTDOWN_GRACE_PERIOD_MS,
                        timeoutMillis = SHUTDOWN_TIMEOUT_MS
                    )
                }
            } finally {
                mutableState.value = McpServerState.Stopped
            }
        }
    }

    private fun resetAfterFailedStart() {
        runCatching {
            transportServer?.stop(
                gracePeriodMillis = 0,
                timeoutMillis = SHUTDOWN_TIMEOUT_MS
            )
        }
        protocolServer = null
        transportServer = null
    }

    private fun createProtocolServer(): Server = Server(
        serverInfo = Implementation(name = "azureql-android", version = "phase0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false)
            )
        )
    ).apply {
        addTool(
            name = "hello",
            description = "Verify that the user-started AzureQL MCP service is reachable.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Optional name to include in the greeting")
                    }
                }
            ),
            toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false)
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content?.trim().orEmpty()
            val suffix = name.takeIf { it.isNotEmpty() }?.let { ", $it" }.orEmpty()
            CallToolResult(
                content = listOf(TextContent("AzureQL MCP is running$suffix."))
            )
        }
    }

    companion object {
        private const val SHUTDOWN_GRACE_PERIOD_MS = 500L
        private const val SHUTDOWN_TIMEOUT_MS = 3_000L
    }
}
