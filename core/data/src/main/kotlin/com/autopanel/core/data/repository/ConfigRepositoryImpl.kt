package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.model.DependencyCacheType
import com.autopanel.core.model.DependencyMirrorEvent
import com.autopanel.core.model.DependencySetting
import com.autopanel.core.model.SystemConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>,
    private val retrofitClient: AutoPanelRetrofitClient,
    private val sessionManager: SessionManager,
    private val json: Json
) : ConfigRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getConfigContent(name: String): Result<String> {
        return try {
            val res = api.getConfigContent(name)
            if (res.code == 200) Result.success(res.data ?: "")
            else Result.failure(Exception(res.message ?: "获取配置内容失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun saveConfig(name: String, content: String): Result<Unit> {
        return try {
            val res = api.saveConfig(mapOf("name" to name, "content" to content))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "保存配置失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getSystemConfig(): Result<SystemConfig> {
        return try {
            val res = api.getSystemConfig()
            if (res.code == 200) {
                val configData = res.data
                val info = configData?.info
                if (info != null) Result.success(info)
                else Result.failure(Exception(res.message ?: "系统配置为空"))
            } else {
                Result.failure(Exception(res.message ?: "获取系统配置失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateSystemConfig(config: SystemConfig): Result<Unit> {
        return try {
            config.logRemoveFrequency?.let {
                val res = api.updateLogRemoveFrequency(mapOf("logRemoveFrequency" to it))
                if (res.code != 200) return Result.failure(Exception(res.message ?: "更新日志频率失败"))
            }
            config.cronConcurrency?.let {
                val res = api.updateCronConcurrency(mapOf("cronConcurrency" to it))
                if (res.code != 200) return Result.failure(Exception(res.message ?: "更新并发数失败"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateDependencySetting(
        setting: DependencySetting,
        value: String
    ): Result<Unit> {
        return try {
            when (setting) {
                DependencySetting.PROXY -> {
                    val response = api.updateDependenceProxy(mapOf("dependenceProxy" to value))
                    if (response.code != 200) {
                        return Result.failure(Exception(response.message ?: "更新依赖代理失败"))
                    }
                }
                DependencySetting.NODE_MIRROR -> {
                    val response = api.updateNodeMirror(mapOf("nodeMirror" to value))
                    response.body()?.close()
                    response.errorBody()?.close()
                    if (!response.isSuccessful) {
                        return Result.failure(
                            Exception("更新 Node.js 镜像失败（HTTP ${response.code()}）")
                        )
                    }
                }
                DependencySetting.PYTHON_MIRROR -> {
                    val response = api.updatePythonMirror(mapOf("pythonMirror" to value))
                    if (response.code != 200) {
                        return Result.failure(Exception(response.message ?: "更新 Python 镜像失败"))
                    }
                }
                DependencySetting.LINUX_MIRROR -> {
                    val response = api.updateLinuxMirror(mapOf("linuxMirror" to value))
                    response.body()?.close()
                    response.errorBody()?.close()
                    if (!response.isSuccessful) {
                        return Result.failure(
                            Exception("更新 Linux 镜像失败（HTTP ${response.code()}）")
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override fun observeDependencyMirrorTasks(): Flow<DependencyMirrorEvent> = callbackFlow {
        val session = sessionManager.getSession()
        val host = session.host
        val token = session.token
        if (host == null || token == null) {
            trySend(DependencyMirrorEvent.ConnectionError("未登录，无法连接依赖任务状态"))
            close()
            return@callbackFlow
        }
        if (host.startsWith("http://", true) && !session.allowInsecureHttp) {
            trySend(DependencyMirrorEvent.ConnectionError("当前服务器未授权不安全 WebSocket"))
            close()
            return@callbackFlow
        }

        val socketHttpUrl = "${host.trimEnd('/')}/api/ws/websocket".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("token", token)
            ?.build()
        if (socketHttpUrl == null) {
            trySend(DependencyMirrorEvent.ConnectionError("服务器 WebSocket 地址无效"))
            close()
            return@callbackFlow
        }

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                decodeSockJsMessages(text).forEach { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(
                    DependencyMirrorEvent.ConnectionError(
                        "依赖任务实时连接中断，仍可查看各项 HTTP 提交结果"
                    )
                )
                this@callbackFlow.close(IOException("Dependency mirror WebSocket failed", t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@callbackFlow.close()
            }
        }
        val socketUrl = socketHttpUrl.toString().replaceFirst(
            if (socketHttpUrl.isHttps) "https://" else "http://",
            if (socketHttpUrl.isHttps) "wss://" else "ws://"
        )
        val webSocket = retrofitClient.createCurrentWebSocket(
            Request.Builder().url(socketUrl).build(),
            listener
        )
        awaitClose { webSocket.cancel() }
    }

    private fun decodeSockJsMessages(frame: String): List<DependencyMirrorEvent.Task> {
        val payloads = when {
            frame.startsWith("a[") -> runCatching {
                json.parseToJsonElement(frame.drop(1)).jsonArray.map { it.jsonPrimitive.content }
            }.getOrDefault(emptyList())
            frame.startsWith("{") -> listOf(frame)
            else -> emptyList()
        }
        return payloads.mapNotNull { payload ->
            val message = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
                ?: return@mapNotNull null
            val setting = when (message["type"]?.jsonPrimitive?.contentOrNull) {
                "updateNodeMirror" -> DependencySetting.NODE_MIRROR
                "updateLinuxMirror" -> DependencySetting.LINUX_MIRROR
                else -> return@mapNotNull null
            }
            DependencyMirrorEvent.Task(
                setting = setting,
                message = message["message"]?.jsonPrimitive?.contentOrNull,
                status = message["status"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    override suspend fun cleanDependencyCache(type: DependencyCacheType): Result<Unit> {
        return try {
            val response = api.cleanDependence(mapOf("type" to type.apiValue))
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception(response.message ?: "清理依赖缓存失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
