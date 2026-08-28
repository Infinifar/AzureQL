package com.autopanel.core.data.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "response_cache",
    primaryKeys = ["scope", "cacheKey"],
    indices = [Index(value = ["updatedAtMillis"])]
)
internal data class CacheEntry(
    val scope: String,
    val cacheKey: String,
    val encryptedPayload: ByteArray,
    val updatedAtMillis: Long
)
