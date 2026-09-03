package com.autopanel.core.data.remote

import com.autopanel.core.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves API services through [ApiClientRegistry]. Access tokens are read by the shared
 * authentication interceptor and therefore never participate in the connection cache key.
 */
@Singleton
class AutoPanelRetrofitClient @Inject internal constructor(
    private val sessionManager: SessionManager,
    private val registry: ApiClientRegistry
) {
    /**
     * Synchronous accessor used by repository providers after application startup has warmed the
     * current profile. Login uses [createApiService], which always builds on [Dispatchers.IO].
     */
    val apiService: AutoPanelApiService
        get() {
            val session = sessionManager.currentSession
            return registry.getOrCreate(session.host ?: error("Host not set"), session).apiService
        }

    /** Loads the persisted session once and prepares its network stack away from the main thread. */
    suspend fun prepareCurrent(): AutoPanelApiService? = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession()
        val host = session.host ?: return@withContext null
        registry.getOrCreate(host, session).apiService
    }

    /** Creates or reuses the profile selected on the login screen. */
    suspend fun createApiService(host: String): AutoPanelApiService = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession()
        registry.getOrCreate(host, session).apiService
    }

    suspend fun createCurrentWebSocket(
        request: Request,
        listener: WebSocketListener
    ): WebSocket = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession()
        val host = session.host ?: error("Host not set")
        registry.getOrCreate(host, session).okHttpClient.newWebSocket(request, listener)
    }

    fun invalidateHost(host: String) {
        registry.invalidateHost(host)
    }
}
