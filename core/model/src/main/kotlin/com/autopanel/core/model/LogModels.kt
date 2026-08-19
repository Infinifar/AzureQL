package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginLogEntry(
    val address: String? = null,
    val ip: String? = null,
    val platform: String? = null,
    val status: Int? = null,     // 0=success, 1=failure
    val time: String? = null
) {
    val statusText: String
        get() = if (status == 1) "失败" else "成功"
}

@Serializable
data class LoginLogsResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: List<LoginLogEntry>? = null
)

@Serializable
data class SystemConfig(
    @SerialName("logRemoveFrequency") val logRemoveFrequency: Int? = null,
    @SerialName("cronConcurrency") val cronConcurrency: Int? = null
)

/** system config 响应中 data.info 的结构 */
@Serializable
data class SystemConfigData(
    val info: SystemConfig? = null
)

@Serializable
data class DependenceLogEntry(
    val log: List<String>? = null
)

@Serializable
data class DependenceLogResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: DependenceLogEntry? = null
)

/**
 * 日志目录树节点（GET /api/logs/ 返回）
 * 青龙后端用 readDirs() 返回嵌套的目录树，字段与脚本列表一致：
 * title=文件名/目录名, key=相对路径, type=file|directory, parent=父路径, children=子节点
 */
@Serializable
data class LogFile(
    val title: String? = null,
    val key: String? = null,
    val type: String? = null,        // "file" | "directory"
    val parent: String? = null,
    val children: List<LogFile>? = null,
    val size: Long? = null,
    @SerialName("createTime") val createTime: Long? = null
) {
    val isDirectory: Boolean get() = type == "directory"
}

/** 递归展开日志目录树，返回扁平化的文件列表（只保留 file 类型） */
fun flattenLogFiles(nodes: List<LogFile>?): List<LogFile> {
    if (nodes == null) return emptyList()
    val result = mutableListOf<LogFile>()
    fun walk(list: List<LogFile>) {
        for (node in list) {
            if (node.isDirectory) {
                node.children?.let { walk(it) }
            } else {
                result.add(node)
            }
        }
    }
    walk(nodes)
    return result
}
