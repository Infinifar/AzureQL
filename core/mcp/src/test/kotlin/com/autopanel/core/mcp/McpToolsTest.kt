package com.autopanel.core.mcp

import com.autopanel.core.domain.EnvRepository
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.model.ScriptFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolsTest {
    @Test
    fun `tool schema validation rejects missing and unknown arguments`() {
        val definition = McpToolDefinition(
            name = "safe_write",
            description = "test",
            requiredScopes = emptySet(),
            riskLevel = McpRiskLevel.CONTROLLED_WRITE,
            inputProperties = buildJsonObject {
                put("idempotency_key", buildJsonObject { put("type", "string") })
            },
            requiredInput = listOf("idempotency_key")
        )
        assertTrue(definition.validateArguments(buildJsonObject { }) is McpToolOutcome.Failure)
        assertTrue(definition.validateArguments(buildJsonObject {
            put("idempotency_key", "request-1")
            put("force", true)
        }) is McpToolOutcome.Failure)
        assertTrue(definition.validateArguments(buildJsonObject {
            put("idempotency_key", "request-1")
        }) == null)
    }

    @Test
    fun `environment metadata never includes secret value`() = runBlocking {
        val repository = mockk<EnvRepository>()
        coEvery { repository.getEnvs(any()) } returns Result.success(
            listOf(EnvInfo(id = 1, name = "TOKEN", value = "super-secret", remarks = "test", status = 0))
        )
        val outcome = ListEnvsTool(repository).invoke(testContext(), buildJsonObject { })
        val json = (outcome as McpToolOutcome.Success).payload.toString()
        assertTrue(json.contains("TOKEN"))
        assertTrue(json.contains("value_masked"))
        assertFalse(json.contains("super-secret"))
    }

    @Test
    fun `script read rejects parent traversal before repository access`() = runBlocking {
        val repository = mockk<ScriptRepository>(relaxed = true)
        val outcome = ReadScriptTool(repository).invoke(
            testContext(),
            buildJsonObject { put("path", "../config/auth.json") }
        )
        assertTrue(outcome is McpToolOutcome.Failure)
        coVerify(exactly = 0) { repository.getScripts() }
    }

    @Test
    fun `script read truncates on utf8 code point boundary`() = runBlocking {
        val repository = mockk<ScriptRepository>()
        val script = ScriptFile(
            title = "large.py",
            key = "jobs/large.py",
            parent = "jobs",
            type = "file",
            size = 5
        )
        val draft = ScriptDraft(
            cacheToken = "draft",
            filename = "large.py",
            path = "jobs",
            sourceKey = "jobs/large.py",
            sizeBytes = 5,
            characterCount = 3,
            pageCount = 1,
            hasUtf8Bom = false,
            isUtf8Valid = true,
            editorUri = "content://draft",
            sourceSizeBytes = 5,
            sourceModifiedTime = 1.0,
            originalSha256 = "hash"
        )
        coEvery { repository.getScripts() } returns Result.success(listOf(script))
        coEvery { repository.prepareDraft(script) } returns Result.success(draft)
        coEvery { repository.readDraftText(draft, 4) } returns Result.success("A龙B")
        coEvery { repository.discardDraft(draft) } returns Unit
        val outcome = ReadScriptTool(repository).invoke(
            testContext(),
            buildJsonObject {
                put("path", JsonPrimitive("jobs/large.py"))
                put("max_bytes", JsonPrimitive(4))
            }
        ) as McpToolOutcome.Success
        val json = outcome.payload.toString()
        assertTrue(json.contains("A龙"))
        assertFalse(json.contains("A龙B"))
        assertTrue(json.contains("\"truncated\":true"))
        coVerify(exactly = 1) { repository.discardDraft(draft) }
    }
}

private fun testContext() = McpCallContext(
    requestId = "request",
    agent = McpAgent(
        id = McpAgentId("agent"),
        name = "Agent",
        scopes = McpAgentManager.DEFAULT_READ_SCOPES,
        allowedAccountIds = setOf("account"),
        createdAtEpochMs = 1L
    ),
    accountId = "account"
)
