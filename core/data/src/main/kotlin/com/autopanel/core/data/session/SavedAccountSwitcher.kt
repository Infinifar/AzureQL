package com.autopanel.core.data.session

import android.content.Context
import androidx.work.WorkManager
import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.model.LoginRequest
import com.autopanel.core.model.TwoFactorRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SavedAccountSwitchResult {
    data object Success : SavedAccountSwitchResult
    data class NeedsTwoFactor(val message: String) : SavedAccountSwitchResult
    data class Failure(val message: String) : SavedAccountSwitchResult
}

/**
 * Re-authenticates a saved account against an isolated candidate connection profile. The active
 * session is only replaced after a token has been returned, so a TLS/network/authentication error
 * cannot strand the user outside the previously working account.
 */
@Singleton
class SavedAccountSwitcher @Inject constructor(
    private val sessionManager: SessionManager,
    private val retrofitClient: AutoPanelRetrofitClient,
    private val operationGuard: AccountOperationGuard
) {
    private val switchMutex = Mutex()

    suspend fun switch(
        account: StoredAccount,
        twoFactorCode: String? = null
    ): SavedAccountSwitchResult = switchMutex.withLock {
        try {
            if (!operationGuard.canChangeActiveAccount()) {
                return@withLock SavedAccountSwitchResult.Failure(
                    "备份、恢复或网络导出仍在进行，请等待完成或先取消任务"
                )
            }
            val secret = sessionManager.getRememberedCredential(account)
                ?: return@withLock SavedAccountSwitchResult.Failure(
                    "此账户未保存登录凭据，请通过新增账户重新登录"
                )
            val certificatePassword =
                sessionManager.getRememberedCertificatePassword(account)
            val profile = SessionSnapshot(
                host = account.host,
                username = account.username,
                certPath = account.certPath,
                certPassword = certificatePassword,
                customCaPath = account.customCaPath,
                allowInsecureHttp = account.allowInsecureHttp,
                authMode = account.authMode,
                connectionRevision = System.nanoTime()
            )

            retrofitClient.cancelAllRequests()
            val api = retrofitClient.createApiService(account.host, profile)
            val response = when {
                account.authMode == AuthMode.CLIENT_CREDENTIALS ->
                    api.loginWithClientCredentials(account.username, secret)
                twoFactorCode != null ->
                    api.loginTwoFactor(TwoFactorRequest(account.username, secret, twoFactorCode))
                else -> api.login(LoginRequest(account.username, secret))
            }
            val token = response.data?.token
            when {
                response.code == 200 && !token.isNullOrBlank() -> {
                    sessionManager.activateStoredAccount(
                        account = account,
                        rememberedSecret = secret,
                        certificatePassword = certificatePassword,
                        token = token
                    )
                    SavedAccountSwitchResult.Success
                }
                response.code == 420 && account.authMode == AuthMode.PASSWORD && twoFactorCode == null ->
                    SavedAccountSwitchResult.NeedsTwoFactor(
                        response.message ?: "需要两步验证码"
                    )
                else -> SavedAccountSwitchResult.Failure(
                    response.message ?: "账户切换失败 (${response.code})"
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SavedAccountSwitchResult.Failure(error.message ?: "账户切换失败")
        }
    }

    suspend fun canChangeActiveAccount(): Boolean = operationGuard.canChangeActiveAccount()
}

/** Prevents account changes while account-bound WorkManager data could cross a session boundary. */
@Singleton
class AccountOperationGuard @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun canChangeActiveAccount(): Boolean =
        listOf(UNIQUE_BACKUP_TRANSFER, UNIQUE_BACKUP_RESTORE).none { uniqueName ->
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName)
                .first()
                .any { !it.state.isFinished }
        }

    private companion object {
        const val UNIQUE_BACKUP_TRANSFER = "azureql_backup_transfer"
        const val UNIQUE_BACKUP_RESTORE = "azureql_backup_restore"
    }
}
