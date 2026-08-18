package com.qinglong.core.data.di

import com.qinglong.core.data.remote.QLApiService
import com.qinglong.core.data.remote.QLRetrofitClient
import com.qinglong.core.data.security.ClientCertificateManager
import com.qinglong.core.data.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(sessionManager: SessionManager): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = sessionManager.token
                val request = if (token != null) {
                    original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @Provides
    @Singleton
    fun provideQLRetrofitClient(
        okHttpClient: OkHttpClient,
        json: Json,
        sessionManager: SessionManager,
        certificateManager: ClientCertificateManager
    ): QLRetrofitClient {
        return QLRetrofitClient(okHttpClient, json, sessionManager, certificateManager)
    }

    @Provides
    @Singleton
    fun provideQLApiService(client: QLRetrofitClient): QLApiService {
        return client.apiService
    }
}
