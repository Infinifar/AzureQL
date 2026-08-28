package com.autopanel.core.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface CacheEntryDao {
    @Query("SELECT * FROM response_cache WHERE scope = :scope AND cacheKey = :cacheKey LIMIT 1")
    suspend fun find(scope: String, cacheKey: String): CacheEntry?

    @Upsert
    suspend fun upsert(entry: CacheEntry)

    @Query("DELETE FROM response_cache WHERE scope = :scope AND cacheKey = :cacheKey")
    suspend fun delete(scope: String, cacheKey: String)

    @Query("DELETE FROM response_cache WHERE scope = :scope AND cacheKey LIKE :prefix || '%'")
    suspend fun deleteByPrefix(scope: String, prefix: String)

    @Query("DELETE FROM response_cache WHERE updatedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query(
        """
        DELETE FROM response_cache
        WHERE rowid IN (
            SELECT rowid FROM response_cache
            ORDER BY updatedAtMillis DESC
            LIMIT -1 OFFSET :maxEntries
        )
        """
    )
    suspend fun trimToNewest(maxEntries: Int): Int
}
