package com.autopanel.core.data.cache

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypts cached API snapshots so task commands and server metadata are never stored as plaintext. */
@Singleton
internal class CachePayloadCipher @Inject constructor() {
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    @Synchronized
    fun encrypt(plainText: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return ByteBuffer.allocate(HEADER_BYTES + cipher.iv.size + encrypted.size)
            .put(FORMAT_VERSION)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
    }

    @Synchronized
    fun decrypt(payload: ByteArray): String? = runCatching {
        val buffer = ByteBuffer.wrap(payload)
        require(buffer.get() == FORMAT_VERSION)
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength in 1..MAX_IV_BYTES && buffer.remaining() > ivLength)
        val iv = ByteArray(ivLength).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

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
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "azureql.response.cache.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val HEADER_BYTES = 2
        const val MAX_IV_BYTES = 32
        const val FORMAT_VERSION: Byte = 1
    }
}
