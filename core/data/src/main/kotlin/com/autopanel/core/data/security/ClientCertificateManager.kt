package com.autopanel.core.data.security

import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 管理 mTLS 客户端证书（.p12 / .pfx）。
 * 只加载客户端身份，服务端证书仍由系统信任库与主机名校验器验证。
 */
@Singleton
class ClientCertificateManager @Inject constructor() {

    data class ClientSslConfig(
        val socketFactory: SSLSocketFactory,
        val trustManager: X509TrustManager
    )

    /**
     * 从证书文件 + 密码构建含客户端证书的 SSLSocketFactory。
     * 加载失败（密码错误、文件损坏）返回 null。
     */
    fun createSslConfig(certPath: String, password: String): ClientSslConfig? {
        return try {
            val keyStore = KeyStore.getInstance("PKCS12")
            File(certPath).inputStream().use { keyStore.load(it, password.toCharArray()) }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password.toCharArray())

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val trustManager = tmf.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
                ?: return null

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, arrayOf<TrustManager>(trustManager), SecureRandom())
            ClientSslConfig(sslContext.socketFactory, trustManager)
        } catch (_: Exception) {
            null
        }
    }
}
