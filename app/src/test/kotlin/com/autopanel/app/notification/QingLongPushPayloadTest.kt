package com.autopanel.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QingLongPushPayloadTest {

    @Test
    fun `accepts bounded versioned relay payload`() {
        val payload = QingLongPushPayload.from(
            mapOf(
                "schema" to "1",
                "eventId" to "evt-123",
                "serverId" to "server:abc",
                "serverName" to "Home\nserver",
                "title" to "Task completed",
                "body" to "The task completed successfully"
            )
        )

        assertEquals("Home server", payload?.serverName)
        assertEquals("Task completed", payload?.title)
    }

    @Test
    fun `rejects unknown schema unsafe identifiers and oversized content`() {
        val valid = mapOf(
            "schema" to "1",
            "eventId" to "evt-123",
            "serverId" to "server:abc",
            "title" to "Title",
            "body" to "Body"
        )

        assertNull(QingLongPushPayload.from(valid + ("schema" to "2")))
        assertNull(QingLongPushPayload.from(valid + ("eventId" to "bad/id")))
        assertNull(QingLongPushPayload.from(valid + ("title" to "x".repeat(161))))
        assertNull(QingLongPushPayload.from(valid + ("body" to "x".repeat(4_097))))
    }
}
