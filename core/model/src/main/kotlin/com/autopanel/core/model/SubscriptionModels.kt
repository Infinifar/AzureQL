package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SubscriptionIntervalSchedule(
    val type: String = "days",
    val value: Int = 1
)

@Serializable
data class SubscriptionLogResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: String? = null,
    val content: String? = null,
    val offset: Long = 0,
    @SerialName("nextOffset") val nextOffset: Long = 0,
    val total: Long = 0,
    val truncated: Boolean = false
)

data class SubscriptionLogChunk(
    val content: String,
    val offset: Long,
    val nextOffset: Long,
    val total: Long,
    val truncated: Boolean
)

/**
 * 订阅（青龙 v2.17+ SQLite 后端，主键为数字 id）
 */
@Serializable
data class SubscriptionInfo(
    val id: Int? = null,
    val name: String? = null,
    val type: String? = null,
    val url: String? = null,
    val schedule: String? = null,
    @SerialName("is_disabled") val isDisabled: Int? = null,
    val status: Int? = null,
    val pid: Int? = null,
    val alias: String? = null,
    val whitelist: String? = null,
    val blacklist: String? = null,
    val extensions: String? = null,
    val branch: String? = null,
    @SerialName("schedule_type") val scheduleType: String? = null,
    @SerialName("interval_schedule") val intervalSchedule: SubscriptionIntervalSchedule? = null,
    @SerialName("autoAddCron") val autoAddCron: Int? = null,
    @SerialName("autoDelCron") val autoDelCron: Int? = null,
    @SerialName("log_path") val logPath: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    val dependences: String? = null,
    @SerialName("sub_before") val subBefore: String? = null,
    @SerialName("sub_after") val subAfter: String? = null,
    @SerialName("pull_type") val pullType: String? = null,
    @SerialName("pull_option") val pullOption: JsonElement? = null,
    val proxy: String? = null
) {
    val disabled: Boolean get() = isDisabled == 1
}

/** Editable subscription fields aligned with QingLong 2.21's subscription API. */
data class SubscriptionDraft(
    val id: Int? = null,
    val name: String = "",
    val type: String = "public-repo",
    val url: String = "",
    val scheduleType: String = "crontab",
    val schedule: String = "",
    val intervalType: String = "days",
    val intervalValue: Int = 1,
    val branch: String = "",
    val alias: String = "",
    val whitelist: String = "",
    val blacklist: String = "",
    val dependences: String = "",
    val extensions: String = "",
    val subBefore: String = "",
    val subAfter: String = "",
    val proxy: String = "",
    val autoAddCron: Boolean = true,
    val autoDelCron: Boolean = true,
    val pullType: String = "ssh-key",
    val privateKey: String = "",
    val username: String = "",
    val password: String = ""
)

fun SubscriptionInfo.toDraft(): SubscriptionDraft = SubscriptionDraft(
    id = id,
    name = name.orEmpty(),
    type = type ?: "public-repo",
    url = url.orEmpty(),
    scheduleType = scheduleType ?: "crontab",
    schedule = schedule.orEmpty(),
    intervalType = intervalSchedule?.type ?: "days",
    intervalValue = intervalSchedule?.value ?: 1,
    branch = branch.orEmpty(),
    alias = alias.orEmpty(),
    whitelist = whitelist.orEmpty(),
    blacklist = blacklist.orEmpty(),
    dependences = dependences.orEmpty(),
    extensions = extensions.orEmpty(),
    subBefore = subBefore.orEmpty(),
    subAfter = subAfter.orEmpty(),
    proxy = proxy.orEmpty(),
    autoAddCron = autoAddCron != 0,
    autoDelCron = autoDelCron != 0,
    pullType = pullType ?: "ssh-key",
    privateKey = pullOption.stringValue("private_key"),
    username = pullOption.stringValue("username"),
    password = pullOption.stringValue("password")
)

private fun JsonElement?.stringValue(key: String): String =
    (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
