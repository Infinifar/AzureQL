package com.autopanel.core.mcp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
enum class McpOperationState {
    WAITING_CONFIRMATION,
    APPROVED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    DENIED,
    EXPIRED
}

@Serializable
data class McpOperation(
    val id: String,
    val agentId: String,
    val agentName: String,
    val accountId: String,
    val tool: String,
    val risk: String,
    val state: McpOperationState,
    val targetSummary: String,
    val idempotencyKeyHash: String,
    val requestHash: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val outcomeCode: String? = null,
    val outcomeMessage: String? = null,
    val resultPayload: String? = null
)

sealed interface McpOperationDecision {
    data class Waiting(val operation: McpOperation) : McpOperationDecision
    data class Execute(val operation: McpOperation) : McpOperationDecision
    data class Replay(val operation: McpOperation, val outcome: McpToolOutcome) : McpOperationDecision
    data class Rejected(
        val code: String,
        val message: String,
        val operation: McpOperation? = null
    ) : McpOperationDecision
}

interface McpOperationManager {
    val operations: StateFlow<List<McpOperation>>

    suspend fun requestExecution(
        context: McpCallContext,
        tool: McpToolDefinition,
        arguments: JsonObject,
        idempotencyKey: String,
        operationId: String?,
        targetSummary: String
    ): McpOperationDecision

    suspend fun approve(operationId: String): Result<Unit>
    suspend fun deny(operationId: String): Result<Unit>
    suspend fun get(operationId: String, agentId: McpAgentId): McpOperation?
    suspend fun complete(operationId: String, outcome: McpToolOutcome)
}

internal interface McpOperationStorage {
    fun load(): List<McpOperation>
    fun save(operations: List<McpOperation>): Boolean
}

private class SharedPreferencesMcpOperationStorage(context: Context) : McpOperationStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<McpOperation> = preferences.getString(KEY_OPERATIONS, null)
        ?.let { runCatching { json.decodeFromString<List<McpOperation>>(it) }.getOrNull() }
        .orEmpty()

    override fun save(operations: List<McpOperation>): Boolean =
        preferences.edit().putString(KEY_OPERATIONS, json.encodeToString(operations)).commit()

    private companion object {
        const val PREFERENCES_NAME = "azureql_mcp_operations"
        const val KEY_OPERATIONS = "operations_v1"
    }
}

@Singleton
class PersistentMcpOperationManager internal constructor(
    private val storage: McpOperationStorage,
    private val auditLogger: McpAuditLogger = NoOpMcpAuditLogger,
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) : McpOperationManager {
    @Inject constructor(
        @ApplicationContext context: Context,
        auditLogger: McpAuditLogger
    ) : this(SharedPreferencesMcpOperationStorage(context), auditLogger)

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val loadedRecords = storage.load().takeLast(MAX_OPERATIONS)
    private var records = recoverInterrupted(loadedRecords)
    private val mutableOperations = MutableStateFlow(records.publicSnapshot())
    override val operations: StateFlow<List<McpOperation>> = mutableOperations.asStateFlow()

    init {
        if (records != loadedRecords) {
            storage.save(records)
        }
    }

    override suspend fun requestExecution(
        context: McpCallContext,
        tool: McpToolDefinition,
        arguments: JsonObject,
        idempotencyKey: String,
        operationId: String?,
        targetSummary: String
    ): McpOperationDecision = withContext(Dispatchers.IO) {
        val normalizedKey = validateIdempotencyKey(idempotencyKey)
            ?: return@withContext McpOperationDecision.Rejected(
                "INVALID_ARGUMENT",
                "idempotency_key must be 8-128 characters using letters, digits, '.', '_', ':' or '-'"
            )
        val keyHash = sha256("${context.agent.id.value}:$normalizedKey")
        val requestHash = requestHash(tool.definitionName(), arguments)
        mutex.withLock {
            val now = nowEpochMs()
            pruneAndExpireLocked(now)
            val existingIndex = records.indexOfFirst {
                it.agentId == context.agent.id.value && it.idempotencyKeyHash == keyHash
            }
            if (existingIndex < 0) {
                if (operationId != null) {
                    return@withLock McpOperationDecision.Rejected(
                        "OPERATION_NOT_FOUND",
                        "The supplied operation_id does not match this idempotency key"
                    )
                }
                val operation = McpOperation(
                    id = "op_${UUID.randomUUID()}",
                    agentId = context.agent.id.value,
                    agentName = context.agent.name,
                    accountId = context.accountId,
                    tool = tool.name,
                    risk = tool.riskLevel.name,
                    state = McpOperationState.WAITING_CONFIRMATION,
                    targetSummary = targetSummary.take(MAX_TARGET_SUMMARY_LENGTH),
                    idempotencyKeyHash = keyHash,
                    requestHash = requestHash,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    expiresAtEpochMs = now + CONFIRMATION_TTL_MS
                )
                records = (records + operation).takeLast(MAX_OPERATIONS)
                persistLocked()
                return@withLock McpOperationDecision.Waiting(operation)
            }

            val existing = records[existingIndex]
            if (existing.requestHash != requestHash || existing.tool != tool.name || existing.accountId != context.accountId) {
                return@withLock McpOperationDecision.Rejected(
                    "IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used for a different request",
                    existing
                )
            }
            if (operationId == null) {
                return@withLock McpOperationDecision.Waiting(existing)
            }
            if (operationId != existing.id) {
                return@withLock McpOperationDecision.Rejected(
                    "OPERATION_NOT_FOUND",
                    "The supplied operation_id does not match this idempotency key",
                    existing
                )
            }
            when (existing.state) {
                McpOperationState.WAITING_CONFIRMATION -> McpOperationDecision.Waiting(existing)
                McpOperationState.APPROVED -> {
                    val anotherWriteRunning = records.any {
                        it.agentId == existing.agentId && it.id != existing.id && it.state == McpOperationState.RUNNING
                    }
                    if (anotherWriteRunning) {
                        McpOperationDecision.Rejected(
                            "OPERATION_IN_PROGRESS",
                            "This Agent already has a write operation in progress",
                            existing
                        )
                    } else {
                        val running = existing.copy(
                            state = McpOperationState.RUNNING,
                            updatedAtEpochMs = now
                        )
                        replaceLocked(existingIndex, running)
                        McpOperationDecision.Execute(running)
                    }
                }
                McpOperationState.RUNNING -> McpOperationDecision.Rejected(
                    "OPERATION_IN_PROGRESS",
                    "The operation is already running",
                    existing
                )
                McpOperationState.SUCCEEDED -> McpOperationDecision.Replay(
                    existing,
                    McpToolOutcome.Success(
                        payload = existing.resultPayload?.let(::decodePayload) ?: JsonObject(emptyMap()),
                        targetSummary = existing.targetSummary
                    )
                )
                McpOperationState.FAILED -> McpOperationDecision.Replay(
                    existing,
                    McpToolOutcome.Failure(
                        existing.outcomeCode ?: "QINGLONG_UNAVAILABLE",
                        existing.outcomeMessage ?: "The operation failed"
                    )
                )
                McpOperationState.DENIED -> McpOperationDecision.Rejected(
                    "CONFIRMATION_DENIED",
                    "The user denied this operation",
                    existing
                )
                McpOperationState.EXPIRED -> McpOperationDecision.Rejected(
                    "CONFIRMATION_EXPIRED",
                    "The confirmation expired; retry with a new idempotency key",
                    existing
                )
            }
        }
    }

    override suspend fun approve(operationId: String): Result<Unit> = confirm(
        operationId,
        setOf(McpOperationState.WAITING_CONFIRMATION),
        McpOperationState.APPROVED,
        "USER_APPROVED"
    )

    override suspend fun deny(operationId: String): Result<Unit> = confirm(
        operationId,
        setOf(McpOperationState.WAITING_CONFIRMATION, McpOperationState.APPROVED),
        McpOperationState.DENIED,
        "USER_DENIED"
    )

    override suspend fun get(operationId: String, agentId: McpAgentId): McpOperation? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                pruneAndExpireLocked(nowEpochMs())
                records.firstOrNull { it.id == operationId && it.agentId == agentId.value }
            }
        }

    override suspend fun complete(operationId: String, outcome: McpToolOutcome) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val index = records.indexOfFirst { it.id == operationId }
            if (index < 0) return@withLock
            val current = records[index]
            if (current.state != McpOperationState.RUNNING) return@withLock
            val now = nowEpochMs()
            val completed = when (outcome) {
                is McpToolOutcome.Success -> current.copy(
                    state = McpOperationState.SUCCEEDED,
                    updatedAtEpochMs = now,
                    outcomeCode = "SUCCESS",
                    resultPayload = outcome.payload.toString()
                )
                is McpToolOutcome.Failure -> current.copy(
                    state = McpOperationState.FAILED,
                    updatedAtEpochMs = now,
                    outcomeCode = outcome.code,
                    outcomeMessage = outcome.message.take(MAX_OUTCOME_MESSAGE_LENGTH)
                )
            }
            replaceLocked(index, completed)
        }
    }

    private suspend fun confirm(
        operationId: String,
        allowedStates: Set<McpOperationState>,
        nextState: McpOperationState,
        auditOutcome: String
    ): Result<Unit> {
        val result = updateConfirmation(operationId, allowedStates, nextState)
        val operation = result.getOrNull() ?: return result.map { }
        try {
            auditLogger.record(
                McpAuditEvent(
                    timestampEpochMs = nowEpochMs(),
                    requestId = operation.id,
                    agentId = operation.agentId,
                    agentName = operation.agentName,
                    tool = operation.tool,
                    risk = operation.risk,
                    outcome = auditOutcome,
                    targetSummary = operation.targetSummary
                )
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Confirmation state is already durable; audit persistence failure must not invite a duplicate tap.
        }
        return Result.success(Unit)
    }

    private suspend fun updateConfirmation(
        operationId: String,
        allowedStates: Set<McpOperationState>,
        nextState: McpOperationState
    ): Result<McpOperation> = withContext(Dispatchers.IO) {
        try {
            val updated = mutex.withLock {
                val now = nowEpochMs()
                pruneAndExpireLocked(now)
                val index = records.indexOfFirst { it.id == operationId }
                require(index >= 0) { "MCP operation was not found" }
                val current = records[index]
                require(current.state in allowedStates) { "MCP operation can no longer be changed" }
                require(current.expiresAtEpochMs > now) { "MCP confirmation has expired" }
                current.copy(state = nextState, updatedAtEpochMs = now).also { replaceLocked(index, it) }
            }
            Result.success(updated)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun pruneAndExpireLocked(now: Long) {
        val updated = records.map { operation ->
            if (
                operation.state in setOf(McpOperationState.WAITING_CONFIRMATION, McpOperationState.APPROVED) &&
                operation.expiresAtEpochMs <= now
            ) {
                operation.copy(
                    state = McpOperationState.EXPIRED,
                    updatedAtEpochMs = now,
                    outcomeCode = "CONFIRMATION_EXPIRED"
                )
            } else {
                operation
            }
        }.filter { now - it.updatedAtEpochMs <= RETENTION_MS }.takeLast(MAX_OPERATIONS)
        if (updated != records) {
            records = updated
            persistLocked()
        }
    }

    private fun replaceLocked(index: Int, operation: McpOperation) {
        records = records.toMutableList().also { it[index] = operation }
        persistLocked()
    }

    private fun recoverInterrupted(loaded: List<McpOperation>): List<McpOperation> {
        val now = nowEpochMs()
        return loaded.map { operation ->
            if (operation.state == McpOperationState.RUNNING) {
                operation.copy(
                    state = McpOperationState.FAILED,
                    updatedAtEpochMs = now,
                    outcomeCode = PROCESS_INTERRUPTED,
                    outcomeMessage = "The app stopped before the operation result was recorded"
                )
            } else {
                operation
            }
        }
    }

    private fun persistLocked() {
        check(storage.save(records)) { "Unable to persist MCP operation state" }
        mutableOperations.value = records.publicSnapshot()
    }

    private fun List<McpOperation>.publicSnapshot(): List<McpOperation> =
        sortedByDescending(McpOperation::updatedAtEpochMs)

    private fun decodePayload(value: String): JsonObject =
        runCatching { json.decodeFromString<JsonObject>(value) }.getOrDefault(JsonObject(emptyMap()))

    private companion object {
        const val MAX_OPERATIONS = 200
        const val MAX_TARGET_SUMMARY_LENGTH = 200
        const val MAX_OUTCOME_MESSAGE_LENGTH = 300
        const val CONFIRMATION_TTL_MS = 10L * 60 * 1000
        const val RETENTION_MS = 24L * 60 * 60 * 1000
        const val PROCESS_INTERRUPTED = "PROCESS_INTERRUPTED"
    }
}

private object NoOpMcpAuditLogger : McpAuditLogger {
    override suspend fun record(event: McpAuditEvent) = Unit
}

internal fun requestHash(toolName: String, arguments: JsonObject): String {
    val businessArguments = JsonObject(arguments.filterKeys { it != "operation_id" })
    return sha256("$toolName\n${businessArguments.canonicalJson()}")
}

private fun McpToolDefinition.definitionName(): String = name

private fun JsonElement.canonicalJson(): String = when (this) {
    is JsonObject -> entries.sortedBy(Map.Entry<String, JsonElement>::key)
        .joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${JsonPrimitive(key)}:${value.canonicalJson()}"
        }
    is JsonArray -> joinToString(prefix = "[", postfix = "]") { it.canonicalJson() }
    else -> toString()
}

private fun validateIdempotencyKey(value: String): String? = value.trim().takeIf {
    it.length in 8..128 && it.all { character ->
        character.isLetterOrDigit() || character == '.' || character == '_' || character == ':' || character == '-'
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
