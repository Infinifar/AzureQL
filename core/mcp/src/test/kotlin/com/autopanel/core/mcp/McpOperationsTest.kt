package com.autopanel.core.mcp

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpOperationsTest {
    @Test
    fun `write waits for approval then executes once and replays result`() = runBlocking {
        val storage = MemoryOperationStorage()
        val manager = PersistentMcpOperationManager(storage)
        val arguments = writeArguments("first")

        val waiting = manager.requestExecution(
            context = writeContext(),
            tool = WRITE_TOOL,
            arguments = arguments,
            idempotencyKey = "request-0001",
            operationId = null,
            targetSummary = "scripts/test.py"
        ) as McpOperationDecision.Waiting
        assertEquals(McpOperationState.WAITING_CONFIRMATION, waiting.operation.state)
        assertFalse(storage.operations.single().idempotencyKeyHash.contains("request-0001"))

        manager.approve(waiting.operation.id).getOrThrow()
        val execute = manager.requestExecution(
            context = writeContext(),
            tool = WRITE_TOOL,
            arguments = arguments,
            idempotencyKey = "request-0001",
            operationId = waiting.operation.id,
            targetSummary = "scripts/test.py"
        ) as McpOperationDecision.Execute
        assertEquals(McpOperationState.RUNNING, execute.operation.state)

        val success = McpToolOutcome.Success(buildJsonObject { put("saved", true) })
        manager.complete(execute.operation.id, success)
        val replay = manager.requestExecution(
            context = writeContext(),
            tool = WRITE_TOOL,
            arguments = arguments,
            idempotencyKey = "request-0001",
            operationId = waiting.operation.id,
            targetSummary = "scripts/test.py"
        ) as McpOperationDecision.Replay
        assertTrue(replay.outcome is McpToolOutcome.Success)
        assertEquals(McpOperationState.SUCCEEDED, manager.operations.first().single().state)
    }

    @Test
    fun `same idempotency key rejects changed arguments and wrong operation id`() = runBlocking {
        val manager = PersistentMcpOperationManager(MemoryOperationStorage())
        val waiting = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("first"), "request-0002", null, "target"
        ) as McpOperationDecision.Waiting

        val changed = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("changed"), "request-0002", waiting.operation.id, "target"
        ) as McpOperationDecision.Rejected
        assertEquals("IDEMPOTENCY_CONFLICT", changed.code)

        val wrongId = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("first"), "request-0002", "op_wrong", "target"
        ) as McpOperationDecision.Rejected
        assertEquals("OPERATION_NOT_FOUND", wrongId.code)
    }

    @Test
    fun `operations are private to the owning agent and running work recovers as failed`() = runBlocking {
        val storage = MemoryOperationStorage()
        val manager = PersistentMcpOperationManager(storage)
        val waiting = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("first"), "request-0003", null, "target"
        ) as McpOperationDecision.Waiting
        assertNull(manager.get(waiting.operation.id, McpAgentId("another-agent")))

        manager.approve(waiting.operation.id).getOrThrow()
        manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("first"), "request-0003", waiting.operation.id, "target"
        ) as McpOperationDecision.Execute
        val recovered = PersistentMcpOperationManager(storage)
        val operation = recovered.get(waiting.operation.id, McpAgentId("agent"))!!
        assertEquals(McpOperationState.FAILED, operation.state)
        assertEquals("PROCESS_INTERRUPTED", operation.outcomeCode)
    }

    @Test
    fun `canonical request hash is independent of object property order but excludes operation id`() {
        val first = buildJsonObject {
            put("b", 2)
            put("a", 1)
            put("operation_id", "op_first")
        }
        val second = buildJsonObject {
            put("operation_id", "op_second")
            put("a", 1)
            put("b", 2)
        }
        assertEquals(requestHash("tool", first), requestHash("tool", second))
        assertNotEquals(requestHash("other", first), requestHash("tool", first))
    }

    @Test
    fun `user approval and denial are audited without request arguments`() = runBlocking {
        val audit = MemoryAuditLogger()
        val manager = PersistentMcpOperationManager(MemoryOperationStorage(), audit)
        val approved = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("secret-content"), "request-0004", null, "scripts/test.py"
        ) as McpOperationDecision.Waiting
        manager.approve(approved.operation.id).getOrThrow()

        val denied = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("another-secret"), "request-0005", null, "scripts/other.py"
        ) as McpOperationDecision.Waiting
        manager.deny(denied.operation.id).getOrThrow()

        assertEquals(listOf("USER_APPROVED", "USER_DENIED"), audit.events.map(McpAuditEvent::outcome))
        assertTrue(audit.events.none { it.toString().contains("secret-content") || it.toString().contains("another-secret") })
    }

    @Test
    fun `one agent cannot execute two writes concurrently`() = runBlocking {
        val manager = PersistentMcpOperationManager(MemoryOperationStorage())
        val first = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("first"), "request-0006", null, "first"
        ) as McpOperationDecision.Waiting
        val second = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("second"), "request-0007", null, "second"
        ) as McpOperationDecision.Waiting
        manager.approve(first.operation.id).getOrThrow()
        manager.approve(second.operation.id).getOrThrow()
        manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("first"), "request-0006", first.operation.id, "first"
        ) as McpOperationDecision.Execute
        val rejected = manager.requestExecution(
            writeContext(), WRITE_TOOL, writeArguments("second"), "request-0007", second.operation.id, "second"
        ) as McpOperationDecision.Rejected
        assertEquals("OPERATION_IN_PROGRESS", rejected.code)
    }
}

private class MemoryOperationStorage(
    initial: List<McpOperation> = emptyList()
) : McpOperationStorage {
    var operations: List<McpOperation> = initial
    override fun load(): List<McpOperation> = operations
    override fun save(operations: List<McpOperation>): Boolean {
        this.operations = operations
        return true
    }
}

private class MemoryAuditLogger : McpAuditLogger {
    val events = mutableListOf<McpAuditEvent>()
    override suspend fun record(event: McpAuditEvent) {
        events += event
    }
}

private val WRITE_TOOL = McpToolDefinition(
    name = "update_script",
    description = "test",
    requiredScopes = setOf(McpScope.SCRIPT_WRITE),
    riskLevel = McpRiskLevel.CONTROLLED_WRITE
)

private fun writeArguments(content: String) = buildJsonObject {
    put("idempotency_key", "request-0001")
    put("path", "scripts/test.py")
    put("content", content)
}

private fun writeContext() = McpCallContext(
    requestId = "request",
    agent = McpAgent(
        id = McpAgentId("agent"),
        name = "Agent",
        scopes = McpAgentManager.DEFAULT_READ_SCOPES + McpAgentManager.PHASE_2_SCOPES,
        allowedAccountIds = setOf("account"),
        createdAtEpochMs = 1L
    ),
    accountId = "account"
)
