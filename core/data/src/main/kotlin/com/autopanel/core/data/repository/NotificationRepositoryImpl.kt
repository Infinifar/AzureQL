package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>
) : NotificationRepository {
    private val api: AutoPanelApiService get() = apiProvider.get()

    override suspend fun getConfig(): Result<JsonObject> = try {
        val response = api.getNotificationConfig()
        if (response.code == 200) Result.success(response.data ?: JsonObject(emptyMap()))
        else Result.failure(Exception(response.message ?: "获取通知设置失败"))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    override suspend fun testAndSave(config: JsonObject): Result<Unit> = try {
        val response = api.updateNotificationConfig(config)
        if (response.code == 200) Result.success(Unit)
        else Result.failure(Exception(response.message ?: "测试通知失败，设置未保存"))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(
            if (e is HttpException) Exception(classifyNotificationHttpFailure(e)) else e
        )
    }

    private fun classifyNotificationHttpFailure(error: HttpException): String {
        val rawMessage = runCatching {
            val raw = error.response()?.errorBody()?.use { it.string().take(ERROR_BODY_LIMIT) }.orEmpty()
            val body = Json.parseToJsonElement(raw).jsonObject
            (body["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.getOrDefault("")
        return when {
            rawMessage.contains("ENOTFOUND", ignoreCase = true) ||
                rawMessage.contains("getaddrinfo", ignoreCase = true) ->
                "通知服务地址无法解析，请检查服务地址和青龙容器网络"
            rawMessage.contains("ECONNREFUSED", ignoreCase = true) ->
                "通知服务拒绝连接，请检查服务地址、端口和网络访问"
            rawMessage.contains("ETIMEDOUT", ignoreCase = true) ||
                rawMessage.contains("timeout", ignoreCase = true) ->
                "通知服务连接超时，请检查青龙容器网络"
            rawMessage.contains("401") || rawMessage.contains("403") ||
                rawMessage.contains("unauthorized", ignoreCase = true) ->
                "通知服务认证失败，请检查令牌或密钥"
            rawMessage.contains("404") ->
                "通知服务地址返回 404，请检查服务地址是否正确"
            else -> "青龙服务端测试通知失败（HTTP ${error.code()}），设置未保存；请检查通知渠道配置、青龙容器网络和系统日志"
        }
    }

    private companion object {
        const val ERROR_BODY_LIMIT = 8 * 1024
    }
}
