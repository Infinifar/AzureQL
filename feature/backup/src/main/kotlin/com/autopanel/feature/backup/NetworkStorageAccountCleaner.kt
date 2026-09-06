package com.autopanel.feature.backup

import com.autopanel.core.data.session.StoredAccount
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkStorageAccountCleaner @Inject internal constructor(
    private val webDavSettingsStore: WebDavSettingsStore,
    private val s3SettingsStore: S3SettingsStore
) {
    suspend fun clear(account: StoredAccount) {
        webDavSettingsStore.clearForAccount(account)
        s3SettingsStore.clearForAccount(account)
    }
}
