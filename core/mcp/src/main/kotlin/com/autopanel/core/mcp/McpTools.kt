package com.autopanel.core.mcp

import com.autopanel.core.domain.DashboardRepository
import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.domain.EnvRepository
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.ScriptFile
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class McpToolDefinition(
    val name: String,
    val description: String,
    val requiredScopes: Set<McpScope>,
    val riskLevel: McpRiskLevel,
    val inputProperties: JsonObject = JsonObject(emptyMap()),
    val requiredInput: List<String> = emptyList()
)

sealed interface McpToolOutcome {
    data class Success(
        val payload: JsonObject,
        val targetSummary: String? = null,
        val auditOutcome: String = "SUCCESS"
    ) : McpToolOutcome
    data class Failure(
        val code: String,
        val message: String,
        val targetSummary: String? = null
    ) : McpToolOutcome
}

interface AzureQlMcpTool {
    val definition: McpToolDefinition
    suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome
}

internal fun McpToolDefinition.validateArguments(arguments: JsonObject): McpToolOutcome.Failure? {
    if (requiredInput.any { it !in arguments }) {
        return McpToolOutcome.Failure("INVALID_ARGUMENT", "One or more required arguments are missing")
    }
    if (arguments.keys.any { it !in inputProperties }) {
        return McpToolOutcome.Failure("INVALID_ARGUMENT", "One or more arguments are not supported by this tool")
    }
    return null
}

@Singleton
class McpToolRegistry @Inject constructor(
    serverStatus: ServerStatusTool,
    listTasks: ListTasksTool,
    listScripts: ListScriptsTool,
    readScript: ReadScriptTool,
    listDependencies: ListDependenciesTool,
    checkDependency: CheckDependencyTool,
    listEnvs: ListEnvsTool,
    listLogs: ListLogsTool,
    readLogTail: ReadLogTailTool,
    getTaskLog: GetTaskLogTool,
    getOperation: GetOperationTool,
    createScript: CreateScriptTool,
    updateScript: UpdateScriptTool,
    runTask: RunTaskTool,
    stopTask: StopTaskTool,
    installDependency: InstallDependencyTool,
    reinstallDependency: ReinstallDependencyTool,
    createEnv: CreateEnvTool,
    updateEnv: UpdateEnvTool,
    enableEnv: EnableEnvTool,
    disableEnv: DisableEnvTool,
    createTask: CreateTaskTool,
    updateTask: UpdateTaskTool
) {
    private val tools = listOf(
        serverStatus,
        listTasks,
        listScripts,
        readScript,
        listDependencies,
        checkDependency,
        listEnvs,
        listLogs,
        readLogTail,
        getTaskLog,
        getOperation,
        createScript,
        updateScript,
        runTask,
        stopTask,
        installDependency,
        reinstallDependency,
        createEnv,
        updateEnv,
        enableEnv,
        disableEnv,
        createTask,
        updateTask
    )

    fun visibleTo(agent: McpAgent): List<AzureQlMcpTool> = tools.filter { tool ->
        agent.scopes.containsAll(tool.definition.requiredScopes) &&
            tool.definition.riskLevel != McpRiskLevel.HIGH_RISK
    }
}

class ServerStatusTool @Inject constructor(
    private val dashboardRepository: DashboardRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "server_status",
        description = "Read a bounded summary of the active QingLong server and task runtime.",
        requiredScopes = setOf(McpScope.STATUS_READ),
        riskLevel = McpRiskLevel.LOW_READ
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val overview = dashboardRepository.getOverview()
        val system = dashboardRepository.getSystem()
        val runtime = dashboardRepository.getRuntime()
        if (overview.isFailure && system.isFailure && runtime.isFailure) return qingLongUnavailable()
        return McpToolOutcome.Success(buildJsonObject {
            put("qinglong_reachable", true)
            overview.getOrNull()?.let { value ->
                putJsonObject("tasks") {
                    put("total", value.total)
                    put("enabled", value.enabled)
                    put("disabled", value.disabled)
                    put("today_runs", value.todayRuns)
                    put("today_success", value.todaySuccess)
                    put("today_fail", value.todayFail)
                    put("success_rate", value.successRate)
                }
            }
            system.getOrNull()?.let { value ->
                putJsonObject("system") {
                    put("platform", value.platform)
                    put("uptime_seconds", value.uptime)
                    put("cpu_count", value.cpus)
                    put("memory_usage_percent", value.memUsagePercent)
                }
            }
            runtime.getOrNull()?.let { value ->
                putJsonObject("runtime") {
                    put("running_count", value.runningCount)
                    put("queued_count", value.queuedCount)
                }
            }
        }, targetSummary = "active account status")
    }
}

class ListTasksTool @Inject constructor(
    private val taskRepository: TaskRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "list_tasks",
        description = "List QingLong task metadata. Commands and logs are intentionally omitted.",
        requiredScopes = setOf(McpScope.TASK_READ),
        riskLevel = McpRiskLevel.LOW_READ,
        inputProperties = buildJsonObject {
            stringProperty("search", "Optional task-name search text")
            integerProperty("page", "Page number, starting at 1")
            integerProperty("limit", "Page size from 1 to 100")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val search = arguments.string("search").take(MAX_SEARCH_LENGTH)
        val page = arguments.int("page", 1).coerceIn(1, MAX_PAGE)
        val limit = arguments.int("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIST_LIMIT)
        val (tasks, total) = taskRepository.getTasks(search, page, limit).getOrElse {
            return qingLongUnavailable()
        }
        return McpToolOutcome.Success(buildJsonObject {
            put("page", page)
            put("limit", limit)
            put("total", total)
            putJsonArray("items") {
                tasks.take(limit).forEach { task ->
                    add(buildJsonObject {
                        put("id", task.id)
                        put("name", task.name)
                        put("schedule", task.schedule)
                        put("status", task.statusText)
                        put("disabled", task.isDisabled == 1)
                        put("pinned", task.pinned)
                        put("labels", JsonArray(task.labels.orEmpty().map(::JsonPrimitive)))
                        put("last_running_time", task.lastRunningTime)
                        put("last_execution_time", task.lastExecutionTime)
                    })
                }
            }
        }, targetSummary = "page=$page limit=$limit")
    }
}

class ListScriptsTool @Inject constructor(
    private val scriptRepository: ScriptRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "list_scripts",
        description = "List script and directory metadata without reading file contents.",
        requiredScopes = setOf(McpScope.SCRIPT_READ),
        riskLevel = McpRiskLevel.LOW_READ,
        inputProperties = buildJsonObject {
            integerProperty("limit", "Maximum items from 1 to 100")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val limit = arguments.int("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIST_LIMIT)
        val roots = scriptRepository.getScripts().getOrElse { return qingLongUnavailable() }
        val flattened = flattenScripts(roots, limit)
        return McpToolOutcome.Success(buildJsonObject {
            put("limit", limit)
            put("returned", flattened.size)
            put("truncated", flattened.size >= limit)
            putJsonArray("items") {
                flattened.forEach { item ->
                    add(buildJsonObject {
                        put("path", item.path)
                        put("name", item.node.title)
                        put("directory", item.node.isDirectory)
                        put("size_bytes", item.node.size)
                        put("modified_time", item.node.mtime)
                    })
                }
            }
        }, targetSummary = "limit=$limit")
    }
}

class ReadScriptTool @Inject constructor(
    private val scriptRepository: ScriptRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "read_script",
        description = "Read a relative script path with a hard 64 KiB UTF-8 response limit.",
        requiredScopes = setOf(McpScope.SCRIPT_READ),
        riskLevel = McpRiskLevel.SENSITIVE_READ,
        inputProperties = buildJsonObject {
            stringProperty("path", "Relative script path; absolute paths and parent traversal are rejected")
            integerProperty("max_bytes", "Maximum UTF-8 content bytes from 1 to 65536")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val path = arguments.string("path")
        val parsed = parseScriptPath(path) ?: return McpToolOutcome.Failure(
            "INVALID_ARGUMENT",
            "path must be a relative script file path without '.' or '..' segments"
        )
        val maxBytes = arguments.int("max_bytes", MAX_CONTENT_BYTES).coerceIn(1, MAX_CONTENT_BYTES)
        val script = findScript(scriptRepository.getScripts().getOrElse { return qingLongUnavailable() }, parsed.normalized)
            ?: return McpToolOutcome.Failure("NOT_FOUND", "The requested script file was not found")
        if (script.isDirectory) return McpToolOutcome.Failure("INVALID_ARGUMENT", "path must identify a script file")
        val draft = scriptRepository.prepareDraft(script).getOrElse { return qingLongUnavailable() }
        return try {
            if (!draft.isUtf8Valid) {
                McpToolOutcome.Failure("UNSUPPORTED_ENCODING", "The script is not valid UTF-8 text")
            } else {
                val content = scriptRepository.readDraftText(draft, maxBytes.toLong())
                    .getOrElse { return qingLongUnavailable() }
                val prefix = utf8Prefix(content, maxBytes)
                McpToolOutcome.Success(buildJsonObject {
                    put("path", parsed.normalized)
                    put("content", prefix)
                    put("size_bytes", draft.sizeBytes)
                    put("returned_bytes", prefix.toByteArray(StandardCharsets.UTF_8).size)
                    put("truncated", draft.sizeBytes > maxBytes)
                    put("sha256", draft.originalSha256)
                }, targetSummary = parsed.normalized.take(160))
            }
        } finally {
            scriptRepository.discardDraft(draft)
        }
    }
}

class ListDependenciesTool @Inject constructor(
    private val dependencyRepository: DependencyRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "list_dependencies",
        description = "List dependency names, types and installation status without logs.",
        requiredScopes = setOf(McpScope.DEPENDENCY_READ),
        riskLevel = McpRiskLevel.LOW_READ,
        inputProperties = buildJsonObject {
            stringProperty("search", "Optional dependency-name search text")
            stringProperty("type", "Optional type: nodejs, python3, or linux")
            integerProperty("limit", "Maximum items from 1 to 100")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val search = arguments.string("search").take(MAX_SEARCH_LENGTH)
        val type = arguments.string("type").take(MAX_TYPE_LENGTH)
        val limit = arguments.int("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIST_LIMIT)
        val items = dependencyRepository.getDependencies(search, type)
            .getOrElse { return qingLongUnavailable() }
        return McpToolOutcome.Success(buildJsonObject {
            put("limit", limit)
            put("total", items.size)
            putJsonArray("items") {
                items.take(limit).forEach { dependency ->
                    add(buildJsonObject {
                        put("id", dependency.id)
                        put("name", dependency.name)
                        put("type", dependency.typeText)
                        put("status", dependency.statusText)
                        put("remark", dependency.remark)
                        put("updated_at", dependency.updatedAt)
                    })
                }
            }
        }, targetSummary = "limit=$limit")
    }
}

class ListEnvsTool @Inject constructor(
    private val envRepository: EnvRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "list_envs",
        description = "List environment-variable metadata. Secret values are never returned.",
        requiredScopes = setOf(McpScope.ENV_READ_METADATA),
        riskLevel = McpRiskLevel.SENSITIVE_READ,
        inputProperties = buildJsonObject {
            stringProperty("search", "Optional variable-name search text")
            integerProperty("limit", "Maximum items from 1 to 100")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val search = arguments.string("search").take(MAX_SEARCH_LENGTH)
        val limit = arguments.int("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIST_LIMIT)
        val items = envRepository.getEnvs(search).getOrElse { return qingLongUnavailable() }
        return McpToolOutcome.Success(buildJsonObject {
            put("limit", limit)
            put("total", items.size)
            put("values_included", false)
            putJsonArray("items") {
                items.take(limit).forEach { env ->
                    add(buildJsonObject {
                        put("id", env.id)
                        put("name", env.name)
                        put("remarks", env.remarks)
                        put("status", env.statusText)
                        put("pinned", env.pinned)
                        put("value_masked", true)
                        put("updated_at", env.updatedAt)
                    })
                }
            }
        }, targetSummary = "limit=$limit")
    }
}

private data class ScriptListItem(val path: String, val node: ScriptFile)
private data class ParsedScriptPath(val normalized: String)

private fun flattenScripts(roots: List<ScriptFile>, limit: Int): List<ScriptListItem> {
    val result = ArrayList<ScriptListItem>(limit)
    fun visit(node: ScriptFile, inheritedParent: String, depth: Int) {
        if (result.size >= limit || depth > MAX_TREE_DEPTH) return
        val title = node.title.orEmpty().trim('/')
        val fallback = listOf(inheritedParent, title).filter(String::isNotBlank).joinToString("/")
        val path = node.key?.trim()?.trimStart('/')?.takeIf(String::isNotBlank) ?: fallback
        if (path.isNotBlank()) result += ScriptListItem(path, node)
        node.children.orEmpty().forEach { child -> visit(child, path.takeIf { node.isDirectory }.orEmpty(), depth + 1) }
    }
    roots.forEach { visit(it, "", 0) }
    return result
}

private fun parseScriptPath(raw: String): ParsedScriptPath? {
    if (raw.isBlank() || raw.length > MAX_PATH_LENGTH || '\u0000' in raw || '\\' in raw || raw.startsWith('/')) {
        return null
    }
    val segments = raw.split('/').filter(String::isNotEmpty)
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
    return ParsedScriptPath(normalized = segments.joinToString("/"))
}

private fun findScript(roots: List<ScriptFile>, requestedPath: String): ScriptFile? {
    var visited = 0
    fun visit(node: ScriptFile, inheritedParent: String, depth: Int): ScriptFile? {
        if (visited++ >= MAX_SCRIPT_SCAN_ITEMS || depth > MAX_TREE_DEPTH) return null
        val title = node.title.orEmpty().trim('/')
        val fallback = listOf(inheritedParent, title).filter(String::isNotBlank).joinToString("/")
        val path = node.key?.trim()?.trimStart('/')?.takeIf(String::isNotBlank) ?: fallback
        if (path == requestedPath) return node
        return node.children.orEmpty().firstNotNullOfOrNull { child ->
            visit(child, path.takeIf { node.isDirectory }.orEmpty(), depth + 1)
        }
    }
    return roots.firstNotNullOfOrNull { visit(it, "", 0) }
}

private fun utf8Prefix(value: String, maxBytes: Int): String {
    var charIndex = 0
    var bytes = 0
    while (charIndex < value.length) {
        val codePoint = value.codePointAt(charIndex)
        val charCount = Character.charCount(codePoint)
        val encoded = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8).size
        if (bytes + encoded > maxBytes) break
        bytes += encoded
        charIndex += charCount
    }
    return value.substring(0, charIndex)
}

private fun qingLongUnavailable() = McpToolOutcome.Failure(
    code = "QINGLONG_UNAVAILABLE",
    message = "The active QingLong server could not complete this request"
)

private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.int(name: String, default: Int): Int = this[name]?.jsonPrimitive?.intOrNull ?: default

private fun kotlinx.serialization.json.JsonObjectBuilder.stringProperty(name: String, description: String) {
    putJsonObject(name) {
        put("type", "string")
        put("description", description)
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.integerProperty(name: String, description: String) {
    putJsonObject(name) {
        put("type", "integer")
        put("description", description)
    }
}

private const val DEFAULT_LIMIT = 50
private const val MAX_LIST_LIMIT = 100
private const val MAX_PAGE = 10_000
private const val MAX_SEARCH_LENGTH = 200
private const val MAX_TYPE_LENGTH = 32
private const val MAX_PATH_LENGTH = 1_024
private const val MAX_TREE_DEPTH = 64
private const val MAX_SCRIPT_SCAN_ITEMS = 10_000
private const val MAX_CONTENT_BYTES = 65_536
