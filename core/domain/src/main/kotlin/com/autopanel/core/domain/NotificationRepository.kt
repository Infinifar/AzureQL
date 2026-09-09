package com.autopanel.core.domain

import kotlinx.serialization.json.JsonObject

interface NotificationRepository {
    suspend fun getConfig(): Result<JsonObject>
    suspend fun testAndSave(config: JsonObject): Result<Unit>
}
