package com.autopanel.core.data.di

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.data.security.ClientCertificateManager
import com.autopanel.core.data.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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
                val token = sessionManager.currentSession.token
                val requestBuilder = original.newBuilder()
                    .removeHeader(AutoPanelApiService.LONG_RUNNING_HEADER)
                    .removeHeader(AutoPanelApiService.NO_AUTH_HEADER)
                if (
                    token != null &&
                    original.header(AutoPanelApiService.NO_AUTH_HEADER) != "true"
                ) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                val request = requestBuilder.build()
                val scopedChain = if (
                    original.header(AutoPanelApiService.LONG_RUNNING_HEADER) == "true"
                ) {
                    chain
                        .withReadTimeout(24, TimeUnit.HOURS)
                        .withWriteTimeout(24, TimeUnit.HOURS)
                } else {
                    chain
                }
                scopedChain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAutoPanelRetrofitClient(
        okHttpClient: OkHttpClient,
        json: Json,
        sessionManager: SessionManager,
        certificateManager: ClientCertificateManager
    ): AutoPanelRetrofitClient {
        return AutoPanelRetrofitClient(okHttpClient, json, sessionManager, certificateManager)
    }

    @Provides
    fun provideAutoPanelApiService(client: AutoPanelRetrofitClient): AutoPanelApiService {
        return client.apiService
    }
}
