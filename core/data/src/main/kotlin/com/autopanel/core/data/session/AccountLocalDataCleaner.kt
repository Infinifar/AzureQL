package com.autopanel.core.data.session

import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.data.script.ScriptDraftStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

data class AccountCleanupResult(
    val responseCacheEntries: Int,
    val scriptDrafts: Int
)

@Singleton
class AccountLocalDataCleaner @Inject constructor(
    private val responseCache: ResponseCache,
    private val scriptDraftStore: ScriptDraftStore
) {
    suspend fun clear(account: StoredAccount): AccountCleanupResult = coroutineScope {
        val cache = async { responseCache.deleteForAccount(account) }
        val drafts = async { scriptDraftStore.deleteForAccount(account) }
        AccountCleanupResult(cache.await(), drafts.await())
    }
}
