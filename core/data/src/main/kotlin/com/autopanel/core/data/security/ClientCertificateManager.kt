package com.autopanel.core.data.security

import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builds TLS configuration from an optional PKCS#12 client identity and optional private CA.
 * System trust and hostname verification always remain enabled.
 */
@Singleton
class ClientCertificateManager @Inject constructor() {

    data class ClientSslConfig(
        val socketFactory: SSLSocketFactory,
        val trustManager: X509TrustManager
    )

    /**
     * Returns null when no custom TLS material is configured. Invalid files and passwords throw a
     * sanitized [TlsConfigurationException] so callers never silently retry without mTLS.
     */
    fun createSslConfig(
        certPath: String?,
        password: String?,
        customCaPath: String?
    ): ClientSslConfig? {
        if (certPath == null && customCaPath == null) return null

        return try {
            val keyManagers = certPath?.let { loadClientKeyManagers(it, password.orEmpty()) }
            val systemTrustManager = defaultTrustManager()
            val trustManager = customCaPath?.let {
                CompositeTrustManager(systemTrustManager, loadCustomTrustManager(it))
            } ?: systemTrustManager

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagers, arrayOf<TrustManager>(trustManager), SecureRandom())
            ClientSslConfig(sslContext.socketFactory, trustManager)
        } catch (e: Exception) {
            throw TlsConfigurationException(
                if (certPath != null) {
                    "无法加载 mTLS 客户端证书，请检查文件和证书密码"
                } else {
                    "无法加载私有 CA 证书，请检查证书文件"
                },
                e
            )
        }
    }

    private fun loadClientKeyManagers(path: String, password: String): Array<KeyManager> {
        val keyStore = KeyStore.getInstance("PKCS12")
        File(path).inputStream().use { keyStore.load(it, password.toCharArray()) }
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore, password.toCharArray())
        return factory.keyManagers
    }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun loadCustomTrustManager(path: String): X509TrustManager {
        val certificates = File(path).inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificates(it)
        }
        require(certificates.isNotEmpty()) { "CA file contains no certificates" }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        certificates.forEachIndexed { index, certificate ->
            keyStore.setCertificateEntry("private-ca-$index", certificate)
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private class CompositeTrustManager(
        private val system: X509TrustManager,
        private val custom: X509TrustManager
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            system.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (systemFailure: CertificateException) {
                try {
                    custom.checkServerTrusted(chain, authType)
                } catch (customFailure: CertificateException) {
                    customFailure.addSuppressed(systemFailure)
                    throw customFailure
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            system.acceptedIssuers + custom.acceptedIssuers
    }
}

class TlsConfigurationException(message: String, cause: Throwable) : Exception(message, cause)
