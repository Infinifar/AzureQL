package com.autopanel.feature.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProviderRegistryTest {
    @Test
    fun `same provider preserves unknown server fields but applies edited values`() {
        val original = JsonObject(mapOf(
            "type" to JsonPrimitive("gotify"),
            "gotifyUrl" to JsonPrimitive("https://old"),
            "futureOption" to JsonPrimitive("keep-me")
        ))

        val payload = buildNotificationPayload(
            original = original,
            originalType = "gotify",
            selectedType = "gotify",
            values = mapOf("gotifyUrl" to "https://new", "gotifyToken" to "secret")
        )

        assertEquals(JsonPrimitive("https://new"), payload["gotifyUrl"])
        assertEquals(JsonPrimitive("keep-me"), payload["futureOption"])
    }

    @Test
    fun `changing provider does not leak fields from previous provider`() {
        val original = JsonObject(mapOf(
            "type" to JsonPrimitive("gotify"),
            "gotifyToken" to JsonPrimitive("old-secret"),
            "futureOption" to JsonPrimitive("old")
        ))

        val payload = buildNotificationPayload(
            original = original,
            originalType = "gotify",
            selectedType = "serverChan",
            values = mapOf("serverChanKey" to "new-key")
        )

        assertEquals(JsonPrimitive("serverChan"), payload["type"])
        assertTrue("serverChanKey" in payload)
        assertFalse("gotifyToken" in payload)
        assertFalse("futureOption" in payload)
    }

    @Test
    fun `feishu response uses lark schema`() {
        assertEquals("lark", NotificationProviderRegistry.find("feishu")?.type)
    }
}
