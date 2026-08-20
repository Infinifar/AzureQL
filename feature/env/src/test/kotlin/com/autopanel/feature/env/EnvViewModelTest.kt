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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
    fun `backup import skips exact duplicates and creates missing entries`() = runTest(dispatcher) {
        val existing = EnvInfo(id = 1, name = "EXISTING", value = "same")
        val created = EnvInfo(id = 2, name = "NEW_VALUE", value = "new", status = EnvStatus.ENABLED)
        val finalList = listOf(existing, created)
        coEvery { repository.getEnvs(any()) } returnsMany listOf(
            Result.success(listOf(existing)),
            Result.success(listOf(existing)),
            Result.success(finalList),
            Result.success(finalList)
        )
        coEvery {
            repository.addEnvs(listOf(Triple("NEW_VALUE", "new", "remark")))
        } returns Result.success(listOf(created))

        val backupDir = File(temporaryFolder.root, "environments").apply { mkdirs() }
        File(backupDir, "envs_backup.json").writeText(
            """
            [
              {"id":99,"name":"EXISTING","value":"same","remarks":"old"},
              {"id":100,"name":"NEW_VALUE","value":"new","remarks":"remark"}
            ]
            """.trimIndent()
        )
        val viewModel = EnvViewModel(repository, context)
        advanceUntilIdle()

        viewModel.importEnvs()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.addEnvs(listOf(Triple("NEW_VALUE", "new", "remark")))
        }
        val message = viewModel.uiState.value.successMessage.orEmpty()
        assertTrue(message.contains("新增 1"))
        assertTrue(message.contains("跳过重复 1"))
    }
}
