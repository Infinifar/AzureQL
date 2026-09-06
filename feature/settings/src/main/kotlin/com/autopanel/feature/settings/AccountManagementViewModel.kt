package com.autopanel.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.data.session.AuthMode
import com.autopanel.core.data.session.AccountLocalDataCleaner
import com.autopanel.core.data.session.SavedAccountSwitchResult
import com.autopanel.core.data.session.SavedAccountSwitcher
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.data.session.historyId
import com.autopanel.core.data.session.mcpAccountId
import com.autopanel.core.data.security.PrivateCertificateFileStore
import com.autopanel.core.mcp.McpAgentManager
import com.autopanel.feature.backup.NetworkStorageAccountCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountManagementUiState(
    val accounts: List<SavedAccountUi> = emptyList(),
    val activeAccountId: String? = null,
    val switchingAccountId: String? = null,
    val editing: AccountEditUi? = null,
    val deleteCandidateId: String? = null,
    val twoFactorAccountId: String? = null,
    val twoFactorCode: String = "",
    val isSubmittingTwoFactor: Boolean = false
)

data class SavedAccountUi(
    val id: String,
    val alias: String?,
    val host: String,
    val username: String,
    val authMode: AuthMode,
    val allowInsecureHttp: Boolean,
    val hasClientCertificate: Boolean,
    val hasCustomCa: Boolean,
    val lastUsedAtEpochMs: Long,
    val isCurrent: Boolean
)

data class AccountEditUi(
    val originalId: String,
    val alias: String,
    val host: String,
    val username: String,
    val allowInsecureHttp: Boolean,
    val authMode: AuthMode,
    val hasClientCertificate: Boolean,
    val hasCustomCa: Boolean,
    val certificatePassword: String = "",
    val isClientCertificateStaged: Boolean = false,
    val isClientCertificateChanged: Boolean = false,
    val isCustomCaChanged: Boolean = false,
    val isImportingCertificate: Boolean = false,
    val isImportingCustomCa: Boolean = false,
    val isSaving: Boolean = false
)

sealed interface AccountManagementEvent {
    data class Message(val text: String) : AccountManagementEvent
    data object AccountActivated : AccountManagementEvent
    data object SignInRequired : AccountManagementEvent
}

@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val accountSwitcher: SavedAccountSwitcher,
    private val localDataCleaner: AccountLocalDataCleaner,
    private val mcpAgentManager: McpAgentManager,
    private val networkStorageAccountCleaner: NetworkStorageAccountCleaner,
    private val certificateFileStore: PrivateCertificateFileStore
) : ViewModel() {
    private val storedAccounts = sessionManager.accountsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val activeAccountId = sessionManager.activeAccountHistoryIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutableInteraction = MutableStateFlow(AccountManagementUiState())
    private val eventsChannel = Channel<AccountManagementEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<AccountManagementUiState> = combine(
        storedAccounts,
        activeAccountId,
        mutableInteraction
    ) { accounts, activeId, interaction ->
        interaction.copy(
            accounts = accounts.map { account -> account.toUi(activeId) },
            activeAccountId = activeId
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountManagementUiState()
    )

    private var pendingTwoFactorAccount: StoredAccount? = null
    private var pendingDeleteAfterSwitch: StoredAccount? = null
    private var pendingEditRollback: PendingEditRollback? = null
    private var stagedClientCertificatePath: String? = null
    private var stagedCustomCaPath: String? = null
    private var removeClientCertificate = false
    private var removeCustomCa = false
    private var certificateImportJob: Job? = null
    private var customCaImportJob: Job? = null
    private var certificateImportGeneration = 0L
    private var customCaImportGeneration = 0L

    fun switchAccount(accountId: String) {
        val account = findAccount(accountId) ?: return
        if (accountId == activeAccountId.value) {
            message("此账户已是当前账户")
            return
        }
        performSwitch(account)
    }

    fun onTwoFactorCodeChanged(value: String) {
        if (value.length <= 8 && value.all(Char::isDigit)) {
            mutableInteraction.update { it.copy(twoFactorCode = value) }
        }
    }

    fun submitTwoFactor() {
        val account = pendingTwoFactorAccount ?: return
        val code = mutableInteraction.value.twoFactorCode.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            mutableInteraction.update { it.copy(isSubmittingTwoFactor = true) }
            completeSwitch(account, accountSwitcher.switch(account, code))
        }
    }

    fun dismissTwoFactor() {
        viewModelScope.launch {
            rollbackPendingEdit()
            pendingTwoFactorAccount = null
            pendingDeleteAfterSwitch = null
            mutableInteraction.update {
                it.copy(
                    twoFactorAccountId = null,
                    twoFactorCode = "",
                    isSubmittingTwoFactor = false,
                    switchingAccountId = null
                )
            }
        }
    }

    fun editAccount(accountId: String) {
        if (pendingEditRollback != null || mutableInteraction.value.switchingAccountId != null) {
            message("请等待当前账户操作完成")
            return
        }
        val account = findAccount(accountId) ?: return
        discardCertificateDraft()
        mutableInteraction.update {
            it.copy(
                editing = AccountEditUi(
                    originalId = accountId,
                    alias = account.alias.orEmpty(),
                    host = account.host,
                    username = account.username,
                    allowInsecureHttp = account.allowInsecureHttp,
                    authMode = account.authMode,
                    hasClientCertificate = account.certPath != null,
                    hasCustomCa = account.customCaPath != null
                )
            )
        }
    }

    fun updateEdit(transform: (AccountEditUi) -> AccountEditUi) {
        mutableInteraction.update { state ->
            state.copy(editing = state.editing?.let(transform))
        }
    }

    fun dismissEdit() {
        discardCertificateDraft()
        mutableInteraction.update { it.copy(editing = null) }
    }

    fun replaceClientCertificate(uri: Uri) {
        if (mutableInteraction.value.editing == null) return
        val generation = ++certificateImportGeneration
        mutableInteraction.update { state ->
            state.copy(editing = state.editing?.copy(isImportingCertificate = true))
        }
        certificateImportJob = viewModelScope.launch {
            try {
                val imported = certificateFileStore.importClientCertificate(uri)
                if (generation != certificateImportGeneration || mutableInteraction.value.editing == null) {
                    certificateFileStore.delete(imported)
                    return@launch
                }
                stagedClientCertificatePath?.let { certificateFileStore.delete(it) }
                stagedClientCertificatePath = imported
                removeClientCertificate = false
                mutableInteraction.update { state ->
                    state.copy(
                        editing = state.editing?.copy(
                            hasClientCertificate = true,
                            certificatePassword = "",
                            isClientCertificateStaged = true,
                            isClientCertificateChanged = true
                        )
                    )
                }
                message("已选择新的客户端证书，保存后生效")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message(error.message ?: "证书保存失败，请检查文件权限和格式")
            } finally {
                if (generation == certificateImportGeneration) {
                    mutableInteraction.update { state ->
                        state.copy(editing = state.editing?.copy(isImportingCertificate = false))
                    }
                }
            }
        }
    }

    fun clearClientCertificate() {
        certificateImportGeneration++
        stagedClientCertificatePath?.let(::deleteStagedFile)
        stagedClientCertificatePath = null
        removeClientCertificate = true
        mutableInteraction.update { state ->
            state.copy(
                editing = state.editing?.copy(
                    hasClientCertificate = false,
                    certificatePassword = "",
                    isClientCertificateStaged = false,
                    isClientCertificateChanged = true,
                    isImportingCertificate = false
                )
            )
        }
    }

    fun replaceCustomCa(uri: Uri) {
        if (mutableInteraction.value.editing == null) return
        val generation = ++customCaImportGeneration
        mutableInteraction.update { state ->
            state.copy(editing = state.editing?.copy(isImportingCustomCa = true))
        }
        customCaImportJob = viewModelScope.launch {
            try {
                val imported = certificateFileStore.importCustomCa(uri)
                if (generation != customCaImportGeneration || mutableInteraction.value.editing == null) {
                    certificateFileStore.delete(imported)
                    return@launch
                }
                stagedCustomCaPath?.let { certificateFileStore.delete(it) }
                stagedCustomCaPath = imported
                removeCustomCa = false
                mutableInteraction.update { state ->
                    state.copy(
                        editing = state.editing?.copy(
                            hasCustomCa = true,
                            isCustomCaChanged = true
                        )
                    )
                }
                message("已选择新的私有 CA，保存后生效")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message(error.message ?: "私有 CA 保存失败，请检查文件权限和格式")
            } finally {
                if (generation == customCaImportGeneration) {
                    mutableInteraction.update { state ->
                        state.copy(editing = state.editing?.copy(isImportingCustomCa = false))
                    }
                }
            }
        }
    }

    fun clearCustomCa() {
        customCaImportGeneration++
        stagedCustomCaPath?.let(::deleteStagedFile)
        stagedCustomCaPath = null
        removeCustomCa = true
        mutableInteraction.update { state ->
            state.copy(
                editing = state.editing?.copy(
                    hasCustomCa = false,
                    isCustomCaChanged = true,
                    isImportingCustomCa = false
                )
            )
        }
    }

    fun saveEdit() {
        val edit = mutableInteraction.value.editing ?: return
        if (edit.isImportingCertificate || edit.isImportingCustomCa || edit.isSaving) return
        val original = findAccount(edit.originalId) ?: return
        val clientCertificateChanged = stagedClientCertificatePath != null || removeClientCertificate
        val customCaChanged = stagedCustomCaPath != null || removeCustomCa
        val updated = original.copy(
            alias = edit.alias.trim().ifBlank { null },
            host = edit.host.trim().trimEnd('/'),
            username = edit.username.trim(),
            allowInsecureHttp = edit.allowInsecureHttp,
            certPath = stagedClientCertificatePath
                ?: original.certPath.takeUnless { removeClientCertificate },
            customCaPath = stagedCustomCaPath
                ?: original.customCaPath.takeUnless { removeCustomCa }
        )
        viewModelScope.launch {
            mutableInteraction.update { state ->
                state.copy(editing = state.editing?.copy(isSaving = true))
            }
            try {
                val originalCertificatePassword = if (clientCertificateChanged) {
                    sessionManager.getRememberedCertificatePassword(original)
                } else {
                    null
                }
                if (clientCertificateChanged) {
                    sessionManager.updateStoredAccountCertificate(
                        original = original,
                        updated = updated,
                        certificatePassword = edit.certificatePassword
                            .takeIf { updated.certPath != null }
                    )
                } else {
                    sessionManager.updateStoredAccount(original, updated)
                }
                mutableInteraction.update { it.copy(editing = null) }
                val requiresReconnect = updated.host != original.host ||
                    updated.username != original.username ||
                    updated.allowInsecureHttp != original.allowInsecureHttp ||
                    updated.authMode != original.authMode ||
                    updated.certPath != original.certPath ||
                    updated.customCaPath != original.customCaPath
                val rollback = PendingEditRollback(
                    updated = updated,
                    original = original,
                    originalCertificatePassword = originalCertificatePassword,
                    certificatePasswordChanged = clientCertificateChanged,
                    oldCertificatePaths = listOfNotNull(
                        original.certPath.takeIf { it != updated.certPath },
                        original.customCaPath.takeIf { it != updated.customCaPath }
                    ),
                    newCertificatePaths = listOfNotNull(
                        stagedClientCertificatePath,
                        stagedCustomCaPath
                    )
                )
                if (edit.originalId == activeAccountId.value && requiresReconnect) {
                    pendingEditRollback = rollback
                    performSwitch(updated)
                } else {
                    acceptCertificateDraft()
                    runCatching {
                        sessionManager.cleanupUnreferencedCertificateFiles(
                            rollback.oldCertificatePaths
                        )
                    }.onFailure {
                        message("账户已更新，但旧证书文件将在后续清理")
                    }
                    message("账户信息已保存")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableInteraction.update { state ->
                    state.copy(editing = state.editing?.copy(isSaving = false))
                }
                message(error.message ?: "无法保存账户信息")
            }
        }
    }

    fun moveAccount(accountId: String, delta: Int) {
        val index = storedAccounts.value.indexOfFirst { it.historyId() == accountId }
        if (index < 0) return
        viewModelScope.launch {
            sessionManager.moveStoredAccount(
                storedAccounts.value[index],
                index + delta
            )
        }
    }

    fun moveAccountToTop(accountId: String) {
        val account = findAccount(accountId) ?: return
        viewModelScope.launch { sessionManager.moveStoredAccount(account, 0) }
    }

    fun requestDelete(accountId: String) {
        if (findAccount(accountId) != null) {
            mutableInteraction.update { it.copy(deleteCandidateId = accountId) }
        }
    }

    fun dismissDelete() {
        mutableInteraction.update { it.copy(deleteCandidateId = null) }
    }

    fun confirmDelete() {
        val id = mutableInteraction.value.deleteCandidateId ?: return
        val account = findAccount(id) ?: return
        mutableInteraction.update { it.copy(deleteCandidateId = null) }
        viewModelScope.launch {
            try {
                if (id != activeAccountId.value) {
                    deleteAccount(account)
                    message("账户已删除")
                    return@launch
                }
                val replacement = storedAccounts.value.firstOrNull { it.historyId() != id }
                if (replacement == null) {
                    if (!accountSwitcher.canChangeActiveAccount()) {
                        message("备份、恢复或网络导出仍在进行，无法删除当前账户")
                        return@launch
                    }
                    cleanAccountDependencies(account)
                    sessionManager.clearActiveAccount()
                    sessionManager.removeStoredAccount(account)
                    eventsChannel.send(AccountManagementEvent.SignInRequired)
                } else {
                    pendingDeleteAfterSwitch = account
                    performSwitch(replacement)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message(error.message ?: "账户清理失败，请重试")
            }
        }
    }

    private fun performSwitch(account: StoredAccount) {
        viewModelScope.launch {
            mutableInteraction.update { it.copy(switchingAccountId = account.historyId()) }
            completeSwitch(account, accountSwitcher.switch(account))
        }
    }

    private suspend fun completeSwitch(
        account: StoredAccount,
        result: SavedAccountSwitchResult
    ) {
        when (result) {
            SavedAccountSwitchResult.Success -> {
                val completedEdit = pendingEditRollback
                pendingEditRollback = null
                if (completedEdit != null) {
                    acceptCertificateDraft()
                    runCatching {
                        sessionManager.cleanupUnreferencedCertificateFiles(
                            completedEdit.oldCertificatePaths
                        )
                    }.onFailure {
                        message("账户已更新，但旧证书文件将在后续清理")
                    }
                }
                val deletionError = pendingDeleteAfterSwitch?.let { account ->
                    try {
                        deleteAccount(account)
                        null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        error
                    }
                }
                pendingDeleteAfterSwitch = null
                pendingTwoFactorAccount = null
                mutableInteraction.update {
                    it.copy(
                        switchingAccountId = null,
                        twoFactorAccountId = null,
                        twoFactorCode = "",
                        isSubmittingTwoFactor = false
                    )
                }
                deletionError?.let {
                    message(it.message ?: "已切换账户，但旧账户清理失败，请重试删除")
                }
                eventsChannel.send(AccountManagementEvent.AccountActivated)
            }
            is SavedAccountSwitchResult.NeedsTwoFactor -> {
                pendingTwoFactorAccount = account
                mutableInteraction.update {
                    it.copy(
                        twoFactorAccountId = account.historyId(),
                        twoFactorCode = "",
                        isSubmittingTwoFactor = false
                    )
                }
                message(result.message)
            }
            is SavedAccountSwitchResult.Failure -> {
                rollbackPendingEdit()
                pendingDeleteAfterSwitch = null
                pendingTwoFactorAccount = null
                mutableInteraction.update {
                    it.copy(
                        switchingAccountId = null,
                        twoFactorAccountId = null,
                        twoFactorCode = "",
                        isSubmittingTwoFactor = false
                    )
                }
                message(result.message)
            }
        }
    }

    private suspend fun rollbackPendingEdit() {
        val rollback = pendingEditRollback ?: return
        val restored = runCatching {
            if (rollback.certificatePasswordChanged) {
                sessionManager.updateStoredAccountCertificate(
                    original = rollback.updated,
                    updated = rollback.original,
                    certificatePassword = rollback.originalCertificatePassword
                )
            } else {
                sessionManager.updateStoredAccount(rollback.updated, rollback.original)
            }
        }
        if (restored.isSuccess) {
            acceptCertificateDraft()
            runCatching {
                sessionManager.cleanupUnreferencedCertificateFiles(rollback.newCertificatePaths)
            }
        } else {
            message("无法恢复原账户配置，请重新打开账户页面检查")
        }
        pendingEditRollback = null
    }

    private fun discardCertificateDraft() {
        certificateImportGeneration++
        customCaImportGeneration++
        val staged = listOfNotNull(stagedClientCertificatePath, stagedCustomCaPath)
        acceptCertificateDraft()
        staged.forEach(::deleteStagedFile)
    }

    private fun acceptCertificateDraft() {
        certificateImportJob = null
        customCaImportJob = null
        stagedClientCertificatePath = null
        stagedCustomCaPath = null
        removeClientCertificate = false
        removeCustomCa = false
    }

    private fun deleteStagedFile(path: String) {
        viewModelScope.launch {
            runCatching { certificateFileStore.delete(path) }
        }
    }

    private fun findAccount(id: String): StoredAccount? =
        storedAccounts.value.firstOrNull { it.historyId() == id }

    private suspend fun deleteAccount(account: StoredAccount) {
        cleanAccountDependencies(account)
        sessionManager.removeStoredAccount(account)
    }

    private suspend fun cleanAccountDependencies(account: StoredAccount) {
        mcpAgentManager.removeAccountAccess(account.mcpAccountId())
        networkStorageAccountCleaner.clear(account)
        localDataCleaner.clear(account)
    }

    private fun message(text: String) {
        eventsChannel.trySend(AccountManagementEvent.Message(text))
    }
}

private data class PendingEditRollback(
    val updated: StoredAccount,
    val original: StoredAccount,
    val originalCertificatePassword: String?,
    val certificatePasswordChanged: Boolean,
    val oldCertificatePaths: List<String>,
    val newCertificatePaths: List<String>
)

private fun StoredAccount.toUi(activeId: String?) = SavedAccountUi(
    id = historyId(),
    alias = alias,
    host = host,
    username = username,
    authMode = authMode,
    allowInsecureHttp = allowInsecureHttp,
    hasClientCertificate = certPath != null,
    hasCustomCa = customCaPath != null,
    lastUsedAtEpochMs = lastUsedAtEpochMs,
    isCurrent = historyId() == activeId
)
