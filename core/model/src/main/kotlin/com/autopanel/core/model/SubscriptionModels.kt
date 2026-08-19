package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("autoAddCron") val autoAddCron: Int? = null,
    @SerialName("autoDelCron") val autoDelCron: Int? = null,
    @SerialName("log_path") val logPath: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    val dependences: String? = null,
    @SerialName("sub_before") val subBefore: String? = null,
    @SerialName("sub_after") val subAfter: String? = null,
    @SerialName("pull_type") val pullType: String? = null,
    @SerialName("pull_option") val pullOption: String? = null,
    val proxy: String? = null
) {
    val disabled: Boolean get() = isDisabled == 1
}
