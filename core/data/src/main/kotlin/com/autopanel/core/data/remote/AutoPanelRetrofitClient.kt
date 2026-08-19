package com.autopanel.core.data.remote

import com.autopanel.core.data.security.ClientCertificateManager
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.SessionSnapshot
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit 客户端工厂。
 * [createApiService] 为每次登录/请求按需创建 Retrofit 实例（支持多 Host + mTLS 证书）。
 */
@Singleton
class AutoPanelRetrofitClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val certificateManager: ClientCertificateManager
) {
    /**
     * 获取当前内存会话的 API 服务。调用者每次请求时获取，以免切换服务器后固定旧 Host。
     */
    val apiService: AutoPanelApiService
        get() {
            val session = sessionManager.currentSession
            return buildService(session.host ?: error("Host not set"), session)
        }

    /**
     * 为指定 host 创建 API 服务（登录前使用）。
     */
    fun createApiService(host: String): AutoPanelApiService =
        buildService(host, sessionManager.currentSession)

    private fun buildService(host: String, session: SessionSnapshot): AutoPanelApiService {
        if (host.startsWith("http://", ignoreCase = true) && !session.allowInsecureHttp) {
            throw IllegalStateException("当前服务器未授权使用不安全 HTTP")
        }
        val baseUrl = if (host.endsWith("/")) host else "$host/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildClient(session))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AutoPanelApiService::class.java)
    }

    /** 根据是否配置 mTLS 证书，构建带/不带客户端证书的 OkHttpClient。 */
    private fun buildClient(session: SessionSnapshot): OkHttpClient {
        val sslConfig = certificateManager.createSslConfig(
            certPath = session.certPath,
            password = session.certPassword,
            customCaPath = session.customCaPath
        )
            ?: return okHttpClient
        return okHttpClient.newBuilder()
            .sslSocketFactory(sslConfig.socketFactory, sslConfig.trustManager)
            .build()
    }
}
