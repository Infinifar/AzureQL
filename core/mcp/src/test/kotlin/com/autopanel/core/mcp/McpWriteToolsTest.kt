package com.autopanel.core.mcp

import com.autopanel.core.domain.EnvRepository
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.ScriptFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpWriteToolsTest {
    @Test
    fun `create script rejects traversal before reading repository`() = runBlocking {
        val repository = mockk<ScriptRepository>(relaxed = true)
        val tool = CreateScriptTool(repository, WaitingOperationManager())
        val outcome = tool.invoke(writeToolContext(), buildJsonObject {
            put("idempotency_key", "script-create-1")
            put("path", "../config.sh")
            put("content", "echo unsafe")
        })
        assertTrue(outcome is McpToolOutcome.Failure)
        coVerify(exactly = 0) { repository.getScripts() }
        coVerify(exactly = 0) { repository.addScript(any(), any(), any()) }
    }

    @Test
    fun `environment secret waits for confirmation and is never returned`() = runBlocking {
        val repository = mockk<EnvRepository>()
        val operationManager = WaitingOperationManager()
        val tool = CreateEnvTool(repository, operationManager)
        val arguments = buildJsonObject {
            put("idempotency_key", "env-create-0001")
            put("name", "SECRET_TOKEN")
            put("value", "never-return-this")
        }
        val waiting = tool.invoke(writeToolContext(), arguments) as McpToolOutcome.Success
        assertTrue(waiting.payload.toString().contains("waiting_confirmation"))
        assertFalse(waiting.payload.toString().contains("never-return-this"))
        coVerify(exactly = 0) { repository.addEnvs(any()) }

        operationManager.decision = McpOperationDecision.Execute(operationManager.operation)
        coEvery { repository.addEnvs(any()) } returns Result.success(emptyList())
        val executed = tool.invoke(
            writeToolContext(),
            JsonObject(arguments + ("operation_id" to kotlinx.serialization.json.JsonPrimitive(operationManager.operation.id)))
        ) as McpToolOutcome.Success
        assertFalse(executed.payload.toString().contains("never-return-this"))
        assertTrue(executed.payload.toString().contains("value_included"))
        coVerify(exactly = 1) { repository.addEnvs(any()) }
    }

    @Test
    fun `update script rejects stale sha before creating an operation`() = runBlocking {
        val repository = mockk<ScriptRepository>()
        val script = ScriptFile(title = "test.py", key = "scripts/test.py", parent = "scripts", type = "file")
        val draft = ScriptDraft(
            cacheToken = "draft",
            filename = "test.py",
            path = "scripts",
            sourceKey = "scripts/test.py",
            sizeBytes = 4,
            characterCount = 4,
            pageCount = 1,
            hasUtf8Bom = false,
            isUtf8Valid = true,
            editorUri = "content://draft",
            sourceSizeBytes = 4,
            sourceModifiedTime = 1.0,
            originalSha256 = "a".repeat(64)
        )
        coEvery { repository.getScripts() } returns Result.success(listOf(script))
        coEvery { repository.prepareDraft(script) } returns Result.success(draft)
        coEvery { repository.discardDraft(draft) } returns Unit
        val outcome = UpdateScriptTool(repository, WaitingOperationManager()).invoke(
            writeToolContext(),
            buildJsonObject {
                put("idempotency_key", "script-update-1")
                put("path", "scripts/test.py")
                put("content", "new")
                put("expected_sha256", "b".repeat(64))
            }
        ) as McpToolOutcome.Failure
        assertTrue(outcome.code == "SCRIPT_CONFLICT")
        coVerify(exactly = 0) { repository.replaceDraftText(any(), any(), any()) }
        coVerify(exactly = 1) { repository.discardDraft(draft) }
    }

    @Test
    fun `task hooks cannot invoke task command`() = runBlocking {
        val repository = mockk<TaskRepository>(relaxed = true)
        val outcome = CreateTaskTool(repository, WaitingOperationManager()).invoke(
            writeToolContext(),
            buildJsonObject {
                put("idempotency_key", "task-create-1")
                put("name", "safe name")
                put("command", "python test.py")
                put("schedule_type", "normal")
                put("schedule", "0 0 * * *")
                put("task_before", "task another-command")
            }
        )
        assertTrue(outcome is McpToolOutcome.Failure)
        coVerify(exactly = 0) { repository.addTask(any()) }
    }
}

private class WaitingOperationManager : McpOperationManager {
    val operation = McpOperation(
        id = "op_test",
        agentId = "agent",
        agentName = "Agent",
        accountId = "account",
        tool = "test",
        risk = McpRiskLevel.CONTROLLED_WRITE.name,
        state = McpOperationState.WAITING_CONFIRMATION,
        targetSummary = "target",
        idempotencyKeyHash = "hash",
        requestHash = "request-hash",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        expiresAtEpochMs = Long.MAX_VALUE
    )
    var decision: McpOperationDecision = McpOperationDecision.Waiting(operation)
    override val operations: StateFlow<List<McpOperation>> = MutableStateFlow(listOf(operation))
    override suspend fun requestExecution(
        context: McpCallContext,
        tool: McpToolDefinition,
        arguments: JsonObject,
        idempotencyKey: String,
        operationId: String?,
        targetSummary: String
    ): McpOperationDecision = decision
    override suspend fun approve(operationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deny(operationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun get(operationId: String, agentId: McpAgentId): McpOperation? = operation
    override suspend fun complete(operationId: String, outcome: McpToolOutcome) = Unit
}

private fun writeToolContext() = McpCallContext(
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
