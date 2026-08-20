package com.autopanel.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `flexible data accepts server path string`() {
        val raw = """{"code":200,"data":"data/db/andata/database.sqlite"}"""

        val response = json.decodeFromString<ApiResponse<JsonElement>>(raw)

        assertEquals(200, response.code)
        assertEquals(
            "data/db/andata/database.sqlite",
            (response.data as JsonPrimitive).content
        )
    }

    @Test
    fun `flexible data accepts array and missing value`() {
        val arrayResponse = json.decodeFromString<ApiResponse<JsonElement>>(
            """{"code":200,"data":[{"id":69}]}"""
        )
        val missingResponse = json.decodeFromString<ApiResponse<JsonElement>>("""{"code":200}""")

        assertTrue(arrayResponse.data is JsonArray)
        assertNull(missingResponse.data)
    }

    @Test
    fun `dependency mutation response accepts returned dependency array`() {
        val raw = """{"code":200,"data":[{"id":69,"name":"tesseract-ocr","type":2,"status":6}]}"""

        val response = json.decodeFromString<ApiResponse<List<DependencyInfo>>>(raw)

        assertEquals(69, response.data?.single()?.id)
        assertEquals("tesseract-ocr", response.data?.single()?.name)
        assertEquals(DependencyStatus.QUEUED, response.data?.single()?.status)
    }
}
