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

internal data class S3Settings(
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
}

@Singleton
internal class AndroidS3SettingsStore @Inject constructor(
    @ApplicationContext context: Context
) : S3SettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    private val state = MutableStateFlow(readSettings())
    private val lock = Any()

    override val settings: Flow<S3Settings> = state

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
        synchronized(lock) {
            val editor = preferences.edit()
                .putString(KEY_ENDPOINT, endpoint.trim().trimEnd('/'))
                .putString(KEY_BUCKET, bucket.trim())
                .putString(KEY_REGION, region.trim().ifBlank { DEFAULT_S3_REGION })
                .putBoolean(KEY_PATH_STYLE, pathStyle)
                .putString(KEY_REMOTE_DIRECTORY, remoteDirectory.trim().trim('/'))
                .putBoolean(KEY_CONFIGURED, isVerified)
            if (accessKeyId != null) {
                if (accessKeyId.isEmpty()) editor.remove(KEY_ACCESS_KEY_ID)
                else editor.putString(KEY_ACCESS_KEY_ID, encrypt(accessKeyId))
            }
            if (secretAccessKey != null) {
                if (secretAccessKey.isEmpty()) editor.remove(KEY_SECRET_ACCESS_KEY)
                else editor.putString(KEY_SECRET_ACCESS_KEY, encrypt(secretAccessKey))
            }
            check(editor.commit()) { "无法保存 S3 设置" }
            state.value = readSettings()
        }
    }

    override suspend fun loadConnection(requireVerified: Boolean): S3Connection? = synchronized(lock) {
        val settings = readSettings()
        val accessKeyId = decrypt(preferences.getString(KEY_ACCESS_KEY_ID, null)).orEmpty()
        val secretAccessKey = decrypt(preferences.getString(KEY_SECRET_ACCESS_KEY, null)).orEmpty()
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

    private fun readSettings() = S3Settings(
        endpoint = preferences.getString(KEY_ENDPOINT, "").orEmpty(),
        bucket = preferences.getString(KEY_BUCKET, "").orEmpty(),
        region = preferences.getString(KEY_REGION, DEFAULT_S3_REGION).orEmpty()
            .ifBlank { DEFAULT_S3_REGION },
        pathStyle = preferences.getBoolean(KEY_PATH_STYLE, true),
        remoteDirectory = preferences.getString(KEY_REMOTE_DIRECTORY, DEFAULT_S3_DIRECTORY)
            .orEmpty()
            .ifBlank { DEFAULT_S3_DIRECTORY },
        hasSavedAccessKey = preferences.contains(KEY_ACCESS_KEY_ID),
        hasSavedSecretKey = preferences.contains(KEY_SECRET_ACCESS_KEY),
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
