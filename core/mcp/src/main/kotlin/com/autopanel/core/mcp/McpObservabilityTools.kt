package com.autopanel.core.mcp

import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.DependencyStatus
import com.autopanel.core.model.LogFile
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class CheckDependencyTool @Inject constructor(
    private val dependencyRepository: DependencyRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "check_dependency",
        description = "Check whether a named QingLong dependency exists and report its current status.",
        requiredScopes = setOf(McpScope.DEPENDENCY_READ),
        riskLevel = McpRiskLevel.LOW_READ,
        inputProperties = buildJsonObject {
            stringProperty("name", "Exact dependency name")
            stringProperty("type", "Optional type: nodejs, python3, or linux")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val name = arguments.string("name").trim()
        if (name.isEmpty() || name.length > MAX_SEARCH_LENGTH) {
            return McpToolOutcome.Failure("INVALID_ARGUMENT", "name must contain 1 to 200 characters")
        }
        val type = arguments.string("type").trim().take(MAX_TYPE_LENGTH)
        val matches = dependencyRepository.getDependencies(name, type)
            .getOrElse { return qingLongUnavailable() }
            .filter { dependency -> dependency.name.equals(name, ignoreCase = true) }
            .take(MAX_DEPENDENCY_MATCHES)
        return McpToolOutcome.Success(buildJsonObject {
            put("name", name)
            put("requested_type", type.ifEmpty { null })
            put("found", matches.isNotEmpty())
            put("installed", matches.any { it.status == DependencyStatus.INSTALLED })
            putJsonArray("matches") {
                matches.forEach { dependency ->
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
        }, targetSummary = "dependency=${name.take(AUDIT_TARGET_LIMIT)}")
    }
}

class ListLogsTool @Inject constructor(
    private val logRepository: LogRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "list_logs",
        description = "List accessible QingLong log-file metadata without reading log content.",
        requiredScopes = setOf(McpScope.LOG_READ),
        riskLevel = McpRiskLevel.SENSITIVE_READ,
        inputProperties = buildJsonObject {
            stringProperty("search", "Optional case-insensitive path search")
            integerProperty("limit", "Maximum files from 1 to 100")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val search = arguments.string("search").trim().take(MAX_SEARCH_LENGTH)
        val limit = arguments.int("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIST_LIMIT)
        val roots = logRepository.getLogFiles().getOrElse { return qingLongUnavailable() }
        val allMatches = flattenLogFiles(roots, MAX_LOG_SCAN_ITEMS)
            .asSequence()
            .filter { search.isEmpty() || it.path.contains(search, ignoreCase = true) }
            .toList()
        return McpToolOutcome.Success(buildJsonObject {
            put("limit", limit)
            put("total", allMatches.size)
            put("truncated", allMatches.size > limit)
            putJsonArray("items") {
                allMatches.take(limit).forEach { item ->
                    add(buildJsonObject {
                        put("path", item.path)
                        put("name", item.node.title)
                        put("size_bytes", item.node.size)
                        put("created_at", item.node.createTime)
                    })
                }
            }
        }, targetSummary = "limit=$limit")
    }
}

class ReadLogTailTool @Inject constructor(
    private val logRepository: LogRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "read_log_tail",
        description = "Read a bounded tail of an accessible QingLong log file.",
        requiredScopes = setOf(McpScope.LOG_READ),
        riskLevel = McpRiskLevel.SENSITIVE_READ,
        inputProperties = buildJsonObject {
            stringProperty("path", "Relative log path; absolute paths and parent traversal are rejected")
            integerProperty("lines", "Maximum trailing lines from 1 to 1000")
            integerProperty("max_bytes", "Maximum UTF-8 response bytes from 1 to 65536")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val requestedPath = parseRelativePath(arguments.string("path"))
            ?: return invalidLogPath()
        val lines = arguments.int("lines", DEFAULT_TAIL_LINES).coerceIn(1, MAX_TAIL_LINES)
        val maxBytes = arguments.int("max_bytes", MAX_CONTENT_BYTES).coerceIn(1, MAX_CONTENT_BYTES)
        val roots = logRepository.getLogFiles().getOrElse { return qingLongUnavailable() }
        val log = flattenLogFiles(roots, MAX_LOG_SCAN_ITEMS).firstOrNull { it.path == requestedPath }
            ?: return McpToolOutcome.Failure("NOT_FOUND", "The requested log file was not found")
        val filename = log.node.title?.takeIf(String::isNotBlank)
            ?: return McpToolOutcome.Failure("NOT_FOUND", "The requested log file was not found")
        val content = logRepository.getLogContent(filename, log.node.parent.orEmpty())
            .getOrElse { return qingLongUnavailable() }
        val tail = boundedTail(content, lines, maxBytes)
        return McpToolOutcome.Success(buildJsonObject {
            put("path", log.path)
            put("content", tail.content)
            put("requested_lines", lines)
            put("returned_lines", tail.returnedLines)
            put("returned_bytes", tail.returnedBytes)
            put("truncated", tail.truncated)
        }, targetSummary = log.path.take(AUDIT_TARGET_LIMIT))
    }
}

class GetTaskLogTool @Inject constructor(
    private val logRepository: LogRepository
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "get_task_log",
        description = "Read a bounded tail of the latest log for a QingLong task ID.",
        requiredScopes = setOf(McpScope.LOG_READ),
        riskLevel = McpRiskLevel.SENSITIVE_READ,
        inputProperties = buildJsonObject {
            integerProperty("task_id", "Positive QingLong task ID")
            integerProperty("lines", "Maximum trailing lines from 1 to 1000")
            integerProperty("max_bytes", "Maximum UTF-8 response bytes from 1 to 65536")
        }
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val taskId = arguments.int("task_id", 0)
        if (taskId <= 0) return McpToolOutcome.Failure("INVALID_ARGUMENT", "task_id must be positive")
        val lines = arguments.int("lines", DEFAULT_TAIL_LINES).coerceIn(1, MAX_TAIL_LINES)
        val maxBytes = arguments.int("max_bytes", MAX_CONTENT_BYTES).coerceIn(1, MAX_CONTENT_BYTES)
        val content = logRepository.getTaskLog(taskId).getOrElse { return qingLongUnavailable() }
        val tail = boundedTail(content, lines, maxBytes)
        return McpToolOutcome.Success(buildJsonObject {
            put("task_id", taskId)
            put("content", tail.content)
            put("requested_lines", lines)
            put("returned_lines", tail.returnedLines)
            put("returned_bytes", tail.returnedBytes)
            put("truncated", tail.truncated)
        }, targetSummary = "task_id=$taskId")
    }
}

private data class LogListItem(val path: String, val node: LogFile)
private data class TailResult(
    val content: String,
    val returnedLines: Int,
    val returnedBytes: Int,
    val truncated: Boolean
)

private fun flattenLogFiles(roots: List<LogFile>, limit: Int): List<LogListItem> {
    val result = ArrayList<LogListItem>(minOf(limit, MAX_LIST_LIMIT))
    fun visit(node: LogFile, inheritedParent: String, depth: Int) {
        if (result.size >= limit || depth > MAX_TREE_DEPTH) return
        val title = node.title.orEmpty().trim('/')
        val fallback = listOf(inheritedParent, title).filter(String::isNotBlank).joinToString("/")
        val path = node.key?.trim()?.trimStart('/')?.takeIf(String::isNotBlank) ?: fallback
        if (node.isDirectory) {
            node.children.orEmpty().forEach { child -> visit(child, path, depth + 1) }
        } else if (path.isNotBlank()) {
            result += LogListItem(path, node)
        }
    }
    roots.forEach { visit(it, "", 0) }
    return result
}

private fun parseRelativePath(raw: String): String? {
    if (raw.isBlank() || raw.length > MAX_PATH_LENGTH || '\u0000' in raw || '\\' in raw || raw.startsWith('/')) {
        return null
    }
    val segments = raw.split('/').filter(String::isNotEmpty)
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
    return segments.joinToString("/")
}

private fun boundedTail(source: String, maxLines: Int, maxBytes: Int): TailResult {
    var lineStart = source.length
    var newlines = 0
    while (lineStart > 0 && newlines < maxLines) {
        lineStart--
        if (source[lineStart] == '\n') {
            newlines++
            if (newlines == maxLines) {
                lineStart++
                break
            }
        }
    }

    var byteStart = source.length
    var bytes = 0
    while (byteStart > lineStart) {
        val codePoint = source.codePointBefore(byteStart)
        val charCount = Character.charCount(codePoint)
        val encoded = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8).size
        if (bytes + encoded > maxBytes) break
        bytes += encoded
        byteStart -= charCount
    }
    val content = source.substring(byteStart)
    return TailResult(
        content = content,
        returnedLines = when {
            content.isEmpty() -> 0
            else -> content.count { it == '\n' } + if (content.endsWith('\n')) 0 else 1
        },
        returnedBytes = bytes,
        truncated = byteStart > 0
    )
}

private fun invalidLogPath() = McpToolOutcome.Failure(
    "INVALID_ARGUMENT",
    "path must be a relative log file path without '.' or '..' segments"
)

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
private const val MAX_DEPENDENCY_MATCHES = 20
private const val MAX_LOG_SCAN_ITEMS = 10_000
private const val MAX_TREE_DEPTH = 64
private const val MAX_SEARCH_LENGTH = 200
private const val MAX_TYPE_LENGTH = 32
private const val MAX_PATH_LENGTH = 1_024
private const val DEFAULT_TAIL_LINES = 200
private const val MAX_TAIL_LINES = 1_000
private const val MAX_CONTENT_BYTES = 65_536
private const val AUDIT_TARGET_LIMIT = 160
