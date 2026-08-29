package com.autopanel.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Android Keystore-backed storage for values that must never be persisted as plaintext. */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    data class Credentials(
        val token: String? = null,
        val password: String? = null,
        val certificatePassword: String? = null
    )

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    @Synchronized
    fun read(): Credentials = Credentials(
        token = decrypt(preferences.getString(KEY_TOKEN, null)),
        password = decrypt(preferences.getString(KEY_PASSWORD, null)),
        certificatePassword = decrypt(preferences.getString(KEY_CERTIFICATE_PASSWORD, null))
    )

    @Synchronized
    fun write(credentials: Credentials, markMigrated: Boolean = true) {
        val editor = preferences.edit()
        editor.putEncrypted(KEY_TOKEN, credentials.token)
        editor.putEncrypted(KEY_PASSWORD, credentials.password)
        editor.putEncrypted(KEY_CERTIFICATE_PASSWORD, credentials.certificatePassword)
        if (markMigrated) editor.putBoolean(KEY_MIGRATED, true)
        check(editor.commit()) { "无法写入安全凭据" }
    }

    /** Reads the remembered password or client secret for one saved account. */
    @Synchronized
    fun readAccountSecret(accountKey: String): String? {
        require(accountKey.isNotBlank()) { "账户凭据键不能为空" }
        return decrypt(preferences.getString(accountSecretKey(accountKey), null))
    }

    /** Stores one account secret independently from the active session credentials. */
    @Synchronized
    fun writeAccountSecret(accountKey: String, secret: String?) {
        require(accountKey.isNotBlank()) { "账户凭据键不能为空" }
        val editor = preferences.edit()
        editor.putEncrypted(accountSecretKey(accountKey), secret)
        check(editor.commit()) { "无法写入账户安全凭据" }
    }

    /** Removes remembered secrets whose saved-account records were deleted or evicted. */
    @Synchronized
    fun removeAccountSecrets(accountKeys: Collection<String>) {
        if (accountKeys.isEmpty()) return
        val editor = preferences.edit()
        accountKeys.filter(String::isNotBlank).distinct().forEach { accountKey ->
            editor.remove(accountSecretKey(accountKey))
        }
        check(editor.commit()) { "无法删除账户安全凭据" }
    }

    fun isMigrated(): Boolean = preferences.getBoolean(KEY_MIGRATED, false)

    @Synchronized
    fun clear() {
        check(preferences.edit().clear().commit()) { "无法清除安全凭据" }
    }

    private fun android.content.SharedPreferences.Editor.putEncrypted(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, encrypt(value))
    }

    private fun accountSecretKey(accountKey: String) = "$KEY_ACCOUNT_SECRET_PREFIX$accountKey"

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
        const val PREFERENCES_NAME = "ql_secure_credentials"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "autopanel.session.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
        const val KEY_MIGRATED = "migrated"
        const val KEY_TOKEN = "token"
        const val KEY_PASSWORD = "password"
        const val KEY_CERTIFICATE_PASSWORD = "certificate_password"
        const val KEY_ACCOUNT_SECRET_PREFIX = "account_secret_"
    }
}
