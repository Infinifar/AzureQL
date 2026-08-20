package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.SubscriptionRepository
import com.autopanel.core.model.ApiResponse
import com.autopanel.core.model.SubscriptionDraft
import com.autopanel.core.model.SubscriptionInfo
import com.autopanel.core.model.SubscriptionLogChunk
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>
) : SubscriptionRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getSubscriptions(): Result<List<SubscriptionInfo>> = apiCall {
        val response = api.getSubscriptions()
        if (response.code == 200) Result.success(response.data.orEmpty())
        else Result.failure(Exception(response.message ?: "获取订阅列表失败"))
    }

    override suspend fun addSubscription(draft: SubscriptionDraft): Result<Unit> = apiCall {
        api.addSubscription(draft.toRequest(includeId = false)).toUnitResult("创建订阅失败")
    }

    override suspend fun updateSubscription(draft: SubscriptionDraft): Result<Unit> = apiCall {
        requireNotNull(draft.id) { "订阅 ID 不能为空" }
        api.updateSubscription(draft.toRequest(includeId = true)).toUnitResult("更新订阅失败")
    }

    override suspend fun deleteSubscription(id: Int): Result<Unit> = apiCall {
        api.deleteSubscriptions(listOf(id)).toUnitResult("删除订阅失败")
    }

    override suspend fun runSubscription(id: Int): Result<Unit> = apiCall {
        api.runSubscriptions(listOf(id)).toUnitResult("运行订阅失败")
    }

    override suspend fun stopSubscription(id: Int): Result<Unit> = apiCall {
        api.stopSubscriptions(listOf(id)).toUnitResult("停止订阅失败")
    }

    override suspend fun setSubscriptionEnabled(id: Int, enabled: Boolean): Result<Unit> = apiCall {
        val response = if (enabled) {
            api.enableSubscriptions(listOf(id))
        } else {
            api.disableSubscriptions(listOf(id))
        }
        response.toUnitResult(if (enabled) "启用订阅失败" else "禁用订阅失败")
    }

    override suspend fun getSubscriptionLog(
        id: Int,
        offset: Long?,
        limit: Int,
        tail: Boolean
    ): Result<SubscriptionLogChunk> = apiCall {
        val response = api.getSubscriptionLog(
            id = id,
            offset = offset,
            limit = limit.coerceIn(1, 1024 * 1024),
            tail = tail
        )
        if (response.code == 200) {
            Result.success(
                SubscriptionLogChunk(
                    content = response.content ?: response.data.orEmpty(),
                    offset = response.offset,
                    nextOffset = response.nextOffset,
                    total = response.total,
                    truncated = response.truncated
                )
            )
        } else {
            Result.failure(Exception(response.message ?: "获取订阅日志失败"))
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }
}

private fun SubscriptionDraft.toRequest(includeId: Boolean) = buildJsonObject {
    if (includeId) put("id", requireNotNull(id))
    put("name", name)
    put("type", type)
    put("url", url)
    put("schedule_type", scheduleType)
    put("alias", alias)
    if (scheduleType == "interval") {
        put("interval_schedule", buildJsonObject {
            put("type", intervalType)
            put("value", intervalValue)
        })
    } else {
        put("schedule", schedule)
    }
    put("branch", branch)
    put("whitelist", whitelist)
    put("blacklist", blacklist)
    put("dependences", dependences)
    put("extensions", extensions)
    put("sub_before", subBefore)
    put("sub_after", subAfter)
    put("proxy", proxy)
    put("autoAddCron", autoAddCron)
    put("autoDelCron", autoDelCron)
    pullType?.let { put("pull_type", it) }
    pullOption?.let { put("pull_option", it) }
}

private fun ApiResponse<JsonElement>.toUnitResult(fallback: String): Result<Unit> =
    if (code == 200) Result.success(Unit)
    else Result.failure(Exception(message ?: fallback))
