package com.autopanel.feature.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
}

@Singleton
internal class AndroidWebDavSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) : WebDavSettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    private val state = MutableStateFlow(readSettings())

    override val settings: Flow<WebDavSettings> = state

    override suspend fun save(
        serverUrl: String,
        username: String,
        remoteDirectory: String,
        password: String?,
        isVerified: Boolean
    ) {
        synchronized(lock) {
            val normalizedUrl = serverUrl.trim().trimEnd('/')
            val normalizedUsername = username.trim()
            val normalizedDirectory = remoteDirectory.trim().trim('/')
            val editor = preferences.edit()
                .putString(KEY_SERVER_URL, normalizedUrl)
                .putString(KEY_USERNAME, normalizedUsername)
                .putString(KEY_REMOTE_DIRECTORY, normalizedDirectory)
                .putBoolean(KEY_CONFIGURED, isVerified)
            if (password != null) {
                if (password.isEmpty()) editor.remove(KEY_PASSWORD)
                else editor.putString(KEY_PASSWORD, encrypt(password))
            }
            check(editor.commit()) { "无法保存 WebDAV 设置" }
            state.value = readSettings()
        }
    }

    override suspend fun loadConnection(requireVerified: Boolean): WebDavConnection? = synchronized(lock) {
        val settings = readSettings()
        if ((requireVerified && !settings.isConfigured) || settings.serverUrl.isBlank()) null else WebDavConnection(
            serverUrl = settings.serverUrl,
            username = settings.username,
            password = decrypt(preferences.getString(KEY_PASSWORD, null)).orEmpty(),
            remoteDirectory = settings.remoteDirectory
        )
    }

    private fun readSettings() = WebDavSettings(
        serverUrl = preferences.getString(KEY_SERVER_URL, "").orEmpty(),
        username = preferences.getString(KEY_USERNAME, "").orEmpty(),
        remoteDirectory = preferences.getString(KEY_REMOTE_DIRECTORY, DEFAULT_WEBDAV_DIRECTORY)
            .orEmpty()
            .ifBlank { DEFAULT_WEBDAV_DIRECTORY },
        hasSavedPassword = preferences.contains(KEY_PASSWORD),
        isConfigured = preferences.getBoolean(KEY_CONFIGURED, false)
    )

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
