package com.qinglong.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionInfo(
    @Serializable(with = ObjectIdSerializer::class)
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val url: String? = null,
    val type: String? = null,
    val branch: String? = null,
    val status: Int? = null,
    @SerialName("isDisabled") val isDisabled: Int? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null
)
