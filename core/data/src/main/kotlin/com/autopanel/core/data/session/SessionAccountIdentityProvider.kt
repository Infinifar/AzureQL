package com.autopanel.core.data.session

import com.autopanel.core.domain.ActiveAccountIdentity
import com.autopanel.core.domain.ActiveAccountIdentityProvider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
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
        val rawIdentity = "$host\u0000$username\u0000${session.authMode.name}"
        val stableId = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(rawIdentity.toByteArray(StandardCharsets.UTF_8))
        )
        return ActiveAccountIdentity(
            stableId = stableId,
            displayName = session.alias?.takeIf(String::isNotBlank)
                ?: username.takeIf(String::isNotBlank)
                ?: host
        )
    }
}
