package com.autopanel.core.domain

import com.autopanel.core.model.SubscriptionDraft
import com.autopanel.core.model.SubscriptionInfo
import com.autopanel.core.model.SubscriptionLogChunk

interface SubscriptionRepository {
    suspend fun getSubscriptions(): Result<List<SubscriptionInfo>>
    suspend fun addSubscription(draft: SubscriptionDraft): Result<Unit>
    suspend fun updateSubscription(draft: SubscriptionDraft): Result<Unit>
    suspend fun deleteSubscription(id: Int): Result<Unit>
    suspend fun runSubscription(id: Int): Result<Unit>
    suspend fun stopSubscription(id: Int): Result<Unit>
    suspend fun setSubscriptionEnabled(id: Int, enabled: Boolean): Result<Unit>
    suspend fun getSubscriptionLog(
        id: Int,
        offset: Long? = null,
        limit: Int = 65_536,
        tail: Boolean = false
    ): Result<SubscriptionLogChunk>
}
