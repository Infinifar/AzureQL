package com.autopanel.app.notification

internal data class QingLongPushPayload(
    val eventId: String,
    val serverId: String,
    val serverName: String,
    val title: String,
    val body: String
) {
    companion object {
        private val SAFE_ID = Regex("[A-Za-z0-9._:-]{1,128}")

        fun from(data: Map<String, String>): QingLongPushPayload? {
            if (data["schema"] != "1") return null
            val eventId = data["eventId"]?.takeIf(SAFE_ID::matches) ?: return null
            val serverId = data["serverId"]?.takeIf(SAFE_ID::matches) ?: return null
            val title = data["title"]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 160 }
                ?: return null
            val body = data["body"]?.trim().orEmpty()
            if (body.length > 4_096) return null
            val serverName = data["serverName"]
                ?.replace(Regex("[\\r\\n\\t]"), " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(80)
                ?: "QingLong"
            return QingLongPushPayload(eventId, serverId, serverName, title, body)
        }
    }
}
