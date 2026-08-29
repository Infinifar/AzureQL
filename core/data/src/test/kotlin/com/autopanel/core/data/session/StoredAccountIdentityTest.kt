package com.autopanel.core.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredAccountIdentityTest {

    @Test
    fun `credential key normalizes host without exposing account identity`() {
        val first = StoredAccount("HTTPS://Panel.Example.com/", "admin")
        val same = StoredAccount("https://panel.example.com", "admin", alias = "Production")

        val key = first.credentialStorageKey()

        assertEquals(key, same.credentialStorageKey())
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        assertFalse(key.contains("panel", ignoreCase = true))
        assertFalse(key.contains("admin", ignoreCase = true))
        assertTrue(first.hasSameIdentity(same))
    }

    @Test
    fun `username and authentication mode separate remembered credentials`() {
        val passwordAccount = StoredAccount("https://panel.example.com", "admin")
        val otherUser = StoredAccount("https://panel.example.com", "operator")
        val clientAccount = StoredAccount(
            host = "https://panel.example.com",
            username = "admin",
            authMode = AuthMode.CLIENT_CREDENTIALS
        )

        assertNotEquals(passwordAccount.credentialStorageKey(), otherUser.credentialStorageKey())
        assertNotEquals(passwordAccount.credentialStorageKey(), clientAccount.credentialStorageKey())
        assertFalse(passwordAccount.hasSameIdentity(otherUser))
        assertFalse(passwordAccount.hasSameIdentity(clientAccount))
    }
}
