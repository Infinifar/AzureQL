package com.qinglong.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfigFile(
    val title: String? = null,
    val name: String? = null,
    @SerialName("isDir") val isDir: Boolean? = null
)

object DependencyType {
    const val NODEJS = "nodejs"
    const val PYTHON = "python3"
    const val LINUX = "linux"

    fun fromCode(code: Int): String = when (code) {
        0 -> NODEJS
        1 -> PYTHON
        else -> LINUX
    }

    fun toCode(type: String): Int = when (type) {
        NODEJS -> 0
        PYTHON -> 1
        else -> 2
    }
}

object DependencyStatus {
    const val INSTALLING = 0
    const val INSTALLED = 1
    const val INSTALL_FAILED = 2
    const val UNINSTALLING = 3
    const val UNINSTALL_FAILED = 5

    fun toText(code: Int): String = when (code) {
        INSTALLING -> "安装中"
        INSTALLED -> "已安装"
        INSTALL_FAILED -> "安装失败"
        UNINSTALLING -> "卸载中"
        UNINSTALL_FAILED -> "卸载失败"
        else -> "未知"
    }
}

@Serializable
data class DependencyInfo(
    @Serializable(with = ObjectIdSerializer::class)
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val type: Int? = null,
    val status: Int? = null,
    val remark: String? = null,
    @SerialName("createdAt") val createdAt: String? = null
) {
    val statusText: String get() = DependencyStatus.toText(status ?: -1)
    val typeText: String get() = DependencyType.fromCode(type ?: -1)
}

@Serializable
data class DependencyCreateRequest(
    val name: String,
    val type: String
)

/** 旧版订阅模型（v15 之前），_id 为 MongoDB ObjectId */
@Serializable
data class SubscribeInfo(
    @Serializable(with = ObjectIdSerializer::class)
    @SerialName("_id") val id: String? = null,
    val name: String? = null,
    val url: String? = null,
    val type: Int? = null,
    val schedule: String? = null,
    val status: Int? = null,
    @SerialName("whitelist") val whitelist: String? = null,
    @SerialName("blacklist") val blacklist: String? = null
)
