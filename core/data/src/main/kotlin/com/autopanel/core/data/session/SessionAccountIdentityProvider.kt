package com.autopanel.core.data.session

import com.autopanel.core.domain.ActiveAccountIdentity
import com.autopanel.core.domain.ActiveAccountIdentityProvider
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionAccountIdentityProvider @Inject constructor(
    private val sessionManager: SessionManager
) : ActiveAccountIdentityProvider {
    override suspend fun current(): ActiveAccountIdentity? {
        val session = sessionManager.getSession()
        val host = session.host?.trim()?.trimEnd('/')?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank) ?: return null
        val username = session.username?.trim().orEmpty()
        if (session.token.isNullOrBlank()) return null
        val stableId = StoredAccount(
            host = host,
            username = username,
            authMode = session.authMode
        ).mcpAccountId()
        return ActiveAccountIdentity(
            stableId = stableId,
            displayName = session.alias?.takeIf(String::isNotBlank)
                ?: username.takeIf(String::isNotBlank)
                ?: host
        )
    }
}
