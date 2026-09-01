package com.autopanel.core.domain

/** A non-secret, stable identity for the currently selected QingLong account. */
data class ActiveAccountIdentity(
    val stableId: String,
    val displayName: String
)

interface ActiveAccountIdentityProvider {
    suspend fun current(): ActiveAccountIdentity?
}
