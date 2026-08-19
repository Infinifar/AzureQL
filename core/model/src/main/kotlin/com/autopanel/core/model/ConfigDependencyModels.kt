package com.autopanel.core.model

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
    const val UNINSTALLED = 4
    const val UNINSTALL_FAILED = 5
    const val QUEUED = 6
    const val CANCELLED = 7

    fun toText(code: Int): String = when (code) {
        INSTALLING -> "安装中"
        INSTALLED -> "已安装"
        INSTALL_FAILED -> "安装失败"
        UNINSTALLING -> "卸载中"
        UNINSTALLED -> "已卸载"
        UNINSTALL_FAILED -> "卸载失败"
        QUEUED -> "队列中"
        CANCELLED -> "已取消"
        else -> "未知"
    }
}

/**
 * 依赖项（青龙 v2.17+ SQLite 后端，主键为数字 id）
 */
@Serializable
data class DependencyInfo(
    val id: Int? = null,
    val name: String? = null,
    val type: Int? = null,
    val status: Int? = null,
    val log: List<String>? = null,
    val remark: String? = null,
    val timestamp: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null
) {
    val statusText: String get() = DependencyStatus.toText(status ?: -1)
    val typeText: String get() = DependencyType.fromCode(type ?: -1)
}

@Serializable
data class DependencyCreateRequest(
    val name: String,
    val type: Int
)

/** 依赖更新请求体（PUT /api/dependencies） */
@Serializable
data class DependencyUpdateRequest(
    val id: Int,
    val name: String,
    val type: Int,
    val remark: String? = null
)
