package com.autopanel.core.mcp

import com.autopanel.core.domain.DependencyRepository
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.model.DependencyInfo
import com.autopanel.core.model.DependencyStatus
import com.autopanel.core.model.LogFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpObservabilityToolsTest {
    @Test
    fun `dependency check uses exact name and reports installed state`() = runBlocking {
        val repository = mockk<DependencyRepository>()
        coEvery { repository.getDependencies("requests", "python3") } returns Result.success(
            listOf(
                DependencyInfo(id = 1, name = "requests-cache", type = 1, status = DependencyStatus.INSTALLED),
                DependencyInfo(id = 2, name = "requests", type = 1, status = DependencyStatus.INSTALLED)
            )
        )
        val outcome = CheckDependencyTool(repository).invoke(
            observabilityContext(),
            buildJsonObject {
                put("name", "requests")
                put("type", "python3")
            }
        ) as McpToolOutcome.Success
        val json = outcome.payload.toString()
        assertTrue(json.contains("\"found\":true"))
        assertTrue(json.contains("\"installed\":true"))
        assertFalse(json.contains("requests-cache"))
    }

    @Test
    fun `log list returns bounded file paths without content`() = runBlocking {
        val repository = mockk<LogRepository>()
        coEvery { repository.getLogFiles() } returns Result.success(logTree())
        val outcome = ListLogsTool(repository).invoke(
            observabilityContext(),
            buildJsonObject { put("limit", 1) }
        ) as McpToolOutcome.Success
        val json = outcome.payload.toString()
        assertTrue(json.contains("task/first.log"))
        assertTrue(json.contains("\"truncated\":true"))
        coVerify(exactly = 0) { repository.getLogContent(any(), any()) }
    }

    @Test
    fun `log tail rejects traversal before repository access`() = runBlocking {
        val repository = mockk<LogRepository>(relaxed = true)
        val outcome = ReadLogTailTool(repository).invoke(
            observabilityContext(),
            buildJsonObject { put("path", "../config/auth.json") }
        )
        assertTrue(outcome is McpToolOutcome.Failure)
        coVerify(exactly = 0) { repository.getLogFiles() }
        coVerify(exactly = 0) { repository.getLogContent(any(), any()) }
    }

    @Test
    fun `log tail keeps only requested trailing lines`() = runBlocking {
        val repository = mockk<LogRepository>()
        coEvery { repository.getLogFiles() } returns Result.success(logTree())
        coEvery { repository.getLogContent("first.log", "task") } returns
            Result.success("old-secret\nline-one\n龙龙\nfinal")
        val outcome = ReadLogTailTool(repository).invoke(
            observabilityContext(),
            buildJsonObject {
                put("path", "task/first.log")
                put("lines", 2)
                put("max_bytes", 64)
            }
        ) as McpToolOutcome.Success
        val json = outcome.payload.toString()
        assertTrue(json.contains("龙龙\\nfinal"))
        assertFalse(json.contains("old-secret"))
        assertTrue(json.contains("\"truncated\":true"))
    }

    @Test
    fun `task log tail respects utf8 byte suffix limit`() = runBlocking {
        val repository = mockk<LogRepository>()
        coEvery { repository.getTaskLog(42) } returns Result.success("A龙B")
        val outcome = GetTaskLogTool(repository).invoke(
            observabilityContext(),
            buildJsonObject {
                put("task_id", 42)
                put("lines", 10)
                put("max_bytes", 4)
            }
        ) as McpToolOutcome.Success
        val json = outcome.payload.toString()
        assertTrue(json.contains("龙B"))
        assertFalse(json.contains("A龙B"))
        assertTrue(json.contains("\"returned_bytes\":4"))
    }
}

private fun logTree() = listOf(
    LogFile(
        title = "task",
        key = "task",
        type = "directory",
        children = listOf(
            LogFile(title = "first.log", key = "task/first.log", parent = "task", type = "file", size = 100),
            LogFile(title = "second.log", key = "task/second.log", parent = "task", type = "file", size = 200)
        )
    )
)

private fun observabilityContext() = McpCallContext(
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
