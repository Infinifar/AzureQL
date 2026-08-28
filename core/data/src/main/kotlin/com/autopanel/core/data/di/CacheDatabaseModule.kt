package com.autopanel.core.data.di

import android.content.Context
import androidx.room.Room
import com.autopanel.core.data.cache.AzureQlCacheDatabase
import com.autopanel.core.data.cache.CacheEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CacheDatabaseModule {
    @Provides
    @Singleton
    fun provideCacheDatabase(
        @ApplicationContext context: Context
    ): AzureQlCacheDatabase = Room.databaseBuilder(
        context,
        AzureQlCacheDatabase::class.java,
        "azureql-response-cache.db"
    ).build()

    @Provides
    fun provideCacheEntryDao(database: AzureQlCacheDatabase): CacheEntryDao =
        database.cacheEntryDao()
}
