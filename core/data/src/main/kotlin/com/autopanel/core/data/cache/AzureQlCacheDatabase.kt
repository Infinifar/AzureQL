package com.autopanel.core.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CacheEntry::class],
    version = 1,
    exportSchema = false
)
internal abstract class AzureQlCacheDatabase : RoomDatabase() {
    abstract fun cacheEntryDao(): CacheEntryDao
}
