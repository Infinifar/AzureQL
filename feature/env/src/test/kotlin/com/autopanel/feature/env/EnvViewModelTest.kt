package com.autopanel.feature.env

import android.content.Context
import com.autopanel.core.domain.EnvRepository
import com.autopanel.core.model.EnvInfo
import com.autopanel.core.model.EnvStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class EnvViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<EnvRepository>()
    private val context = mockk<Context>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { context.getExternalFilesDir(null) } returns temporaryFolder.root
        coEvery { repository.getEnvs(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pin updates item and calls official pin endpoint`() = runTest(dispatcher) {
        val original = EnvInfo(id = 7, name = "JD_COOKIE", value = "secret", isPinned = 0)
        val pinned = original.copy(isPinned = 1)
        coEvery { repository.getEnvs(any()) } returnsMany listOf(
            Result.success(listOf(original)),
            Result.success(listOf(pinned))
        )
        coEvery { repository.pinEnvs(listOf(7)) } returns Result.success(Unit)
        val viewModel = EnvViewModel(repository, context)
        advanceUntilIdle()

        viewModel.togglePin(original)
        assertTrue(viewModel.uiState.value.envs.single().pinned)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.pinEnvs(listOf(7)) }
        assertTrue(viewModel.uiState.value.envs.single().pinned)
    }

    @Test
    fun `search reloads the list with the entered query`() = runTest(dispatcher) {
        val match = EnvInfo(id = 8, name = "AZUREQL_E2E")
        coEvery { repository.getEnvs("") } returns Result.success(emptyList())
        coEvery { repository.getEnvs("AZUREQL") } returns Result.success(listOf(match))
        val viewModel = EnvViewModel(repository, context)
        advanceUntilIdle()

        viewModel.onSearch("AZUREQL")
        advanceUntilIdle()

        assertEquals("AZUREQL", viewModel.uiState.value.searchQuery)
        assertEquals(listOf(match), viewModel.uiState.value.envs)
        coVerify(exactly = 1) { repository.getEnvs("AZUREQL") }
    }

    @Test
    fun `backup import skips exact duplicates and creates missing entries`() = runTest(dispatcher) {
        val existing = EnvInfo(id = 1, name = "EXISTING", value = "same")
        val fakeRepository = FakeEnvRepository(mutableListOf(existing))

        val backupDir = File(temporaryFolder.root, "environments").apply { mkdirs() }
        File(backupDir, "envs_backup.json").writeText(
            """
            [
              {"id":99,"name":"EXISTING","value":"same","remarks":"old"},
              {"id":100,"name":"NEW_VALUE","value":"new","remarks":"remark"}
            ]
            """.trimIndent()
        )
        val viewModel = EnvViewModel(fakeRepository, context)
        advanceUntilIdle()

        viewModel.importEnvs()
        awaitImportCompletion(viewModel)
        assertEquals(
            listOf(Triple("NEW_VALUE", "new", "remark")),
            fakeRepository.addedRequests
        )
        val message = (viewModel.events.first() as EnvEvent.Message).text
        assertTrue(message.contains("新增 1"))
        assertTrue(message.contains("跳过重复 1"))
    }

    @Test
    fun `backup import reads the user selected document`() = runTest(dispatcher) {
        val fakeRepository = FakeEnvRepository(mutableListOf())
        val selectedDocument = ByteArrayInputStream(
            """
            [
              {"name":"SELECTED_FILE","value":"from-picker","remarks":"manual"}
            ]
            """.trimIndent().toByteArray()
        )
        val viewModel = EnvViewModel(fakeRepository, context)
        advanceUntilIdle()

        viewModel.importEnvs(selectedDocument)
        awaitImportCompletion(viewModel)

        assertEquals(
            listOf(Triple("SELECTED_FILE", "from-picker", "manual")),
            fakeRepository.addedRequests
        )
    }

    @Test
    fun `empty backup is rejected locally without repository mutation`() = runTest(dispatcher) {
        val fakeRepository = FakeEnvRepository(mutableListOf())
        val viewModel = EnvViewModel(fakeRepository, context)
        advanceUntilIdle()

        viewModel.importEnvs(ByteArrayInputStream(byteArrayOf()))
        awaitImportCompletion(viewModel)

        assertTrue(fakeRepository.addedRequests.isEmpty())
        assertEquals("备份文件为空", (viewModel.events.first() as EnvEvent.Message).text)
        assertFalse(viewModel.uiState.value.isImportingBackup)
    }

    @Test
    fun `backup import separates duplicate and invalid counts`() = runTest(dispatcher) {
        val existing = EnvInfo(id = 1, name = "EXISTING", value = "same")
        val fakeRepository = FakeEnvRepository(mutableListOf(existing))
        val source = ByteArrayInputStream(
            """
            [
              {"name":"EXISTING","value":"same"},
              {"name":"NEW_VALUE","value":"new"},
              {"name":"NEW_VALUE","value":"new"},
              {"name":"INVALID-NAME","value":"bad"},
              {"name":"MISSING_VALUE"}
            ]
            """.trimIndent().toByteArray()
        )
        val viewModel = EnvViewModel(fakeRepository, context)
        advanceUntilIdle()

        viewModel.importEnvs(source)
        awaitImportCompletion(viewModel)

        val message = (viewModel.events.first() as EnvEvent.Message).text
        assertTrue(message.contains("新增 1"))
        assertTrue(message.contains("跳过重复 2"))
        assertTrue(message.contains("无效 2"))
    }

    @Test
    fun `backup import batches one hundred entries in groups of twenty five`() = runTest(dispatcher) {
        val fakeRepository = FakeEnvRepository(mutableListOf())
        val payload = (1..100).joinToString(prefix = "[", postfix = "]") { index ->
            """{"name":"VALUE_$index","value":"$index"}"""
        }
        val viewModel = EnvViewModel(fakeRepository, context)
        advanceUntilIdle()

        viewModel.importEnvs(ByteArrayInputStream(payload.toByteArray()))
        awaitImportCompletion(viewModel)

        assertEquals(listOf(25, 25, 25, 25), fakeRepository.addBatchSizes)
        assertEquals(100, fakeRepository.addedRequests.size)
        assertTrue((viewModel.events.first() as EnvEvent.Message).text.contains("新增 100"))
    }

    @Test
    fun `failed batch retries individually and reports only unresolved entries`() = runTest(dispatcher) {
        val fakeRepository = FakeEnvRepository(mutableListOf()).apply {
            failMultiEntryBatches = true
            individuallyFailingNames += "VALUE_C"
        }
        val source = ByteArrayInputStream(
            """
            [
              {"name":"VALUE_A","value":"a"},
              {"name":"VALUE_B","value":"b"},
              {"name":"VALUE_C","value":"c"}
            ]
            """.trimIndent().toByteArray()
        )
        val viewModel = EnvViewModel(fakeRepository, context)
        advanceUntilIdle()

        viewModel.importEnvs(source)
        awaitImportCompletion(viewModel)

        assertEquals(listOf(3, 1, 1, 1), fakeRepository.addBatchSizes)
        assertEquals(listOf("VALUE_A", "VALUE_B"), fakeRepository.addedRequests.map { it.first })
        val message = (viewModel.events.first() as EnvEvent.Message).text
        assertTrue(message.contains("新增 2"))
        assertTrue(message.contains("失败 1"))
    }

    @Test
    fun `backup import restores pinned and disabled state after creation`() = runTest(dispatcher) {
        val fakeRepository = FakeEnvRepository(mutableListOf())
        val source = ByteArrayInputStream(
            """
            [
              {"name":"PINNED_VALUE","value":"one","isPinned":1},
              {"name":"DISABLED_VALUE","value":"two","status":1}
            ]
            """.trimIndent().toByteArray()
        )
        val viewModel = EnvViewModel(fakeRepository, context)
        advanceUntilIdle()

        viewModel.importEnvs(source)
        awaitImportCompletion(viewModel)

        assertEquals(1, fakeRepository.pinnedIds.size)
        assertEquals(1, fakeRepository.disabledIds.size)
        assertFalse(viewModel.uiState.value.isImportingBackup)
    }

    /**
     * 可靠等待备份导入完成：`importEnvs` 内部在 viewModelScope（StandardTestDispatcher）上
     * 执行，且读取文件会切到真实 `Dispatchers.IO`。单纯 `first { !isImportingBackup }` 会在
     * 导入尚未开始（isImportingBackup 仍为 false）时就立即返回，导致断言读到空的 addedRequests。
     * 这里先推进虚拟时间让协程进入 IO 挂起态，再交替推进虚拟时间与真实等待，直到导入结束。
     */
    private suspend fun TestScope.awaitImportCompletion(viewModel: EnvViewModel) {
        advanceUntilIdle()
        withContext(Dispatchers.Default) {
            withTimeout(15_000) {
                while (viewModel.uiState.value.isImportingBackup) {
                    dispatcher.scheduler.advanceUntilIdle()
                    delay(10)
                }
            }
        }
    }
}

private class FakeEnvRepository(
    private val envs: MutableList<EnvInfo>
) : EnvRepository {
    val addedRequests = mutableListOf<Triple<String, String, String?>>()
    val addBatchSizes = mutableListOf<Int>()
    val pinnedIds = mutableListOf<Int>()
    val disabledIds = mutableListOf<Int>()
    var failMultiEntryBatches = false
    val individuallyFailingNames = mutableSetOf<String>()

    override suspend fun getEnvs(search: String): Result<List<EnvInfo>> =
        Result.success(envs.toList())

    override suspend fun addEnvs(
        envs: List<Triple<String, String, String?>>
    ): Result<List<EnvInfo>> {
        addBatchSizes += envs.size
        if (failMultiEntryBatches && envs.size > 1) {
            return Result.failure(IllegalStateException("batch rejected"))
        }
        if (envs.any { it.first in individuallyFailingNames }) {
            return Result.failure(IllegalStateException("entry rejected"))
        }
        addedRequests += envs
        val created = envs.mapIndexed { index, entry ->
            EnvInfo(
                id = (this.envs.maxOfOrNull { it.id ?: 0 } ?: 0) + index + 1,
                name = entry.first,
                value = entry.second,
                remarks = entry.third,
                status = EnvStatus.ENABLED
            )
        }
        this.envs += created
        return Result.success(created)
    }

    override suspend fun updateEnv(
        id: Int,
        name: String,
        value: String,
        remarks: String?
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteEnvs(ids: List<Int>): Result<Unit> = Result.success(Unit)
    override suspend fun enableEnvs(ids: List<Int>): Result<Unit> = Result.success(Unit)
    override suspend fun disableEnvs(ids: List<Int>): Result<Unit> {
        disabledIds += ids
        return Result.success(Unit)
    }
    override suspend fun pinEnvs(ids: List<Int>): Result<Unit> {
        pinnedIds += ids
        return Result.success(Unit)
    }
    override suspend fun unpinEnvs(ids: List<Int>): Result<Unit> = Result.success(Unit)
}
