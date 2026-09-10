package com.autopanel.feature.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.StoredAccount
import com.autopanel.core.data.session.historyId
import com.autopanel.core.data.session.networkStorageScopeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal data class WebDavSettings(
    val accountScopeId: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val remoteDirectory: String = DEFAULT_WEBDAV_DIRECTORY,
    val hasSavedPassword: Boolean = false,
    val isConfigured: Boolean = false
)

internal data class WebDavConnection(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remoteDirectory: String
)

internal interface WebDavSettingsStore {
    val settings: Flow<WebDavSettings>

    suspend fun save(
        serverUrl: String,
        username: String,
        remoteDirectory: String,
        password: String?,
        isVerified: Boolean = false
    )

    suspend fun loadConnection(requireVerified: Boolean = true): WebDavConnection?
    suspend fun clearForAccount(account: StoredAccount) = Unit
}

@Singleton
internal class AndroidWebDavSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionManager: SessionManager
) : WebDavSettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    private val revision = MutableStateFlow(0L)

    override val settings: Flow<WebDavSettings> = combine(
        sessionManager.activeAccountFlow.filterNotNull(),
        revision
    ) { account, _ -> synchronized(lock) { readSettings(account) } }

    override suspend fun save(
        serverUrl: String,
        username: String,
        remoteDirectory: String,
        password: String?,
        isVerified: Boolean
    ) {
        val account = currentAccount()
        synchronized(lock) {
            val scope = prepareScope(account)
            val normalizedUrl = serverUrl.trim().trimEnd('/')
            val normalizedUsername = username.trim()
            val normalizedDirectory = remoteDirectory.trim().trim('/')
            val editor = preferences.edit()
                .putString(scoped(scope, KEY_SERVER_URL), normalizedUrl)
                .putString(scoped(scope, KEY_USERNAME), normalizedUsername)
                .putString(scoped(scope, KEY_REMOTE_DIRECTORY), normalizedDirectory)
                .putBoolean(scoped(scope, KEY_CONFIGURED), isVerified)
            if (password != null) {
                if (password.isEmpty()) editor.remove(scoped(scope, KEY_PASSWORD))
                else editor.putString(scoped(scope, KEY_PASSWORD), encrypt(password))
            }
            check(editor.commit()) { "无法保存 WebDAV 设置" }
            revision.value += 1L
        }
    }

    override suspend fun loadConnection(requireVerified: Boolean): WebDavConnection? {
        val account = currentAccount()
        return synchronized(lock) {
            val scope = prepareScope(account)
            val settings = readSettingsFromScope(scope)
            if ((requireVerified && !settings.isConfigured) || settings.serverUrl.isBlank()) null else WebDavConnection(
                serverUrl = settings.serverUrl,
                username = settings.username,
                password = decrypt(preferences.getString(scoped(scope, KEY_PASSWORD), null)).orEmpty(),
                remoteDirectory = settings.remoteDirectory
            )
        }
    }

    override suspend fun clearForAccount(account: StoredAccount) {
        val scopes = setOf(account.networkStorageScopeId(), account.historyId())
        synchronized(lock) {
            val editor = preferences.edit()
            scopes.forEach { scope ->
                ALL_KEYS.forEach { editor.remove(scoped(scope, it)) }
                editor.remove(migrationKey(scope))
            }
            check(editor.commit()) { "无法清除账户 WebDAV 设置" }
            revision.value += 1L
        }
    }

    private suspend fun currentAccount(): StoredAccount =
        sessionManager.activeAccountFlow.filterNotNull().first()

    private fun readSettings(account: StoredAccount): WebDavSettings {
        val scope = prepareScope(account)
        return readSettingsFromScope(scope)
    }

    private fun readSettingsFromScope(scope: String): WebDavSettings {
        return WebDavSettings(
        accountScopeId = scope,
        serverUrl = preferences.getString(scoped(scope, KEY_SERVER_URL), "").orEmpty(),
        username = preferences.getString(scoped(scope, KEY_USERNAME), "").orEmpty(),
        remoteDirectory = preferences.getString(scoped(scope, KEY_REMOTE_DIRECTORY), DEFAULT_WEBDAV_DIRECTORY)
            .orEmpty()
            .ifBlank { DEFAULT_WEBDAV_DIRECTORY },
        hasSavedPassword = preferences.contains(scoped(scope, KEY_PASSWORD)),
        isConfigured = preferences.getBoolean(scoped(scope, KEY_CONFIGURED), false)
        )
    }

    private fun prepareScope(account: StoredAccount): String {
        val scope = account.networkStorageScopeId()
        migrateAccountScope(account.historyId(), scope)
        migrateLegacySettings(scope)
        return scope
    }

    private fun migrateAccountScope(oldScope: String, scope: String) {
        if (oldScope == scope || preferences.getBoolean(scopeMigrationKey(scope), false)) return
        val editor = preferences.edit()
        val targetHasSettings = ALL_KEYS.any { preferences.contains(scoped(scope, it)) }
        if (!targetHasSettings) {
            ALL_KEYS.forEach { key ->
                when (val value = preferences.all[scoped(oldScope, key)]) {
                    is String -> editor.putString(scoped(scope, key), value)
                    is Boolean -> editor.putBoolean(scoped(scope, key), value)
                }
            }
        }
        editor.putBoolean(scopeMigrationKey(scope), true)
        check(editor.commit()) { "无法迁移账户 WebDAV 设置" }
    }

    private fun migrateLegacySettings(scope: String) {
        if (preferences.getBoolean(migrationKey(scope), false)) return
        val editor = preferences.edit()
        if (!preferences.getBoolean(KEY_LEGACY_ASSIGNED, false)) {
            val targetHasSettings = ALL_KEYS.any { preferences.contains(scoped(scope, it)) }
            if (!targetHasSettings) {
                ALL_KEYS.forEach { key ->
                    when (val value = preferences.all[key]) {
                        is String -> editor.putString(scoped(scope, key), value)
                        is Boolean -> editor.putBoolean(scoped(scope, key), value)
                    }
                }
            }
            editor.putBoolean(KEY_LEGACY_ASSIGNED, true)
        }
        editor.putBoolean(migrationKey(scope), true)
        check(editor.commit()) { "无法迁移 WebDAV 设置" }
    }

    private fun scoped(scope: String, key: String) = "account_${scope}_$key"
    private fun migrationKey(scope: String) = "account_${scope}_migrated_v2"
    private fun scopeMigrationKey(scope: String) = "account_${scope}_scope_migrated_v3"

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.getEncoder().encodeToString(cipher.iv)
        val encrypted = Base64.getEncoder().encodeToString(
            cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        )
        return "$FORMAT_VERSION:$iv:$encrypted"
    }

    private fun decrypt(value: String?): String? {
        if (value == null) return null
        return runCatching {
            val parts = value.split(':', limit = 3)
            require(parts.size == 3 && parts[0] == FORMAT_VERSION)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.getDecoder().decode(parts[1]))
            )
            String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private val lock = Any()

    private companion object {
        const val PREFERENCES_NAME = "azureql_webdav_backup"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "azureql.webdav.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_REMOTE_DIRECTORY = "remote_directory"
        const val KEY_PASSWORD = "password"
        const val KEY_CONFIGURED = "configured"
        const val KEY_LEGACY_ASSIGNED = "legacy_assigned_v2"
        val ALL_KEYS = listOf(
            KEY_SERVER_URL,
            KEY_USERNAME,
            KEY_REMOTE_DIRECTORY,
            KEY_PASSWORD,
            KEY_CONFIGURED
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WebDavSettingsModule {
    @Binds
    abstract fun bindWebDavSettingsStore(
        implementation: AndroidWebDavSettingsStore
    ): WebDavSettingsStore
}

internal const val DEFAULT_WEBDAV_DIRECTORY = "AzureQL"
