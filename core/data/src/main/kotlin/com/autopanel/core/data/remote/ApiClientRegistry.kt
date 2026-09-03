package com.autopanel.core.data.remote

import com.autopanel.core.data.performance.performanceTrace
import com.autopanel.core.data.security.ClientCertificateManager
import com.autopanel.core.data.security.TlsConfigurationException
import com.autopanel.core.data.session.SessionSnapshot
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal data class ConnectionProfileKey(
    val baseUrl: String,
    val clientCertFingerprint: String?,
    val caFingerprint: String?,
    val tlsPolicy: TlsPolicy,
    val networkPolicyVersion: Int
)

internal data class TlsPolicy(
    val mtlsEnabled: Boolean,
    val customCaEnabled: Boolean,
    val allowInsecureHttp: Boolean
)

internal data class CachedClient(
    val okHttpClient: OkHttpClient,
    val retrofit: Retrofit,
    val apiService: AutoPanelApiService
)

internal data class ApiClientBuildMetrics(
    val okHttpBuilds: Int = 0,
    val retrofitBuilds: Int = 0,
    val sslContextBuilds: Int = 0,
    val clientCertificateLoads: Int = 0,
    val caLoads: Int = 0
)

/** Resolves certificate material only when the explicit connection generation changes. */
@Singleton
internal class ConnectionProfileKeyFactory @Inject constructor() {
    @Volatile
    private var lastResolved: ResolvedProfile? = null
    private val lock = Any()

    fun create(host: String, session: SessionSnapshot): ConnectionProfileKey {
        val input = ProfileInput(
            baseUrl = normalizeBaseUrl(host),
            certPath = session.certPath,
            certPassword = session.certPassword,
            customCaPath = session.customCaPath,
            allowInsecureHttp = session.allowInsecureHttp,
            connectionRevision = session.connectionRevision
        )
        lastResolved?.takeIf { it.input == input }?.let { return it.key }
        return synchronized(lock) {
            lastResolved?.takeIf { it.input == input }?.key ?: ConnectionProfileKey(
                baseUrl = input.baseUrl,
                clientCertFingerprint = input.certPath?.let { path ->
                    fingerprint(path, input.certPassword, "mTLS 客户端证书")
                },
                caFingerprint = input.customCaPath?.let { path ->
                    fingerprint(path, secret = null, label = "私有 CA 证书")
                },
                tlsPolicy = TlsPolicy(
                    mtlsEnabled = input.certPath != null,
                    customCaEnabled = input.customCaPath != null,
                    allowInsecureHttp = input.allowInsecureHttp
                ),
                networkPolicyVersion = NETWORK_POLICY_VERSION
            ).also { key -> lastResolved = ResolvedProfile(input, key) }
        }
    }

    private fun fingerprint(path: String, secret: String?, label: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            File(path).inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            if (secret != null) {
                digest.update(0.toByte())
                digest.update(secret.toByteArray(Charsets.UTF_8))
            }
            digest.digest().toHex()
        } catch (error: Exception) {
            throw TlsConfigurationException("无法读取$label，请重新选择文件", error)
        }
    }

    private data class ProfileInput(
        val baseUrl: String,
        val certPath: String?,
        val certPassword: String?,
        val customCaPath: String?,
        val allowInsecureHttp: Boolean,
        val connectionRevision: Long
    )

    private data class ResolvedProfile(
        val input: ProfileInput,
        val key: ConnectionProfileKey
    )
}

/**
 * Owns one OkHttp/Retrofit/service triple for each connection profile. Token changes are excluded
 * from [ConnectionProfileKey] and are read by the authentication interceptor at request time.
 */
@Singleton
internal class ApiClientRegistry @Inject constructor(
    private val baseOkHttpClient: OkHttpClient,
    private val json: Json,
    private val certificateManager: ClientCertificateManager,
    private val keyFactory: ConnectionProfileKeyFactory
) {
    private val lock = Any()
    private val clients = LinkedHashMap<ConnectionProfileKey, CachedClient>(4, 0.75f, true)
    private var metrics = ApiClientBuildMetrics()

    fun getOrCreate(host: String, session: SessionSnapshot): CachedClient {
        val key = keyFactory.create(host, session)
        validateHttpPolicy(key)
        return synchronized(lock) {
            clients[key] ?: buildClient(key, session).also { client ->
                clients[key] = client
                while (clients.size > MAX_CACHED_CONNECTIONS) {
                    val eldest = clients.entries.iterator()
                    if (eldest.hasNext()) {
                        eldest.next()
                        eldest.remove()
                    }
                }
            }
        }
    }

    fun invalidateHost(host: String) {
        val baseUrl = normalizeBaseUrl(host)
        synchronized(lock) {
            clients.keys.removeAll { it.baseUrl == baseUrl }
        }
    }

    internal fun buildMetrics(): ApiClientBuildMetrics = synchronized(lock) { metrics }

    private fun buildClient(key: ConnectionProfileKey, session: SessionSnapshot): CachedClient {
        return performanceTrace(TRACE_API_CLIENT_BUILD) {
            val sslConfig = performanceTrace(TRACE_TLS_MATERIAL_LOAD) {
                certificateManager.createSslConfig(
                    certPath = session.certPath,
                    password = session.certPassword,
                    customCaPath = session.customCaPath
                )
            }
            val client = if (sslConfig == null) {
                baseOkHttpClient
            } else {
                baseOkHttpClient.newBuilder()
                    .sslSocketFactory(sslConfig.socketFactory, sslConfig.trustManager)
                    .build()
            }
            val retrofit = Retrofit.Builder()
                .baseUrl(key.baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val service = retrofit.create(AutoPanelApiService::class.java)
            metrics = metrics.copy(
                okHttpBuilds = metrics.okHttpBuilds + 1,
                retrofitBuilds = metrics.retrofitBuilds + 1,
                sslContextBuilds = metrics.sslContextBuilds + if (sslConfig == null) 0 else 1,
                clientCertificateLoads = metrics.clientCertificateLoads + if (session.certPath == null) 0 else 1,
                caLoads = metrics.caLoads + if (session.customCaPath == null) 0 else 1
            )
            CachedClient(client, retrofit, service)
        }
    }

    private fun validateHttpPolicy(key: ConnectionProfileKey) {
        if (key.baseUrl.startsWith("http://", ignoreCase = true) && !key.tlsPolicy.allowInsecureHttp) {
            throw IllegalStateException("当前服务器未授权使用不安全 HTTP")
        }
    }
}

private fun normalizeBaseUrl(host: String): String =
    host.trim().trimEnd('/').let { normalized ->
        require(normalized.startsWith("http://", true) || normalized.startsWith("https://", true)) {
            "服务器地址必须以 http:// 或 https:// 开头"
        }
        "$normalized/"
    }

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}

private const val NETWORK_POLICY_VERSION = 1
private const val MAX_CACHED_CONNECTIONS = 8
private const val TRACE_API_CLIENT_BUILD = "AzureQL:ApiClient.build"
private const val TRACE_TLS_MATERIAL_LOAD = "AzureQL:TLS.material"
