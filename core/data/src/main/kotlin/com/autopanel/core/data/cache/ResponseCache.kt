package com.autopanel.core.data.cache

import com.autopanel.core.data.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseCache @Inject internal constructor(
    private val dao: CacheEntryDao,
    private val sessionManager: SessionManager,
    private val json: Json,
    private val cipher: CachePayloadCipher
) {
    internal suspend fun <T> read(cacheKey: String, deserializer: DeserializationStrategy<T>): T? {
        var resolvedScope: String? = null
        return try {
            val scope = currentScope() ?: return null
            resolvedScope = scope
            val entry = dao.find(scope, cacheKey) ?: return null
            if (entry.updatedAtMillis < retentionCutoff()) {
                dao.delete(scope, cacheKey)
                return null
            }
            val plainText = withContext(Dispatchers.IO) {
                cipher.decrypt(entry.encryptedPayload)
            } ?: run {
                dao.delete(scope, cacheKey)
                return null
            }
            withContext(Dispatchers.Default) {
                json.decodeFromString(deserializer, plainText)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            resolvedScope?.let { scope ->
                try {
                    dao.delete(scope, cacheKey)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A corrupt cache entry must never make the fallback network request fail.
                }
            }
            null
        }
    }

    internal suspend fun <T> write(
        cacheKey: String,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        try {
            val scope = currentScope() ?: return
            val plainText = withContext(Dispatchers.Default) {
                json.encodeToString(serializer, value)
            }
            val payloadSize = withContext(Dispatchers.Default) {
                plainText.toByteArray(Charsets.UTF_8).size
            }
            if (payloadSize > MAX_ENTRY_BYTES) return
            val encrypted = withContext(Dispatchers.IO) { cipher.encrypt(plainText) }
            dao.upsert(
                CacheEntry(
                    scope = scope,
                    cacheKey = cacheKey,
                    encryptedPayload = encrypted,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A cache write must never turn a successful network request into a screen error.
        }
    }

    internal suspend fun invalidate(prefix: String) {
        try {
            val scope = currentScope() ?: return
            dao.deleteByPrefix(scope, prefix)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Invalidating stale data is best effort and must not fail a successful mutation.
        }
    }

    internal suspend fun prune(nowMillis: Long = System.currentTimeMillis()): Int {
        val expired = dao.deleteOlderThan(retentionCutoff(nowMillis))
        val overflow = dao.trimToNewest(MAX_ENTRIES)
        return expired + overflow
    }

    private suspend fun currentScope(): String? {
        val session = sessionManager.getSession()
        val host = session.host?.trim()?.trimEnd('/')?.lowercase()?.takeIf(String::isNotEmpty)
            ?: return null
        val username = session.username?.trim()?.lowercase().orEmpty()
        return hash("$host\u0000$username\u0000${session.authMode.name}")
    }

    private fun retentionCutoff(nowMillis: Long = System.currentTimeMillis()): Long =
        nowMillis - RETENTION_MILLIS

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    internal companion object {
        val RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(8)
        const val MAX_ENTRY_BYTES = 1024 * 1024
        const val MAX_ENTRIES = 256

        const val DASHBOARD_OVERVIEW = "dashboard:overview"
        const val DASHBOARD_SYSTEM = "dashboard:system"
        const val DASHBOARD_TREND_PREFIX = "dashboard:trend:"
        const val DASHBOARD_RUNTIME = "dashboard:runtime"
        const val DASHBOARD_TOP_COUNT = "dashboard:top-count"
        const val DASHBOARD_TOP_TIME = "dashboard:top-time"
        const val TASKS_PREFIX = "tasks:"
        const val SCRIPTS = "scripts:list"

        fun taskPageKey(search: String, page: Int, size: Int): String {
            val queryHash = MessageDigest.getInstance("SHA-256")
                .digest(search.trim().toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
            return "$TASKS_PREFIX$queryHash:$page:$size"
        }
    }
}
