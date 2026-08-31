package com.autopanel.core.mcp

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
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
import kotlinx.serialization.json.put

@Singleton
class KotlinSdkMcpServerEngine @Inject constructor(
    private val httpSecurity: McpHttpSecurity,
    private val toolRegistry: McpToolRegistry,
    private val auditLogger: McpAuditLogger
) : McpServerEngine {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    override val state: StateFlow<McpServerState> = mutableState.asStateFlow()

    private var transportServer:
        EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    override suspend fun start(config: McpServerConfig) {
        lifecycleMutex.withLock {
            if (mutableState.value is McpServerState.Running || mutableState.value == McpServerState.Starting) {
                return@withLock
            }
            mutableState.value = McpServerState.Starting
            try {
                val ktorServer = embeddedServer(
                    factory = Netty,
                    host = config.bindAddress,
                    port = config.port
                ) {
                    install(RequestBodyLimit) {
                        bodyLimit { McpHttpSecurity.MAX_REQUEST_BODY_BYTES }
                    }
                    intercept(ApplicationCallPipeline.Plugins) {
                        if (call.request.path() != MCP_PATH) return@intercept
                        val authorizationResult = httpSecurity.authorize(
                            authorization = call.request.headers[HttpHeaders.Authorization],
                            host = call.request.headers[HttpHeaders.Host].orEmpty(),
                            origin = call.request.headers[HttpHeaders.Origin],
                            peer = "loopback",
                            contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        )
                        when (authorizationResult) {
                            is McpAuthorizationResult.Rejected -> {
                                call.respondText(
                                    text = errorJson(authorizationResult.code),
                                    contentType = ContentType.Application.Json,
                                    status = HttpStatusCode.fromValue(authorizationResult.statusCode)
                                )
                                finish()
                            }
                            is McpAuthorizationResult.Allowed -> {
                                call.attributes.put(MCP_CONTEXT_KEY, authorizationResult.context)
                                try {
                                    proceed()
                                } finally {
                                    httpSecurity.release(authorizationResult.context)
                                }
                            }
                        }
                    }
                    mcpStatelessStreamableHttp {
                        createProtocolServer(call.attributes[MCP_CONTEXT_KEY])
                    }
                }
                withContext(Dispatchers.IO) { ktorServer.start(wait = false) }
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
            if (mutableState.value == McpServerState.Stopped || mutableState.value == McpServerState.Stopping) {
                return@withLock
            }
            mutableState.value = McpServerState.Stopping
            val currentTransportServer = transportServer
            transportServer = null
            try {
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
            transportServer?.stop(gracePeriodMillis = 0, timeoutMillis = SHUTDOWN_TIMEOUT_MS)
        }
        transportServer = null
    }

    private fun createProtocolServer(context: McpCallContext): Server = Server(
        serverInfo = Implementation(name = "azureql-android", version = "phase1"),
        options = ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))
        )
    ).apply {
        toolRegistry.visibleTo(context.agent).forEach { tool ->
            addTool(
                name = tool.definition.name,
                description = tool.definition.description,
                inputSchema = ToolSchema(properties = tool.definition.inputProperties),
                toolAnnotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false)
            ) { request ->
                val startedAt = System.nanoTime()
                val outcome = try {
                    if (!context.agent.scopes.containsAll(tool.definition.requiredScopes)) {
                        McpToolOutcome.Failure("SCOPE_DENIED", "This Agent is not allowed to use the tool")
                    } else {
                        tool.invoke(context, request.arguments ?: buildJsonObject { })
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    McpToolOutcome.Failure("INTERNAL_ERROR", "The tool could not complete the request")
                }
                val durationMs = (System.nanoTime() - startedAt) / 1_000_000
                val success = outcome as? McpToolOutcome.Success
                auditLogger.record(
                    McpAuditEvent(
                        timestampEpochMs = System.currentTimeMillis(),
                        requestId = context.requestId,
                        agentId = context.agent.id.value,
                        agentName = context.agent.name,
                        tool = tool.definition.name,
                        risk = tool.definition.riskLevel.name,
                        outcome = if (success != null) "SUCCESS" else (outcome as McpToolOutcome.Failure).code,
                        durationMs = durationMs,
                        targetSummary = success?.targetSummary
                    )
                )
                when (outcome) {
                    is McpToolOutcome.Success -> CallToolResult(
                        content = listOf(TextContent(outcome.payload.toString()))
                    )
                    is McpToolOutcome.Failure -> CallToolResult(
                        content = listOf(TextContent(errorJson(outcome.code, outcome.message))),
                        isError = true
                    )
                }
            }
        }
    }

    private fun errorJson(code: String, message: String = "MCP request rejected"): String =
        buildJsonObject {
            put("ok", false)
            put("code", code)
            put("message", message)
        }.toString()

    companion object {
        private const val MCP_PATH = "/mcp"
        private const val SHUTDOWN_GRACE_PERIOD_MS = 500L
        private const val SHUTDOWN_TIMEOUT_MS = 3_000L
        private val MCP_CONTEXT_KEY = AttributeKey<McpCallContext>("AzureQLMcpCallContext")
    }
}
