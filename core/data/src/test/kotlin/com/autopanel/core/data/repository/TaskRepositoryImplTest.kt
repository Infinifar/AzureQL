package com.autopanel.core.data.repository

import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.TaskCreateRequest
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskListData
import com.autopanel.core.model.TaskLogResponse
import com.autopanel.core.model.TaskScheduleType
import com.autopanel.core.model.TaskUpdateRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Provider

class TaskRepositoryImplTest {
    private val api = mockk<AutoPanelApiService>()
    private val provider = mockk<Provider<AutoPanelApiService>> {
        every { get() } returns api
    }
    private val cache = mockk<ResponseCache>(relaxed = true)
    private val repository = TaskRepositoryImpl(provider, cache)

    @Test
    fun `create task maps all QingLong 221 fields`() = runTest {
        coEvery { api.addTask(any()) } returns ApiResponse(code = 200, data = TaskInfo(id = 3))
        val draft = TaskDraft(
            name = "Lenovo",
            command = "python3 Lenovo.py",
            scheduleType = TaskScheduleType.NORMAL,
            schedule = "0 1 * * *",
            extraSchedules = listOf("0 2 * * *"),
            labels = listOf("daily", "reward"),
            allowMultipleInstances = true,
            logName = "Lenovo_LenovoClub_347",
            workDir = "automation/lenovo",
            taskBefore = "echo before",
            taskAfter = "echo after"
        )

        val result = repository.addTask(draft)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            api.addTask(match<TaskCreateRequest> { body ->
                body.schedule == "0 1 * * *" &&
                    body.extraSchedules.single().schedule == "0 2 * * *" &&
                    body.labels == listOf("daily", "reward") &&
                    body.allowMultipleInstances == 1 &&
                    body.logName == "Lenovo_LenovoClub_347" &&
                    body.workDir == "automation/lenovo" &&
                    body.taskBefore == "echo before" &&
                    body.taskAfter == "echo after"
            })
        }
    }

    @Test
    fun `boot task maps schedule marker and drops regular extra schedules`() = runTest {
        coEvery { api.addTask(any()) } returns ApiResponse(code = 200, data = TaskInfo(id = 4))

        val result = repository.addTask(
            TaskDraft(
                name = "boot",
                command = "python3 boot.py",
                scheduleType = TaskScheduleType.BOOT,
                extraSchedules = listOf("0 2 * * *")
            )
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            api.addTask(match<TaskCreateRequest> { body ->
                body.schedule == "@boot" && body.extraSchedules.isEmpty()
            })
        }
    }

    @Test
    fun `update task sends empty values so advanced fields can be cleared`() = runTest {
        coEvery { api.updateTask(any()) } returns ApiResponse(code = 200, data = TaskInfo(id = 8))

        val result = repository.updateTask(
            TaskDraft(
                id = 8,
                name = "manual",
                command = "node manual.js",
                scheduleType = TaskScheduleType.ONCE
            )
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            api.updateTask(match<TaskUpdateRequest> { body ->
                body.id == 8 &&
                    body.schedule == "@once" &&
                    body.labels.isEmpty() &&
                    body.extraSchedules.isEmpty() &&
                    body.allowMultipleInstances == 0 &&
                    body.logName.isEmpty() &&
                    body.workDir.isEmpty() &&
                    body.taskBefore.isEmpty() &&
                    body.taskAfter.isEmpty()
            })
        }
    }

    @Test
    fun `task list sends QingLong view query for every selected label`() = runTest {
        coEvery { api.getTasks("weibo", 1, 50, any()) } returns
            ApiResponse(code = 200, data = TaskListData(data = emptyList(), total = 0))

        val result = repository.getTasks(
            search = "weibo",
            page = 1,
            size = 50,
            labels = setOf("reward", "daily")
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            api.getTasks("weibo", 1, 50, match { queryString ->
                val query = Json.parseToJsonElement(requireNotNull(queryString)).jsonObject
                val filters = query.getValue("filters").jsonArray
                query.getValue("filterRelation").jsonPrimitive.content == "and" &&
                    filters.map { it.jsonObject.getValue("property").jsonPrimitive.content }
                        .all { it == "labels" } &&
                    filters.map { it.jsonObject.getValue("value").jsonPrimitive.content } ==
                        listOf("daily", "reward") &&
                    filters.map { it.jsonObject.getValue("operation").jsonPrimitive.content }
                        .all { it == "Reg" }
            })
        }
    }

    @Test
    fun `task log keeps incremental cursor metadata`() = runTest {
        coEvery { api.getTaskLogChunk(9, 128, 4096, false) } returns TaskLogResponse(
            code = 200,
            data = "next lines",
            logStatus = JsonPrimitive("running"),
            offset = 128,
            nextOffset = 138,
            total = 138,
            truncated = false
        )

        val result = repository.getTaskLogChunk(9, offset = 128, limit = 4096, tail = false)

        assertTrue(result.isSuccess)
        assertEquals("next lines", result.getOrThrow().content)
        assertEquals(128L, result.getOrThrow().offset)
        assertEquals(138L, result.getOrThrow().nextOffset)
        assertEquals("running", result.getOrThrow().logStatus)
    }

    @Test
    fun `task log response accepts string and numeric status values`() {
        val parser = Json { ignoreUnknownKeys = true }

        val missing = parser.decodeFromString<TaskLogResponse>(
            """{"code":200,"data":"日志不存在","logStatus":"notFound"}"""
        )
        val running = parser.decodeFromString<TaskLogResponse>(
            """{"code":200,"data":"running","logStatus":0}"""
        )

        assertEquals("notFound", missing.logStatus?.jsonPrimitive?.content)
        assertEquals("0", running.logStatus?.jsonPrimitive?.content)
    }
}
