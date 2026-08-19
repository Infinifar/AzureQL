package com.autopanel.core.data.security

import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 管理 mTLS 客户端证书（.p12 / .pfx）。
 * 青龙面板常见自签名/私有 CA 证书，需要同时信任服务器证书并提供客户端证书。
 */
@Singleton
class ClientCertificateManager @Inject constructor() {

    /** 信任所有服务器证书（青龙常见自签名证书） */
    val trustAllManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * 从证书文件 + 密码构建含客户端证书的 SSLSocketFactory。
     * 加载失败（密码错误、文件损坏）返回 null。
     */
    fun createSslSocketFactory(certPath: String, password: String): SSLSocketFactory? {
        return try {
            val keyStore = KeyStore.getInstance("PKCS12")
            File(certPath).inputStream().use { keyStore.load(it, password.toCharArray()) }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password.toCharArray())
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, arrayOf<TrustManager>(trustAllManager), SecureRandom())
            sslContext.socketFactory
        } catch (_: Exception) {
            null
        }
    }
}
