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

internal data class S3Settings(
    val accountScopeId: String = "",
    val endpoint: String = "",
    val bucket: String = "",
    val region: String = DEFAULT_S3_REGION,
    val pathStyle: Boolean = true,
    val remoteDirectory: String = DEFAULT_S3_DIRECTORY,
    val hasSavedAccessKey: Boolean = false,
    val hasSavedSecretKey: Boolean = false,
    val isConfigured: Boolean = false
)

internal data class S3Connection(
    val endpoint: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val bucket: String,
    val region: String,
    val pathStyle: Boolean,
    val remoteDirectory: String
)

internal interface S3SettingsStore {
    val settings: Flow<S3Settings>

    suspend fun save(
        endpoint: String,
        accessKeyId: String?,
        secretAccessKey: String?,
        bucket: String,
        region: String,
        pathStyle: Boolean,
        remoteDirectory: String,
        isVerified: Boolean = false
    )

    suspend fun loadConnection(requireVerified: Boolean = true): S3Connection?
    suspend fun clearForAccount(account: StoredAccount) = Unit
}

@Singleton
internal class AndroidS3SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionManager: SessionManager
) : S3SettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    private val revision = MutableStateFlow(0L)
    private val lock = Any()

    override val settings: Flow<S3Settings> = combine(
        sessionManager.activeAccountFlow.filterNotNull(),
        revision
    ) { account, _ -> synchronized(lock) { readSettings(account) } }

    override suspend fun save(
        endpoint: String,
        accessKeyId: String?,
        secretAccessKey: String?,
        bucket: String,
        region: String,
        pathStyle: Boolean,
        remoteDirectory: String,
        isVerified: Boolean
    ) {
        val account = currentAccount()
        synchronized(lock) {
            val scope = prepareScope(account)
            val editor = preferences.edit()
                .putString(scoped(scope, KEY_ENDPOINT), endpoint.trim().trimEnd('/'))
                .putString(scoped(scope, KEY_BUCKET), bucket.trim())
                .putString(scoped(scope, KEY_REGION), region.trim().ifBlank { DEFAULT_S3_REGION })
                .putBoolean(scoped(scope, KEY_PATH_STYLE), pathStyle)
                .putString(scoped(scope, KEY_REMOTE_DIRECTORY), remoteDirectory.trim().trim('/'))
                .putBoolean(scoped(scope, KEY_CONFIGURED), isVerified)
            if (accessKeyId != null) {
                if (accessKeyId.isEmpty()) editor.remove(scoped(scope, KEY_ACCESS_KEY_ID))
                else editor.putString(scoped(scope, KEY_ACCESS_KEY_ID), encrypt(accessKeyId))
            }
            if (secretAccessKey != null) {
                if (secretAccessKey.isEmpty()) editor.remove(scoped(scope, KEY_SECRET_ACCESS_KEY))
                else editor.putString(scoped(scope, KEY_SECRET_ACCESS_KEY), encrypt(secretAccessKey))
            }
            check(editor.commit()) { "无法保存 S3 设置" }
            revision.value += 1L
        }
    }

    override suspend fun loadConnection(requireVerified: Boolean): S3Connection? {
        val account = currentAccount()
        return synchronized(lock) {
            val scope = prepareScope(account)
            val settings = readSettingsFromScope(scope)
            val accessKeyId = decrypt(preferences.getString(scoped(scope, KEY_ACCESS_KEY_ID), null)).orEmpty()
            val secretAccessKey = decrypt(preferences.getString(scoped(scope, KEY_SECRET_ACCESS_KEY), null)).orEmpty()
            if ((requireVerified && !settings.isConfigured) ||
                settings.endpoint.isBlank() || accessKeyId.isBlank() || secretAccessKey.isBlank()
            ) {
                null
            } else {
                S3Connection(
                    endpoint = settings.endpoint,
                    accessKeyId = accessKeyId,
                    secretAccessKey = secretAccessKey,
                    bucket = settings.bucket,
                    region = settings.region,
                    pathStyle = settings.pathStyle,
                    remoteDirectory = settings.remoteDirectory
                )
            }
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
            check(editor.commit()) { "无法清除账户 S3 设置" }
            revision.value += 1L
        }
    }

    private suspend fun currentAccount(): StoredAccount =
        sessionManager.activeAccountFlow.filterNotNull().first()

    private fun readSettings(account: StoredAccount): S3Settings {
        val scope = prepareScope(account)
        return readSettingsFromScope(scope)
    }

    private fun readSettingsFromScope(scope: String): S3Settings {
        return S3Settings(
        accountScopeId = scope,
        endpoint = preferences.getString(scoped(scope, KEY_ENDPOINT), "").orEmpty(),
        bucket = preferences.getString(scoped(scope, KEY_BUCKET), "").orEmpty(),
        region = preferences.getString(scoped(scope, KEY_REGION), DEFAULT_S3_REGION).orEmpty()
            .ifBlank { DEFAULT_S3_REGION },
        pathStyle = preferences.getBoolean(scoped(scope, KEY_PATH_STYLE), true),
        remoteDirectory = preferences.getString(scoped(scope, KEY_REMOTE_DIRECTORY), DEFAULT_S3_DIRECTORY)
            .orEmpty()
            .ifBlank { DEFAULT_S3_DIRECTORY },
        hasSavedAccessKey = preferences.contains(scoped(scope, KEY_ACCESS_KEY_ID)),
        hasSavedSecretKey = preferences.contains(scoped(scope, KEY_SECRET_ACCESS_KEY)),
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
        check(editor.commit()) { "无法迁移账户 S3 设置" }
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
        check(editor.commit()) { "无法迁移 S3 设置" }
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

    private companion object {
        const val PREFERENCES_NAME = "azureql_s3_backup"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "azureql.s3.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_ACCESS_KEY_ID = "access_key_id"
        const val KEY_SECRET_ACCESS_KEY = "secret_access_key"
        const val KEY_BUCKET = "bucket"
        const val KEY_REGION = "region"
        const val KEY_PATH_STYLE = "path_style"
        const val KEY_REMOTE_DIRECTORY = "remote_directory"
        const val KEY_CONFIGURED = "configured"
        const val KEY_LEGACY_ASSIGNED = "legacy_assigned_v2"
        val ALL_KEYS = listOf(
            KEY_ENDPOINT,
            KEY_ACCESS_KEY_ID,
            KEY_SECRET_ACCESS_KEY,
            KEY_BUCKET,
            KEY_REGION,
            KEY_PATH_STYLE,
            KEY_REMOTE_DIRECTORY,
            KEY_CONFIGURED
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class S3SettingsModule {
    @Binds
    abstract fun bindS3SettingsStore(implementation: AndroidS3SettingsStore): S3SettingsStore
}

internal const val DEFAULT_S3_REGION = "auto"
internal const val DEFAULT_S3_DIRECTORY = "AzureQL"
