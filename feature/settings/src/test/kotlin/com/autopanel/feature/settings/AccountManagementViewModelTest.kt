package com.autopanel.feature.settings

import android.net.Uri
import app.cash.turbine.test
import com.autopanel.core.data.security.PrivateCertificateFileStore
import com.autopanel.core.data.session.AuthMode
import com.autopanel.core.data.session.AccountLocalDataCleaner
import com.autopanel.core.data.session.AccountCleanupResult
import com.autopanel.core.data.session.SavedAccountSwitchResult
import com.autopanel.core.data.session.SavedAccountSwitcher
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.data.session.historyId
import com.autopanel.core.data.session.mcpAccountId
import com.autopanel.core.mcp.McpAgentManager
import com.autopanel.feature.backup.NetworkStorageAccountCleaner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val switcher = mockk<SavedAccountSwitcher>()
    private val localDataCleaner = mockk<AccountLocalDataCleaner>()
    private val mcpAgentManager = mockk<McpAgentManager>(relaxed = true)
    private val networkStorageAccountCleaner = mockk<NetworkStorageAccountCleaner>(relaxed = true)
    private val certificateFileStore = mockk<PrivateCertificateFileStore>(relaxed = true)
    private val first = StoredAccount("https://one.example.com", "admin", alias = "One")
    private val second = StoredAccount(
        "https://two.example.com",
        "client-id",
        alias = "Two",
        authMode = AuthMode.CLIENT_CREDENTIALS,
        certPath = "/private/client.p12",
        lastUsedAtEpochMs = 42L
    )
    private val accounts = MutableStateFlow(listOf(first, second))
    private val activeId = MutableStateFlow<String?>(first.historyId())
    private lateinit var viewModel: AccountManagementViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { sessionManager.accountsFlow } returns accounts
        every { sessionManager.activeAccountHistoryIdFlow } returns activeId
        coEvery { localDataCleaner.clear(any()) } returns AccountCleanupResult(0, 0)
        viewModel = AccountManagementViewModel(
            sessionManager,
            switcher,
            localDataCleaner,
            mcpAgentManager,
            networkStorageAccountCleaner,
            certificateFileStore
        )
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ui marks current account without exposing a credential`() = runTest(dispatcher) {
        viewModel.uiState.test {
            val initial = awaitItem()
            val state = if (initial.accounts.isEmpty()) awaitItem() else initial
            assertEquals(2, state.accounts.size)
            assertTrue(state.accounts.first().isCurrent)
            assertFalse(state.accounts.last().isCurrent)
            assertTrue(state.accounts.last().hasClientCertificate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful direct switch emits navigation event`() = runTest(dispatcher) {
        coEvery { switcher.switch(second, null) } returns SavedAccountSwitchResult.Success

        viewModel.events.test {
            viewModel.switchAccount(second.historyId())
            dispatcher.scheduler.runCurrent()
            assertEquals(AccountManagementEvent.AccountActivated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a non-current account removes only that identity`() = runTest(dispatcher) {
        viewModel.requestDelete(second.historyId())
        viewModel.confirmDelete()
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { sessionManager.removeStoredAccount(second) }
        coVerify(exactly = 1) { localDataCleaner.clear(second) }
        coVerify(exactly = 1) { mcpAgentManager.removeAccountAccess(second.mcpAccountId()) }
        coVerify(exactly = 1) { networkStorageAccountCleaner.clear(second) }
        coVerify(exactly = 0) { sessionManager.clearSession() }
    }

    @Test
    fun `server preview masks domain and ip details`() {
        assertEquals("https://p***.example.com:5700", maskServerAddress("https://panel.example.com:5700"))
        assertEquals("http://192.168.*.*:5700", maskServerAddress("http://192.168.1.20:5700"))
    }

    @Test
    fun `client certificate can be staged and saved for an inactive account`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val updatedAccount = slot<StoredAccount>()
        coEvery { certificateFileStore.importClientCertificate(uri) } returns "/private/new-client.p12"

        viewModel.uiState.test {
            val initial = awaitItem()
            if (initial.accounts.isEmpty()) awaitItem()
            viewModel.editAccount(second.historyId())
            viewModel.replaceClientCertificate(uri)
            dispatcher.scheduler.runCurrent()
            viewModel.updateEdit { it.copy(certificatePassword = "new-password") }
            dispatcher.scheduler.runCurrent()

            val editing = expectMostRecentItem().editing
            assertTrue(editing?.isClientCertificateStaged == true)
            assertTrue(editing?.isClientCertificateChanged == true)

            viewModel.saveEdit()
            dispatcher.scheduler.runCurrent()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            sessionManager.updateStoredAccountCertificate(
                second,
                capture(updatedAccount),
                "new-password"
            )
        }
        assertEquals("/private/new-client.p12", updatedAccount.captured.certPath)
        coVerify(exactly = 1) {
            sessionManager.cleanupUnreferencedCertificateFiles(listOf("/private/client.p12"))
        }
    }

    @Test
    fun `failed active certificate replacement restores old account and removes staged file`() =
        runTest(dispatcher) {
            val uri = mockk<Uri>()
            val activeWithCertificate = second
            activeId.value = activeWithCertificate.historyId()
            dispatcher.scheduler.runCurrent()
            coEvery { certificateFileStore.importClientCertificate(uri) } returns
                "/private/rejected-client.p12"
            coEvery { sessionManager.getRememberedCertificatePassword(activeWithCertificate) } returns
                "old-password"
            coEvery { switcher.switch(any(), null) } returns
                SavedAccountSwitchResult.Failure("证书验证失败")

            viewModel.editAccount(activeWithCertificate.historyId())
            viewModel.replaceClientCertificate(uri)
            dispatcher.scheduler.runCurrent()
            viewModel.updateEdit { it.copy(certificatePassword = "wrong-password") }
            viewModel.saveEdit()
            dispatcher.scheduler.runCurrent()

            coVerify(exactly = 1) {
                sessionManager.updateStoredAccountCertificate(
                    match { it.certPath == "/private/rejected-client.p12" },
                    activeWithCertificate,
                    "old-password"
                )
            }
            coVerify(exactly = 1) {
                sessionManager.cleanupUnreferencedCertificateFiles(
                    listOf("/private/rejected-client.p12")
                )
            }
        }
}
