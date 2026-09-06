package com.autopanel.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autopanel.core.data.performance.performanceTraceAsync
import com.autopanel.core.data.security.SecureCredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "ql_session")

@Serializable
enum class AuthMode {
    PASSWORD,
    CLIENT_CREDENTIALS
}

data class SessionSnapshot(
    val host: String? = null,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val alias: String? = null,
    val rememberPassword: Boolean = false,
    val certPath: String? = null,
    val certPassword: String? = null,
    val customCaPath: String? = null,
    val allowInsecureHttp: Boolean = false,
    val authMode: AuthMode = AuthMode.PASSWORD,
    /** In-memory generation used to invalidate connection material without entering cache keys. */
    val connectionRevision: Long = 0L
)

@Singleton
class SessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureCredentialStore: SecureCredentialStore
) {
    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PASSWORD = stringPreferencesKey("password")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_ALIAS = stringPreferencesKey("alias")
        private val KEY_REMEMBER = booleanPreferencesKey("remember_password")
        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_CERT_PATH = stringPreferencesKey("cert_path")
        private val KEY_CERT_PASSWORD = stringPreferencesKey("cert_password")
        private val KEY_CUSTOM_CA_PATH = stringPreferencesKey("custom_ca_path")
        private val KEY_ALLOW_INSECURE_HTTP = booleanPreferencesKey("allow_insecure_http")
        private val KEY_AUTH_MODE = stringPreferencesKey("auth_mode")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private const val TRACE_SESSION_LOAD = "AzureQL:Session.load"
        private const val TRACE_SESSION_RELOAD = "AzureQL:Session.reload"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val migrationMutex = Mutex()
    private val sessionLoadMutex = Mutex()
    private val sessionState = MutableStateFlow<SessionSnapshot?>(null)

    /**
     * Cold view over the single in-memory session snapshot. The first collector performs one
     * persistent load; later collectors and requests reuse memory until an explicit session event.
     */
    val sessionFlow: Flow<SessionSnapshot> = flow {
        emit(getSession())
        emitAll(sessionState.filterNotNull())
    }
        .distinctUntilChanged()

    val hostFlow: Flow<String?> = sessionFlow.map { it.host }.distinctUntilChanged()
    val usernameFlow: Flow<String?> = sessionFlow.map { it.username }.distinctUntilChanged()
    val passwordFlow: Flow<String?> = sessionFlow.map { it.password }.distinctUntilChanged()
    val tokenFlow: Flow<String?> = sessionFlow.map { it.token }.distinctUntilChanged()
    val aliasFlow: Flow<String?> = sessionFlow.map { it.alias }.distinctUntilChanged()
    val isLoggedInFlow: Flow<Boolean> = tokenFlow.map { it != null }.distinctUntilChanged()
    val activeAccountHistoryIdFlow: Flow<String?> = sessionFlow
        .map { it.toStoredAccount()?.historyId() }
        .distinctUntilChanged()

    val accountsFlow: Flow<List<StoredAccount>> = context.sessionDataStore.data.map { prefs ->
        val raw = prefs[KEY_ACCOUNTS_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<StoredAccount>>(raw) }.getOrDefault(emptyList())
    }

    /** 深色模式偏好："system" / "light" / "dark" */
    val darkModeFlow: Flow<String> = context.sessionDataStore.data.map { it[KEY_DARK_MODE] ?: "system" }

    /** 自定义主题色（"#AARRGGBB"），null 表示使用默认品牌色 */
    val themeColorFlow: Flow<String?> = context.sessionDataStore.data.map { it[KEY_THEME_COLOR] }

    /** 是否启用系统动态取色（Material You，Android 12+） */
    val dynamicColorFlow: Flow<Boolean> = context.sessionDataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }

    /** Synchronous memory-only view for OkHttp interceptors. Never performs disk I/O. */
    val currentSession: SessionSnapshot
        get() = sessionState.value ?: SessionSnapshot()

    /** Loads persisted credentials once, then returns the in-memory snapshot for later requests. */
    suspend fun getSession(): SessionSnapshot {
        sessionState.value?.let { return it }
        return sessionLoadMutex.withLock {
            sessionState.value ?: performanceTraceAsync(TRACE_SESSION_LOAD) {
                snapshotFromPreferences(context.sessionDataStore.data.first())
                    .also { snapshot ->
                        migrateCurrentRememberedCredential(snapshot)
                        migrateCurrentAccountMetadata(snapshot)
                        sessionState.value = snapshot
                    }
            }
        }
    }

    /** Explicit reload hook for account import/edit flows that change storage outside this class. */
    suspend fun reloadSession(): SessionSnapshot = sessionLoadMutex.withLock {
        performanceTraceAsync(TRACE_SESSION_RELOAD) {
            snapshotFromPreferences(context.sessionDataStore.data.first()).let { snapshot ->
                migrateCurrentRememberedCredential(snapshot)
                migrateCurrentAccountMetadata(snapshot)
                snapshot.copy(
                    connectionRevision = (sessionState.value?.connectionRevision ?: 0L) + 1L
                ).also { sessionState.value = it }
            }
        }
    }

    suspend fun configureConnection(
        host: String,
        certPath: String?,
        certPassword: String?,
        customCaPath: String?,
        allowInsecureHttp: Boolean
    ) {
        val previous = getSession()
        writeSecure(previous.copy(certPassword = certPassword).toSecureCredentials())
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs.putOrRemove(KEY_CERT_PATH, certPath)
            prefs.putOrRemove(KEY_CUSTOM_CA_PATH, customCaPath)
            prefs[KEY_ALLOW_INSECURE_HTTP] = allowInsecureHttp
            removeLegacyCredentials(prefs)
        }
        sessionState.value =
            previous.copy(
                host = host,
                certPath = certPath,
                certPassword = certPassword,
                customCaPath = customCaPath,
                allowInsecureHttp = allowInsecureHttp,
                connectionRevision = previous.connectionRevision + 1L
            )
    }

    suspend fun saveSession(
        host: String,
        username: String,
        password: String,
        token: String,
        alias: String? = null,
        remember: Boolean = false,
        allowInsecureHttp: Boolean = false,
        authMode: AuthMode = AuthMode.PASSWORD
    ) {
        val previous = getSession()
        val savedPassword = password.takeIf { remember }
        val account = StoredAccount(
            host = host,
            username = username,
            alias = alias,
            allowInsecureHttp = allowInsecureHttp,
            authMode = authMode,
            certPath = previous.certPath,
            customCaPath = previous.customCaPath,
            lastUsedAtEpochMs = System.currentTimeMillis()
        )
        writeSecure(
            previous.copy(token = token, password = savedPassword).toSecureCredentials()
        )
        writeRememberedCredential(account, savedPassword)
        writeRememberedCertificatePassword(account, previous.certPassword)
        var evictedAccounts: List<StoredAccount> = emptyList()
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_USERNAME] = username
            prefs.putOrRemove(KEY_ALIAS, alias)
            prefs[KEY_REMEMBER] = remember
            prefs[KEY_ALLOW_INSECURE_HTTP] = allowInsecureHttp
            prefs[KEY_AUTH_MODE] = authMode.name
            removeLegacyCredentials(prefs)
            evictedAccounts = updateHistoryInPrefs(prefs, account)
        }
        removeRememberedCredentials(evictedAccounts)
        sessionState.value =
            previous.copy(
                host = host,
                username = username,
                password = savedPassword,
                token = token,
                alias = alias,
                rememberPassword = remember,
                allowInsecureHttp = allowInsecureHttp,
                authMode = authMode,
                connectionRevision = if (
                    previous.host != host || previous.allowInsecureHttp != allowInsecureHttp
                ) previous.connectionRevision + 1L else previous.connectionRevision
            )
    }

    suspend fun setHost(host: String) {
        getSession()
        context.sessionDataStore.edit { prefs -> prefs[KEY_HOST] = host }
        sessionState.update {
            val previous = it ?: SessionSnapshot()
            previous.copy(host = host, connectionRevision = previous.connectionRevision + 1L)
        }
    }

    suspend fun setDarkMode(mode: String) {
        context.sessionDataStore.edit { prefs -> prefs[KEY_DARK_MODE] = mode }
    }

    suspend fun setThemeColor(hex: String?) {
        context.sessionDataStore.edit { prefs -> prefs.putOrRemove(KEY_THEME_COLOR, hex) }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.sessionDataStore.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun saveCertificate(path: String?, password: String?) {
        val previous = getSession()
        writeSecure(previous.copy(certPassword = password).toSecureCredentials())
        context.sessionDataStore.edit { prefs ->
            prefs.putOrRemove(KEY_CERT_PATH, path)
            removeLegacyCredentials(prefs)
        }
        sessionState.value = previous.copy(
            certPath = path,
            certPassword = password,
            connectionRevision = previous.connectionRevision + 1L
        )
    }

    suspend fun saveCustomCa(path: String?) {
        getSession()
        context.sessionDataStore.edit { prefs -> prefs.putOrRemove(KEY_CUSTOM_CA_PATH, path) }
        sessionState.update {
            val previous = it ?: SessionSnapshot()
            previous.copy(customCaPath = path, connectionRevision = previous.connectionRevision + 1L)
        }
    }

    suspend fun clearSession() {
        val previous = getSession()
        writeSecure(previous.copy(token = null, password = null).toSecureCredentials())
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_REMEMBER] = false
            removeLegacyCredentials(prefs)
        }
        sessionState.value = previous.copy(token = null, password = null, rememberPassword = false)
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) { secureCredentialStore.clear() }
        context.sessionDataStore.edit { it.clear() }
        sessionState.value = SessionSnapshot()
    }

    suspend fun removeFromHistory(host: String) {
        val normalizedTarget = normalizeAccountHost(host)
        var removedAccounts: List<StoredAccount> = emptyList()
        context.sessionDataStore.edit { prefs ->
            val raw = prefs[KEY_ACCOUNTS_JSON] ?: return@edit
            val list = runCatching {
                json.decodeFromString<MutableList<StoredAccount>>(raw)
            }.getOrNull() ?: return@edit
            removedAccounts = list.filter { it.normalizedHost() == normalizedTarget }
            list.removeAll { it.normalizedHost() == normalizedTarget }
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
        }
        removeRememberedCredentials(removedAccounts)
    }

    /** Clears the complete active identity when that saved account is explicitly deleted. */
    suspend fun clearActiveAccount() {
        val previous = getSession()
        writeSecure(SecureCredentialStore.Credentials())
        context.sessionDataStore.edit { prefs ->
            prefs.remove(KEY_HOST)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_ALIAS)
            prefs.remove(KEY_REMEMBER)
            prefs.remove(KEY_CERT_PATH)
            prefs.remove(KEY_CUSTOM_CA_PATH)
            prefs.remove(KEY_ALLOW_INSECURE_HTTP)
            prefs.remove(KEY_AUTH_MODE)
            removeLegacyCredentials(prefs)
        }
        sessionState.value = SessionSnapshot(
            connectionRevision = previous.connectionRevision + 1L
        )
    }

    /** Updates one saved account without exposing or rewriting its encrypted credentials. */
    suspend fun updateStoredAccount(original: StoredAccount, updated: StoredAccount) {
        updateStoredAccountInternal(
            original = original,
            updated = updated,
            certificatePassword = getRememberedCertificatePassword(original)
        )
    }

    /** Updates certificate metadata and its encrypted password as one saved-account operation. */
    suspend fun updateStoredAccountCertificate(
        original: StoredAccount,
        updated: StoredAccount,
        certificatePassword: String?
    ) {
        updateStoredAccountInternal(original, updated, certificatePassword)
    }

    private suspend fun updateStoredAccountInternal(
        original: StoredAccount,
        updated: StoredAccount,
        certificatePassword: String?
    ) {
        validateStoredAccount(updated)
        val oldKey = original.credentialStorageKey()
        val newKey = updated.credentialStorageKey()
        val rememberedSecret = getRememberedCredential(original)
        context.sessionDataStore.edit { prefs ->
            val list = decodeAccounts(prefs).toMutableList()
            val originalIndex = list.indexOfFirst { it.hasSameIdentity(original) }
            require(originalIndex >= 0) { "保存的账户不存在" }
            list.removeAll { it.hasSameIdentity(updated) }
            val insertAt = originalIndex.coerceAtMost(list.size)
            list.add(insertAt, updated.copy(lastUsedAtEpochMs = original.lastUsedAtEpochMs))
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list.take(MAX_SAVED_ACCOUNTS))
            val current = sessionState.value
            if (current?.toStoredAccount()?.hasSameIdentity(original) == true && oldKey == newKey) {
                prefs.putOrRemove(KEY_ALIAS, updated.alias)
            }
        }
        if (oldKey != newKey) {
            withContext(Dispatchers.IO) {
                secureCredentialStore.writeAccountSecret(newKey, rememberedSecret)
                secureCredentialStore.writeAccountCertificatePassword(
                    newKey,
                    certificatePassword.takeIf { updated.certPath != null }
                )
                secureCredentialStore.removeAccountSecrets(listOf(oldKey))
            }
        } else {
            withContext(Dispatchers.IO) {
                secureCredentialStore.writeAccountCertificatePassword(
                    newKey,
                    certificatePassword.takeIf { updated.certPath != null }
                )
            }
        }
        sessionState.update { current ->
            if (current?.toStoredAccount()?.hasSameIdentity(original) == true && oldKey == newKey) {
                current.copy(alias = updated.alias)
            } else {
                current
            }
        }
    }

    suspend fun moveStoredAccount(account: StoredAccount, newIndex: Int) {
        context.sessionDataStore.edit { prefs ->
            val list = decodeAccounts(prefs).toMutableList()
            val oldIndex = list.indexOfFirst { it.hasSameIdentity(account) }
            if (oldIndex < 0) return@edit
            val item = list.removeAt(oldIndex)
            list.add(newIndex.coerceIn(0, list.size), item)
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
        }
    }

    /** Removes exactly one account identity, including all remembered encrypted credentials. */
    suspend fun removeStoredAccount(account: StoredAccount): Boolean {
        var removed = false
        var remainingAccounts: List<StoredAccount> = emptyList()
        context.sessionDataStore.edit { prefs ->
            val list = decodeAccounts(prefs).toMutableList()
            removed = list.removeAll { it.hasSameIdentity(account) }
            remainingAccounts = list.toList()
            prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(list)
        }
        if (removed) {
            removeRememberedCredentials(listOf(account))
            deleteUnreferencedCertificateFiles(account, remainingAccounts)
        }
        return removed
    }

    /** Deletes replaced certificate files only after no saved or active account references them. */
    suspend fun cleanupUnreferencedCertificateFiles(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val remaining = decodeAccounts(context.sessionDataStore.data.first())
        deleteUnreferencedCertificateFiles(paths, remaining)
    }

    /** Commits a successfully authenticated saved account as the active session. */
    suspend fun activateStoredAccount(
        account: StoredAccount,
        rememberedSecret: String,
        certificatePassword: String?,
        token: String
    ) {
        val previous = getSession()
        val activated = account.copy(lastUsedAtEpochMs = System.currentTimeMillis())
        writeSecure(
            SecureCredentialStore.Credentials(
                token = token,
                password = rememberedSecret,
                certificatePassword = certificatePassword
            )
        )
        writeRememberedCredential(activated, rememberedSecret)
        writeRememberedCertificatePassword(activated, certificatePassword)
        var evictedAccounts: List<StoredAccount> = emptyList()
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_HOST] = activated.host
            prefs[KEY_USERNAME] = activated.username
            prefs.putOrRemove(KEY_ALIAS, activated.alias)
            prefs[KEY_REMEMBER] = true
            prefs[KEY_ALLOW_INSECURE_HTTP] = activated.allowInsecureHttp
            prefs[KEY_AUTH_MODE] = activated.authMode.name
            prefs.putOrRemove(KEY_CERT_PATH, activated.certPath)
            prefs.putOrRemove(KEY_CUSTOM_CA_PATH, activated.customCaPath)
            removeLegacyCredentials(prefs)
            evictedAccounts = updateHistoryInPrefs(prefs, activated)
        }
        removeRememberedCredentials(evictedAccounts)
        sessionState.value = SessionSnapshot(
            host = activated.host,
            username = activated.username,
            password = rememberedSecret,
            token = token,
            alias = activated.alias,
            rememberPassword = true,
            certPath = activated.certPath,
            certPassword = certificatePassword,
            customCaPath = activated.customCaPath,
            allowInsecureHttp = activated.allowInsecureHttp,
            authMode = activated.authMode,
            connectionRevision = previous.connectionRevision + 1L
        )
    }

    suspend fun getRememberedCertificatePassword(account: StoredAccount): String? =
        withContext(Dispatchers.IO) {
            secureCredentialStore.readAccountCertificatePassword(account.credentialStorageKey())
        }

    suspend fun isCurrentAccount(account: StoredAccount): Boolean =
        getSession().toStoredAccount()?.hasSameIdentity(account) == true

    /** Returns only a user-approved remembered secret; active session tokens are never exposed. */
    suspend fun getRememberedCredential(account: StoredAccount): String? =
        withContext(Dispatchers.IO) {
            secureCredentialStore.readAccountSecret(account.credentialStorageKey())
        }

    private suspend fun snapshotFromPreferences(prefs: Preferences): SessionSnapshot {
        migrateLegacyCredentials(prefs)
        val secure = withContext(Dispatchers.IO) { secureCredentialStore.read() }
        return SessionSnapshot(
            host = prefs[KEY_HOST],
            username = prefs[KEY_USERNAME],
            password = secure.password,
            token = secure.token,
            alias = prefs[KEY_ALIAS],
            rememberPassword = prefs[KEY_REMEMBER] ?: false,
            certPath = prefs[KEY_CERT_PATH],
            certPassword = secure.certificatePassword,
            customCaPath = prefs[KEY_CUSTOM_CA_PATH],
            allowInsecureHttp = prefs[KEY_ALLOW_INSECURE_HTTP] ?: false,
            authMode = prefs[KEY_AUTH_MODE]
                ?.let { runCatching { AuthMode.valueOf(it) }.getOrNull() }
                ?: AuthMode.PASSWORD
        )
    }

    private suspend fun migrateLegacyCredentials(prefs: Preferences) {
        migrationMutex.withLock {
            if (!secureCredentialStore.isMigrated()) {
                withContext(Dispatchers.IO) {
                    secureCredentialStore.write(
                        SecureCredentialStore.Credentials(
                            token = prefs[KEY_TOKEN],
                            password = prefs[KEY_PASSWORD],
                            certificatePassword = prefs[KEY_CERT_PASSWORD]
                        )
                    )
                }
            }
            if (
                prefs[KEY_TOKEN] != null ||
                prefs[KEY_PASSWORD] != null ||
                prefs[KEY_CERT_PASSWORD] != null
            ) {
                context.sessionDataStore.edit(::removeLegacyCredentials)
            }
        }
    }

    private suspend fun writeSecure(credentials: SecureCredentialStore.Credentials) {
        withContext(Dispatchers.IO) { secureCredentialStore.write(credentials) }
    }

    private suspend fun writeRememberedCredential(account: StoredAccount, secret: String?) {
        withContext(Dispatchers.IO) {
            secureCredentialStore.writeAccountSecret(account.credentialStorageKey(), secret)
        }
    }

    private suspend fun writeRememberedCertificatePassword(
        account: StoredAccount,
        password: String?
    ) {
        withContext(Dispatchers.IO) {
            secureCredentialStore.writeAccountCertificatePassword(
                account.credentialStorageKey(),
                password.takeIf { account.certPath != null }
            )
        }
    }

    private suspend fun removeRememberedCredentials(accounts: Collection<StoredAccount>) {
        if (accounts.isEmpty()) return
        withContext(Dispatchers.IO) {
            secureCredentialStore.removeAccountSecrets(accounts.map(StoredAccount::credentialStorageKey))
        }
    }

    private suspend fun migrateCurrentRememberedCredential(snapshot: SessionSnapshot) {
        val secret = snapshot.password ?: return
        if (!snapshot.rememberPassword) return
        val host = snapshot.host ?: return
        val username = snapshot.username ?: return
        val account = StoredAccount(
            host = host,
            username = username,
            alias = snapshot.alias,
            allowInsecureHttp = snapshot.allowInsecureHttp,
            authMode = snapshot.authMode
        )
        withContext(Dispatchers.IO) {
            val accountKey = account.credentialStorageKey()
            if (!secureCredentialStore.hasAccountSecret(accountKey)) {
                secureCredentialStore.writeAccountSecret(accountKey, secret)
            }
        }
    }

    private fun SessionSnapshot.toSecureCredentials() = SecureCredentialStore.Credentials(
        token = token,
        password = password,
        certificatePassword = certPassword
    )

    private fun removeLegacyCredentials(prefs: MutablePreferences) {
        prefs.remove(KEY_TOKEN)
        prefs.remove(KEY_PASSWORD)
        prefs.remove(KEY_CERT_PASSWORD)
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun updateHistoryInPrefs(
        prefs: MutablePreferences,
        account: StoredAccount
    ): List<StoredAccount> {
        val raw = prefs[KEY_ACCOUNTS_JSON] ?: "[]"
        val list = runCatching {
            json.decodeFromString<MutableList<StoredAccount>>(raw)
        }.getOrDefault(mutableListOf())
        list.removeAll { it.hasSameIdentity(account) }
        list.add(0, account)
        val retained = list.take(MAX_SAVED_ACCOUNTS)
        prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(retained)
        return list.drop(MAX_SAVED_ACCOUNTS)
    }

    /** Enriches account records written by older versions with current TLS metadata. */
    private suspend fun migrateCurrentAccountMetadata(snapshot: SessionSnapshot) {
        val host = snapshot.host ?: return
        val username = snapshot.username ?: return
        val current = StoredAccount(
            host = host,
            username = username,
            alias = snapshot.alias,
            allowInsecureHttp = snapshot.allowInsecureHttp,
            authMode = snapshot.authMode,
            certPath = snapshot.certPath,
            customCaPath = snapshot.customCaPath
        )
        context.sessionDataStore.edit { prefs ->
            val accounts = decodeAccounts(prefs).toMutableList()
            val index = accounts.indexOfFirst { it.hasSameIdentity(current) }
            if (index >= 0) {
                val previous = accounts[index]
                accounts[index] = current.copy(
                    lastUsedAtEpochMs = previous.lastUsedAtEpochMs.takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                )
                prefs[KEY_ACCOUNTS_JSON] = json.encodeToString(accounts)
            }
        }
        writeRememberedCertificatePassword(current, snapshot.certPassword)
    }

    private fun decodeAccounts(prefs: Preferences): List<StoredAccount> {
        val raw = prefs[KEY_ACCOUNTS_JSON] ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredAccount>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun validateStoredAccount(account: StoredAccount) {
        val host = account.host.trim()
        require(host.startsWith("https://", true) || host.startsWith("http://", true)) {
            "服务器地址必须以 http:// 或 https:// 开头"
        }
        require(account.username.isNotBlank()) { "用户名或 Client ID 不能为空" }
        require(!host.startsWith("http://", true) || account.allowInsecureHttp) {
            "HTTP 连接必须明确允许不安全传输"
        }
    }

    private suspend fun deleteUnreferencedCertificateFiles(
        removed: StoredAccount,
        remaining: List<StoredAccount>
    ) = deleteUnreferencedCertificateFiles(
        listOfNotNull(removed.certPath, removed.customCaPath),
        remaining
    )

    private suspend fun deleteUnreferencedCertificateFiles(
        candidates: Collection<String>,
        remaining: List<StoredAccount>
    ) = withContext(Dispatchers.IO) {
        val referenced = remaining.flatMap { listOfNotNull(it.certPath, it.customCaPath) }.toSet() +
            listOfNotNull(sessionState.value?.certPath, sessionState.value?.customCaPath)
        val certificateRoot = File(context.filesDir, "cert").canonicalFile
        candidates
            .distinct()
            .filterNot(referenced::contains)
            .forEach { path ->
                val target = File(path).canonicalFile
                if (target.parentFile == certificateRoot) target.delete()
            }
    }

}

@Serializable
data class StoredAccount(
    val host: String,
    val username: String,
    val alias: String? = null,
    val allowInsecureHttp: Boolean = false,
    val authMode: AuthMode = AuthMode.PASSWORD,
    val certPath: String? = null,
    val customCaPath: String? = null,
    val lastUsedAtEpochMs: Long = 0L
)

private const val MAX_SAVED_ACCOUNTS = 20

internal fun StoredAccount.hasSameIdentity(other: StoredAccount): Boolean =
    normalizedHost() == other.normalizedHost() &&
        username == other.username &&
        authMode == other.authMode

internal fun StoredAccount.credentialStorageKey(): String {
    val identity = "${normalizedHost()}\u0000$username\u0000${authMode.name}"
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun StoredAccount.normalizedHost(): String =
    normalizeAccountHost(host)

private fun SessionSnapshot.toStoredAccount(): StoredAccount? {
    val resolvedHost = host ?: return null
    val resolvedUsername = username ?: return null
    return StoredAccount(
        host = resolvedHost,
        username = resolvedUsername,
        alias = alias,
        allowInsecureHttp = allowInsecureHttp,
        authMode = authMode,
        certPath = certPath,
        customCaPath = customCaPath
    )
}

/** Stable opaque identifier for UI lists; contains no host, username, or credential text. */
fun StoredAccount.historyId(): String = credentialStorageKey()

/** Stable account identity used by MCP Agent allow-lists. */
fun StoredAccount.mcpAccountId(): String {
    val identity = "${normalizedHost()}\u0000${username.trim()}\u0000${authMode.name}"
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
    )
}

internal fun StoredAccount.dataScopeId(): String {
    val identity = "${normalizedHost()}\u0000${username.trim().lowercase(Locale.ROOT)}\u0000${authMode.name}"
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun normalizeAccountHost(host: String): String =
    host.trim().trimEnd('/').lowercase(Locale.ROOT)
