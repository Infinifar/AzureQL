package com.autopanel.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LoginModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `LoginRequest serialization`() {
        val request = LoginRequest("admin", "secret")
        val encoded = json.encodeToString(LoginRequest.serializer(), request)
        assertTrue(encoded.contains("admin"))

        val decoded = json.decodeFromString<LoginRequest>(encoded)
        assertEquals("admin", decoded.username)
        assertEquals("secret", decoded.password)
    }

    @Test
    fun `LoginData deserialization`() {
        val raw = """{"token":"abc123","lastip":"1.2.3.4","lastaddr":"Beijing"}"""
        val data = json.decodeFromString<LoginData>(raw)
        assertEquals("abc123", data.token)
        assertEquals("1.2.3.4", data.lastIp)
        assertEquals("Beijing", data.lastAddr)
    }

    @Test
    fun `LoginData with camelCase serial names`() {
        val raw = """{"token":"tok","lastip":"10.0.0.1","lastaddr":"Home","lastlogon":1700000000}"""
        val data = json.decodeFromString<LoginData>(raw)
        assertEquals("tok", data.token)
        assertEquals("10.0.0.1", data.lastIp)
        assertEquals(1700000000L, data.lastLogon)
    }

    @Test
    fun `LoginResult sealed class equality`() {
        val s1 = LoginResult.Success(LoginData(token = "t"))
        val s2 = LoginResult.Success(LoginData(token = "t"))
        assertEquals(s1, s2)

        val e1 = LoginResult.Error("msg")
        val e2 = LoginResult.Error("msg")
        assertEquals(e1, e2)
    }

    @Test
    fun `ApiResponse with data`() {
        val raw = """{"code":200,"message":"ok","data":{"token":"x"}}"""
        val response = json.decodeFromString<ApiResponse<LoginData>>(raw)
        assertEquals(200, response.code)
        assertEquals("x", response.data?.token)
    }
}
