package com.autopanel.core.mcp

import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.domain.EnvRepository
import com.autopanel.core.domain.ScriptDraftUploadResult
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.DependencyType
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskScheduleType
import com.autopanel.core.model.toDraft
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class GetOperationTool @Inject constructor(
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = McpToolDefinition(
        name = "get_operation",
        description = "Read the status of an MCP operation owned by this Agent.",
        requiredScopes = setOf(McpScope.STATUS_READ),
        riskLevel = McpRiskLevel.LOW_READ,
        inputProperties = buildJsonObject {
            stringProperty("operation_id", "Operation identifier returned by a controlled tool")
        },
        requiredInput = listOf("operation_id")
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val operationId = arguments.text("operation_id")
        if (!operationId.startsWith("op_") || operationId.length > 64) {
            return invalid("operation_id is invalid")
        }
        val operation = operationManager.get(operationId, context.agent.id)
            ?: return McpToolOutcome.Failure("OPERATION_NOT_FOUND", "The operation was not found")
        return McpToolOutcome.Success(operationPayload(operation), operation.targetSummary)
    }
}

class CreateScriptTool @Inject constructor(
    private val repository: ScriptRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = controlledDefinition(
        name = "create_script",
        description = "Create a UTF-8 QingLong script after confirmation; maximum content size is 512 KiB.",
        scopes = setOf(McpScope.SCRIPT_WRITE),
        risk = McpRiskLevel.CONTROLLED_WRITE,
        properties = buildJsonObject {
            controlledProperties()
            stringProperty("path", "Relative script path")
            stringProperty("content", "UTF-8 script content, at most 512 KiB")
        },
        required = listOf("idempotency_key", "path", "content")
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val path = parseWritableScriptPath(arguments.text("path"))
            ?: return invalid("path must be a relative script file path without '.' or '..' segments")
        val content = arguments.text("content")
        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
        if (contentBytes.size > MAX_WRITE_SCRIPT_BYTES) return tooLargeScript()
        val existing = repository.getScripts().getOrElse { return unavailable() }
        if (findScriptByPath(existing, path.normalized) != null) {
            return McpToolOutcome.Failure("ALREADY_EXISTS", "A script already exists at this path")
        }
        return controlled(
            context = context,
            arguments = arguments,
            operationManager = operationManager,
            definition = definition,
            targetSummary = path.normalized
        ) { operationId ->
            val latest = repository.getScripts().getOrElse { return@controlled unavailable() }
            if (findScriptByPath(latest, path.normalized) != null) {
                return@controlled McpToolOutcome.Failure("ALREADY_EXISTS", "A script already exists at this path")
            }
            repository.addScript(path.filename, path.parent, content).fold(
                onSuccess = {
                    McpToolOutcome.Success(buildJsonObject {
                        put("ok", true)
                        put("operation_id", operationId)
                        put("path", path.normalized)
                        put("size_bytes", contentBytes.size)
                        put("sha256", sha256Bytes(contentBytes))
                    }, path.normalized)
                },
                onFailure = { unavailable() }
            )
        }
    }
}

class UpdateScriptTool @Inject constructor(
    private val repository: ScriptRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = controlledDefinition(
        name = "update_script",
        description = "Update a UTF-8 QingLong script after confirmation and SHA-256 conflict checking.",
        scopes = setOf(McpScope.SCRIPT_WRITE),
        risk = McpRiskLevel.CONTROLLED_WRITE,
        properties = buildJsonObject {
            controlledProperties()
            stringProperty("path", "Relative path of an existing script")
            stringProperty("content", "Replacement UTF-8 content, at most 512 KiB")
            stringProperty("expected_sha256", "Full SHA-256 returned by read_script")
        },
        required = listOf("idempotency_key", "path", "content", "expected_sha256")
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val path = parseWritableScriptPath(arguments.text("path"))
            ?: return invalid("path must be a relative script file path without '.' or '..' segments")
        val content = arguments.text("content")
        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
        if (contentBytes.size > MAX_WRITE_SCRIPT_BYTES) return tooLargeScript()
        val expectedSha256 = arguments.text("expected_sha256").lowercase()
        if (!SHA256_PATTERN.matches(expectedSha256)) return invalid("expected_sha256 must be a 64-character SHA-256")

        val initialScript = repository.getScripts().getOrElse { return unavailable() }
            .let { findScriptByPath(it, path.normalized) }
            ?: return McpToolOutcome.Failure("NOT_FOUND", "The requested script was not found")
        if (initialScript.isDirectory) return invalid("path must identify a script file")
        val initialDraft = repository.prepareDraft(initialScript).getOrElse { return unavailable() }
        try {
            if (!initialDraft.isUtf8Valid) {
                return McpToolOutcome.Failure("UNSUPPORTED_ENCODING", "The script is not valid UTF-8 text")
            }
            if (!initialDraft.originalSha256.equals(expectedSha256, ignoreCase = true)) return scriptConflict()
        } finally {
            repository.discardDraft(initialDraft)
        }

        return controlled(
            context = context,
            arguments = arguments,
            operationManager = operationManager,
            definition = definition,
            targetSummary = path.normalized
        ) { operationId ->
            val script = repository.getScripts().getOrElse { return@controlled unavailable() }
                .let { findScriptByPath(it, path.normalized) }
                ?: return@controlled McpToolOutcome.Failure("NOT_FOUND", "The requested script was not found")
            val draft = repository.prepareDraft(script).getOrElse { return@controlled unavailable() }
            try {
                if (!draft.isUtf8Valid) {
                    return@controlled McpToolOutcome.Failure("UNSUPPORTED_ENCODING", "The script is not valid UTF-8 text")
                }
                if (!draft.originalSha256.equals(expectedSha256, ignoreCase = true)) {
                    return@controlled scriptConflict(draft.originalSha256)
                }
                val updated = repository.replaceDraftText(draft, content, draft.hasUtf8Bom)
                    .getOrElse { return@controlled unavailable() }
                when (repository.uploadDraft(updated, force = false).getOrElse { return@controlled unavailable() }) {
                    ScriptDraftUploadResult.CONFLICT -> scriptConflict()
                    ScriptDraftUploadResult.SAVED -> McpToolOutcome.Success(buildJsonObject {
                        put("ok", true)
                        put("operation_id", operationId)
                        put("path", path.normalized)
                        put("size_bytes", contentBytes.size)
                        put("sha256", sha256Bytes(contentBytes))
                    }, path.normalized)
                }
            } finally {
                repository.discardDraft(draft)
            }
        }
    }
}

class RunTaskTool @Inject constructor(
    private val repository: TaskRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = taskExecutionDefinition("run_task", "Run one QingLong task after confirmation.")

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome =
        mutateTask(context, arguments, repository, operationManager, definition) { id -> repository.runTasks(listOf(id)) }
}

class StopTaskTool @Inject constructor(
    private val repository: TaskRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = taskExecutionDefinition("stop_task", "Stop one QingLong task after confirmation.")

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome =
        mutateTask(context, arguments, repository, operationManager, definition) { id -> repository.stopTasks(listOf(id)) }
}

class InstallDependencyTool @Inject constructor(
    private val repository: DependencyRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = controlledDefinition(
        name = "install_dependency",
        description = "Submit installation of one QingLong dependency after confirmation.",
        scopes = setOf(McpScope.DEPENDENCY_WRITE),
        risk = McpRiskLevel.EXECUTION,
        properties = buildJsonObject {
            controlledProperties()
            stringProperty("name", "Dependency package name")
            stringProperty("type", "nodejs, python3, or linux")
        },
        required = listOf("idempotency_key", "name", "type")
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val name = arguments.text("name").trim()
        if (name.isEmpty() || name.length > MAX_DEPENDENCY_NAME_LENGTH || name.any(Char::isISOControl)) {
            return invalid("name must be 1-$MAX_DEPENDENCY_NAME_LENGTH characters without control characters")
        }
        val type = arguments.text("type").lowercase()
        if (type !in DEPENDENCY_TYPES) return invalid("type must be nodejs, python3, or linux")
        val existing = repository.getDependencies(name, type).getOrElse { return unavailable() }
            .any { it.name.equals(name, ignoreCase = true) && it.typeText == type }
        if (existing) {
            return McpToolOutcome.Failure(
                "ALREADY_EXISTS",
                "The dependency already exists; use reinstall_dependency with its ID"
            )
        }
        return controlled(context, arguments, operationManager, definition, "dependency=$type:$name") { operationId ->
            val nowExists = repository.getDependencies(name, type).getOrElse { return@controlled unavailable() }
                .any { it.name.equals(name, ignoreCase = true) && it.typeText == type }
            if (nowExists) {
                return@controlled McpToolOutcome.Failure(
                    "ALREADY_EXISTS",
                    "The dependency already exists; use reinstall_dependency with its ID"
                )
            }
            repository.addDependency(name, type).fold(
                onSuccess = { dependencies ->
                    McpToolOutcome.Success(buildJsonObject {
                        put("ok", true)
                        put("operation_id", operationId)
                        put("name", name)
                        put("type", type)
                        put("submitted", true)
                        dependencies.firstOrNull()?.id?.let { put("dependency_id", it) }
                    }, "dependency=$type:$name")
                },
                onFailure = { unavailable() }
            )
        }
    }
}

class ReinstallDependencyTool @Inject constructor(
    private val repository: DependencyRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = controlledDefinition(
        name = "reinstall_dependency",
        description = "Reinstall one existing QingLong dependency after confirmation.",
        scopes = setOf(McpScope.DEPENDENCY_WRITE),
        risk = McpRiskLevel.EXECUTION,
        properties = buildJsonObject {
            controlledProperties()
            integerProperty("id", "Positive dependency ID")
        },
        required = listOf("idempotency_key", "id")
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val id = arguments.number("id")
        if (id <= 0) return invalid("id must be positive")
        val dependency = repository.getDependencies().getOrElse { return unavailable() }
            .firstOrNull { it.id == id }
            ?: return McpToolOutcome.Failure("NOT_FOUND", "The dependency was not found")
        val summary = "dependency=${dependency.typeText}:${dependency.name.orEmpty().take(120)}#$id"
        return controlled(context, arguments, operationManager, definition, summary) { operationId ->
            repository.reinstallDependencies(listOf(id)).fold(
                onSuccess = {
                    McpToolOutcome.Success(buildJsonObject {
                        put("ok", true)
                        put("operation_id", operationId)
                        put("dependency_id", id)
                        put("submitted", true)
                    }, summary)
                },
                onFailure = { unavailable() }
            )
        }
    }
}

class CreateEnvTool @Inject constructor(
    private val repository: EnvRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = envDefinition(
        name = "create_env",
        description = "Create one secret QingLong environment variable after confirmation.",
        update = false
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val values = validateEnv(arguments, requireId = false) ?: return invalidEnv()
        return controlled(context, arguments, operationManager, definition, "env=${values.name}") { operationId ->
            repository.addEnvs(listOf(Triple(values.name, values.value, values.remarks))).fold(
                onSuccess = { envs ->
                    McpToolOutcome.Success(buildJsonObject {
                        put("ok", true)
                        put("operation_id", operationId)
                        put("name", values.name)
                        put("value_stored", true)
                        put("value_included", false)
                        envs.firstOrNull()?.id?.let { put("env_id", it) }
                    }, "env=${values.name}")
                },
                onFailure = { unavailable() }
            )
        }
    }
}

class UpdateEnvTool @Inject constructor(
    private val repository: EnvRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = envDefinition(
        name = "update_env",
        description = "Replace one secret QingLong environment variable value after confirmation.",
        update = true
    )

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val values = validateEnv(arguments, requireId = true) ?: return invalidEnv()
        val id = arguments.number("id")
        val existing = repository.getEnvs().getOrElse { return unavailable() }.firstOrNull { it.id == id }
            ?: return McpToolOutcome.Failure("NOT_FOUND", "The environment variable was not found")
        val summary = "env=${existing.name.orEmpty().take(100)}#$id -> ${values.name.take(100)}"
        return controlled(context, arguments, operationManager, definition, summary) { operationId ->
            val stillExists = repository.getEnvs().getOrElse { return@controlled unavailable() }.any { it.id == id }
            if (!stillExists) return@controlled McpToolOutcome.Failure("NOT_FOUND", "The environment variable was not found")
            repository.updateEnv(id, values.name, values.value, values.remarks).fold(
                onSuccess = {
                    McpToolOutcome.Success(buildJsonObject {
                        put("ok", true)
                        put("operation_id", operationId)
                        put("env_id", id)
                        put("name", values.name)
                        put("value_stored", true)
                        put("value_included", false)
                    }, summary)
                },
                onFailure = { unavailable() }
            )
        }
    }
}

class EnableEnvTool @Inject constructor(
    private val repository: EnvRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = envStatusDefinition("enable_env", "Enable one QingLong environment variable after confirmation.")

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome =
        mutateEnvStatus(context, arguments, repository, operationManager, definition, true)
}

class DisableEnvTool @Inject constructor(
    private val repository: EnvRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = envStatusDefinition("disable_env", "Disable one QingLong environment variable after confirmation.")

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome =
        mutateEnvStatus(context, arguments, repository, operationManager, definition, false)
}

class CreateTaskTool @Inject constructor(
    private val repository: TaskRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = taskWriteDefinition("create_task", "Create a QingLong scheduled task after confirmation.", false)

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val draft = taskDraft(arguments, null) ?: return invalidTask()
        return controlled(context, arguments, operationManager, definition, "task=${draft.name.take(160)}") { operationId ->
            repository.addTask(draft).fold(
                onSuccess = { taskMutationSuccess(operationId, null, draft.name) },
                onFailure = { unavailable() }
            )
        }
    }
}

class UpdateTaskTool @Inject constructor(
    private val repository: TaskRepository,
    private val operationManager: McpOperationManager
) : AzureQlMcpTool {
    override val definition = taskWriteDefinition("update_task", "Update supported fields of one QingLong task after confirmation.", true)

    override suspend fun invoke(context: McpCallContext, arguments: JsonObject): McpToolOutcome {
        val id = arguments.number("id")
        if (id <= 0) return invalid("id must be positive")
        if (TASK_MUTABLE_FIELDS.none(arguments::containsKey)) {
            return invalid("At least one supported task field must be supplied")
        }
        val existing = findTask(repository, id).getOrElse { return unavailable() }
            ?: return McpToolOutcome.Failure("NOT_FOUND", "The task was not found")
        val draft = taskDraft(arguments, existing.toDraft()) ?: return invalidTask()
        return controlled(context, arguments, operationManager, definition, "task=${draft.name.take(140)}#$id") { operationId ->
            val latest = findTask(repository, id).getOrElse { return@controlled unavailable() }
                ?: return@controlled McpToolOutcome.Failure("NOT_FOUND", "The task was not found")
            val latestDraft = taskDraft(arguments, latest.toDraft()) ?: return@controlled invalidTask()
            repository.updateTask(latestDraft).fold(
                onSuccess = { taskMutationSuccess(operationId, id, latestDraft.name) },
                onFailure = { unavailable() }
            )
        }
    }
}

private suspend fun controlled(
    context: McpCallContext,
    arguments: JsonObject,
    operationManager: McpOperationManager,
    definition: McpToolDefinition,
    targetSummary: String,
    execute: suspend (operationId: String) -> McpToolOutcome
): McpToolOutcome {
    val decision = operationManager.requestExecution(
        context = context,
        tool = definition,
        arguments = arguments,
        idempotencyKey = arguments.text("idempotency_key"),
        operationId = arguments.optionalText("operation_id"),
        targetSummary = targetSummary
    )
    return when (decision) {
        is McpOperationDecision.Waiting -> McpToolOutcome.Success(
            operationPayload(decision.operation),
            decision.operation.targetSummary,
            auditOutcome = decision.operation.state.name
        )
        is McpOperationDecision.Rejected -> McpToolOutcome.Failure(
            decision.code,
            decision.message,
            decision.operation?.targetSummary ?: targetSummary
        )
        is McpOperationDecision.Replay -> decision.outcome
        is McpOperationDecision.Execute -> {
            val outcome = try {
                execute(decision.operation.id).withOperation(decision.operation.id)
            } catch (cancelled: CancellationException) {
                operationManager.complete(
                    decision.operation.id,
                    McpToolOutcome.Failure("OPERATION_CANCELLED", "The operation was cancelled")
                )
                throw cancelled
            } catch (_: Exception) {
                McpToolOutcome.Failure("INTERNAL_ERROR", "The operation could not complete", targetSummary)
            }
            val auditedOutcome = outcome.withTarget(targetSummary)
            operationManager.complete(decision.operation.id, auditedOutcome)
            auditedOutcome
        }
    }
}

private fun McpToolOutcome.withOperation(operationId: String): McpToolOutcome = when (this) {
    is McpToolOutcome.Success -> McpToolOutcome.Success(
        payload = JsonObject(payload + mapOf(
            "operation_id" to JsonPrimitive(operationId),
            "state" to JsonPrimitive(McpOperationState.SUCCEEDED.name.lowercase())
        )),
        targetSummary = targetSummary
    )
    is McpToolOutcome.Failure -> this
}

private fun McpToolOutcome.withTarget(targetSummary: String): McpToolOutcome = when (this) {
    is McpToolOutcome.Success -> this
    is McpToolOutcome.Failure -> copy(targetSummary = targetSummary)
}

private fun operationPayload(operation: McpOperation): JsonObject = buildJsonObject {
    put("operation_id", operation.id)
    put("tool", operation.tool)
    put("state", operation.state.name.lowercase())
    put("target", operation.targetSummary)
    put("created_at", operation.createdAtEpochMs)
    put("updated_at", operation.updatedAtEpochMs)
    put("expires_at", operation.expiresAtEpochMs)
    put("confirmation_required", operation.state == McpOperationState.WAITING_CONFIRMATION)
    put("next_action", when (operation.state) {
        McpOperationState.WAITING_CONFIRMATION -> "Ask the user to approve in AzureQL, then poll get_operation"
        McpOperationState.APPROVED -> "Retry the original tool with this operation_id and identical arguments"
        McpOperationState.RUNNING -> "Poll get_operation"
        else -> "none"
    })
    operation.outcomeCode?.let { put("outcome_code", it) }
    operation.outcomeMessage?.let { put("message", it) }
    operation.resultPayload?.let { payload ->
        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonObject }
            .getOrNull()?.let { put("result", it) }
    }
}

private suspend fun mutateTask(
    context: McpCallContext,
    arguments: JsonObject,
    repository: TaskRepository,
    operationManager: McpOperationManager,
    definition: McpToolDefinition,
    action: suspend (Int) -> Result<Unit>
): McpToolOutcome {
    val id = arguments.number("id")
    if (id <= 0) return invalid("id must be positive")
    val task = findTask(repository, id).getOrElse { return unavailable() }
        ?: return McpToolOutcome.Failure("NOT_FOUND", "The task was not found")
    val summary = "task=${task.name.orEmpty().take(140)}#$id"
    return controlled(context, arguments, operationManager, definition, summary) { operationId ->
        action(id).fold(
            onSuccess = { taskMutationSuccess(operationId, id, task.name.orEmpty()) },
            onFailure = { unavailable() }
        )
    }
}

private suspend fun mutateEnvStatus(
    context: McpCallContext,
    arguments: JsonObject,
    repository: EnvRepository,
    operationManager: McpOperationManager,
    definition: McpToolDefinition,
    enable: Boolean
): McpToolOutcome {
    val id = arguments.number("id")
    if (id <= 0) return invalid("id must be positive")
    val env = repository.getEnvs().getOrElse { return unavailable() }.firstOrNull { it.id == id }
        ?: return McpToolOutcome.Failure("NOT_FOUND", "The environment variable was not found")
    val summary = "env=${env.name.orEmpty().take(160)}#$id"
    return controlled(context, arguments, operationManager, definition, summary) { operationId ->
        val result = if (enable) repository.enableEnvs(listOf(id)) else repository.disableEnvs(listOf(id))
        result.fold(
            onSuccess = {
                McpToolOutcome.Success(buildJsonObject {
                    put("ok", true)
                    put("operation_id", operationId)
                    put("env_id", id)
                    put("enabled", enable)
                }, summary)
            },
            onFailure = { unavailable() }
        )
    }
}

private suspend fun findTask(repository: TaskRepository, id: Int): Result<TaskInfo?> {
    for (page in 1..MAX_TASK_SCAN_PAGES) {
        val result = repository.getTasks(page = page, size = MAX_TASK_PAGE_SIZE)
        if (result.isFailure) return Result.failure(requireNotNull(result.exceptionOrNull()))
        val (items, total) = result.getOrThrow()
        items.firstOrNull { it.id == id }?.let { return Result.success(it) }
        if (items.isEmpty() || page * MAX_TASK_PAGE_SIZE >= total) return Result.success(null)
    }
    return Result.success(null)
}

private data class WritableScriptPath(val normalized: String, val filename: String, val parent: String)

private fun parseWritableScriptPath(raw: String): WritableScriptPath? {
    if (raw.isBlank() || raw.length > MAX_WRITE_PATH_LENGTH || '\u0000' in raw || '\\' in raw || raw.startsWith('/')) {
        return null
    }
    val segments = raw.split('/').filter(String::isNotEmpty)
    if (segments.isEmpty() || segments.any { it == "." || it == ".." || it.length > MAX_PATH_SEGMENT_LENGTH }) return null
    val filename = segments.last()
    if (filename.isBlank()) return null
    return WritableScriptPath(segments.joinToString("/"), filename, segments.dropLast(1).joinToString("/"))
}

private fun findScriptByPath(roots: List<ScriptFile>, requestedPath: String): ScriptFile? {
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

private data class EnvValues(val name: String, val value: String, val remarks: String?)

private fun validateEnv(arguments: JsonObject, requireId: Boolean): EnvValues? {
    if (requireId && arguments.number("id") <= 0) return null
    val name = arguments.text("name").trim()
    val value = arguments.text("value")
    val remarks = arguments.optionalText("remarks")
    if (name.isEmpty() || name.length > MAX_ENV_NAME_LENGTH || name.any(Char::isISOControl)) return null
    if (value.toByteArray(StandardCharsets.UTF_8).size > MAX_ENV_VALUE_BYTES || '\u0000' in value) return null
    if (remarks != null && (remarks.length > MAX_REMARKS_LENGTH || remarks.any { it == '\u0000' })) return null
    return EnvValues(name, value, remarks)
}

private fun taskDraft(arguments: JsonObject, base: TaskDraft?): TaskDraft? {
    val name = arguments.updatedText("name", base?.name).orEmpty().trim()
    val command = arguments.updatedText("command", base?.command).orEmpty().trim()
    val typeText = arguments.updatedText("schedule_type", base?.scheduleType?.name?.lowercase() ?: "normal")
        .orEmpty().lowercase()
    val scheduleType = when (typeText) {
        "normal" -> TaskScheduleType.NORMAL
        "once" -> TaskScheduleType.ONCE
        "boot" -> TaskScheduleType.BOOT
        else -> return null
    }
    val schedule = arguments.updatedText("schedule", base?.schedule).orEmpty().trim()
    val labels = arguments.updatedStrings("labels", base?.labels ?: emptyList()) ?: return null
    val extraSchedules = arguments.updatedStrings("extra_schedules", base?.extraSchedules ?: emptyList()) ?: return null
    val taskBefore = arguments.updatedText("task_before", base?.taskBefore).orEmpty()
    val taskAfter = arguments.updatedText("task_after", base?.taskAfter).orEmpty()
    val logName = arguments.updatedText("log_name", base?.logName).orEmpty()
    val workDir = arguments.updatedText("work_dir", base?.workDir).orEmpty()
    val multiple = arguments.updatedBoolean("allow_multiple_instances", base?.allowMultipleInstances ?: false)

    if (name.isEmpty() || name.length > MAX_TASK_NAME_LENGTH || name.any(Char::isISOControl)) return null
    if (command.isEmpty() || command.length > MAX_TASK_COMMAND_LENGTH || '\u0000' in command) return null
    if (scheduleType == TaskScheduleType.NORMAL && (schedule.isEmpty() || schedule.length > MAX_SCHEDULE_LENGTH)) return null
    if (labels.size > MAX_TASK_LABELS || labels.any { it.isBlank() || it.length > MAX_TASK_LABEL_LENGTH }) return null
    if (extraSchedules.size > MAX_EXTRA_SCHEDULES || extraSchedules.any { it.isBlank() || it.length > MAX_SCHEDULE_LENGTH }) return null
    if (taskBefore.length > MAX_TASK_HOOK_LENGTH || taskAfter.length > MAX_TASK_HOOK_LENGTH) return null
    if (TASK_COMMAND_PATTERN.containsMatchIn(taskBefore) || TASK_COMMAND_PATTERN.containsMatchIn(taskAfter)) return null
    if (logName.length > MAX_TASK_PATH_FIELD_LENGTH || workDir.length > MAX_TASK_PATH_FIELD_LENGTH) return null

    return TaskDraft(
        id = base?.id,
        name = name,
        command = command,
        scheduleType = scheduleType,
        schedule = schedule,
        extraSchedules = extraSchedules,
        labels = labels,
        allowMultipleInstances = multiple,
        logName = logName,
        workDir = workDir,
        taskBefore = taskBefore,
        taskAfter = taskAfter
    )
}

private fun controlledDefinition(
    name: String,
    description: String,
    scopes: Set<McpScope>,
    risk: McpRiskLevel,
    properties: JsonObject,
    required: List<String>
) = McpToolDefinition(name, description, scopes, risk, properties, required)

private fun taskExecutionDefinition(name: String, description: String) = controlledDefinition(
    name,
    description,
    setOf(McpScope.TASK_EXECUTE),
    McpRiskLevel.EXECUTION,
    buildJsonObject {
        controlledProperties()
        integerProperty("id", "Positive QingLong task ID")
    },
    listOf("idempotency_key", "id")
)

private fun envDefinition(name: String, description: String, update: Boolean) = controlledDefinition(
    name,
    description,
    setOf(McpScope.ENV_WRITE),
    McpRiskLevel.CONTROLLED_WRITE,
    buildJsonObject {
        controlledProperties()
        if (update) integerProperty("id", "Positive environment-variable ID")
        stringProperty("name", "Environment-variable name")
        stringProperty("value", "Secret value; never echoed in tool results or audit")
        stringProperty("remarks", "Optional remarks")
    },
    buildList {
        add("idempotency_key")
        if (update) add("id")
        add("name")
        add("value")
    }
)

private fun envStatusDefinition(name: String, description: String) = controlledDefinition(
    name,
    description,
    setOf(McpScope.ENV_WRITE),
    McpRiskLevel.CONTROLLED_WRITE,
    buildJsonObject {
        controlledProperties()
        integerProperty("id", "Positive environment-variable ID")
    },
    listOf("idempotency_key", "id")
)

private fun taskWriteDefinition(name: String, description: String, update: Boolean) = controlledDefinition(
    name,
    description,
    setOf(McpScope.TASK_WRITE),
    McpRiskLevel.CONTROLLED_WRITE,
    buildJsonObject {
        controlledProperties()
        if (update) integerProperty("id", "Positive task ID")
        stringProperty("name", "Task name")
        stringProperty("command", "Task command")
        stringProperty("schedule_type", "normal, once, or boot")
        stringProperty("schedule", "Cron schedule for normal tasks")
        stringArrayProperty("extra_schedules", "Additional cron schedules")
        stringArrayProperty("labels", "Task labels")
        booleanProperty("allow_multiple_instances", "Whether concurrent instances are allowed")
        stringProperty("log_name", "Optional log name")
        stringProperty("work_dir", "Optional working directory")
        stringProperty("task_before", "Optional command before task; cannot contain task command")
        stringProperty("task_after", "Optional command after task; cannot contain task command")
    },
    if (update) listOf("idempotency_key", "id") else
        listOf("idempotency_key", "name", "command", "schedule_type")
)

private fun kotlinx.serialization.json.JsonObjectBuilder.controlledProperties() {
    stringProperty("idempotency_key", "Stable unique key for exactly-once execution")
    stringProperty("operation_id", "Echo after the user approves the pending operation")
}

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

private fun kotlinx.serialization.json.JsonObjectBuilder.booleanProperty(name: String, description: String) {
    putJsonObject(name) {
        put("type", "boolean")
        put("description", description)
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.stringArrayProperty(name: String, description: String) {
    putJsonObject(name) {
        put("type", "array")
        put("description", description)
        putJsonObject("items") { put("type", "string") }
    }
}

private fun JsonObject.text(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.optionalText(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.number(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.updatedText(name: String, fallback: String?): String? =
    if (containsKey(name)) optionalText(name) else fallback
private fun JsonObject.updatedBoolean(name: String, fallback: Boolean): Boolean =
    if (containsKey(name)) this[name]?.jsonPrimitive?.booleanOrNull ?: fallback else fallback
private fun JsonObject.updatedStrings(name: String, fallback: List<String>): List<String>? {
    if (!containsKey(name)) return fallback
    val array = this[name] as? JsonArray ?: return null
    return array.map { element -> element.jsonPrimitive.contentOrNull ?: return null }
}

private fun invalid(message: String) = McpToolOutcome.Failure("INVALID_ARGUMENT", message)
private fun unavailable() = McpToolOutcome.Failure(
    "QINGLONG_UNAVAILABLE",
    "The active QingLong server could not complete this request"
)
private fun tooLargeScript() = McpToolOutcome.Failure(
    "RESULT_TOO_LARGE",
    "Script content exceeds the 512 KiB write limit"
)
private fun scriptConflict(currentSha256: String? = null) = McpToolOutcome.Failure(
    "SCRIPT_CONFLICT",
    if (currentSha256 == null) {
        "The script changed after it was read. Read the latest version and retry with a new idempotency key."
    } else {
        "The script changed after it was read (current_sha256=$currentSha256). Read it again and retry with a new idempotency key."
    }
)
private fun invalidEnv() = invalid(
    "Environment input is invalid; id must be positive, name must be non-empty, and value must be at most 64 KiB"
)
private fun invalidTask() = invalid(
    "Task fields are invalid; normal tasks require a schedule and task_before/task_after cannot contain the task command"
)
private fun taskMutationSuccess(operationId: String, id: Int?, name: String) = McpToolOutcome.Success(
    buildJsonObject {
        put("ok", true)
        put("operation_id", operationId)
        id?.let { put("task_id", it) }
        put("name", name)
        put("submitted", true)
    },
    "task=${name.take(140)}${id?.let { "#$it" }.orEmpty()}"
)

private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte) }

private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
private val TASK_COMMAND_PATTERN = Regex("(^|[\\s;&|])task([\\s;&|]|$)", RegexOption.IGNORE_CASE)
private val DEPENDENCY_TYPES = setOf(DependencyType.NODEJS, DependencyType.PYTHON, DependencyType.LINUX)
private val TASK_MUTABLE_FIELDS = setOf(
    "name",
    "command",
    "schedule_type",
    "schedule",
    "extra_schedules",
    "labels",
    "allow_multiple_instances",
    "log_name",
    "work_dir",
    "task_before",
    "task_after"
)
private const val MAX_WRITE_SCRIPT_BYTES = 512 * 1024
private const val MAX_WRITE_PATH_LENGTH = 1_024
private const val MAX_PATH_SEGMENT_LENGTH = 255
private const val MAX_SCRIPT_SCAN_ITEMS = 10_000
private const val MAX_TREE_DEPTH = 64
private const val MAX_TASK_SCAN_PAGES = 100
private const val MAX_TASK_PAGE_SIZE = 100
private const val MAX_DEPENDENCY_NAME_LENGTH = 300
private const val MAX_ENV_NAME_LENGTH = 256
private const val MAX_ENV_VALUE_BYTES = 64 * 1024
private const val MAX_REMARKS_LENGTH = 1_000
private const val MAX_TASK_NAME_LENGTH = 200
private const val MAX_TASK_COMMAND_LENGTH = 8_192
private const val MAX_SCHEDULE_LENGTH = 300
private const val MAX_TASK_LABELS = 50
private const val MAX_TASK_LABEL_LENGTH = 100
private const val MAX_EXTRA_SCHEDULES = 20
private const val MAX_TASK_HOOK_LENGTH = 4_096
private const val MAX_TASK_PATH_FIELD_LENGTH = 1_024
