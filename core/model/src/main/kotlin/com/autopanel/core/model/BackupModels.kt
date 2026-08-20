package com.autopanel.core.model

import kotlinx.serialization.Serializable

/** 青龙官方数据导出模块；基础数据库与上传文件始终包含。 */
enum class BackupModule(
    val apiValue: String,
    val displayName: String,
    val description: String
) {
    BASE("base", "基础数据", "数据库与上传文件（必选）"),
    CONFIG("config", "配置文件", "config 目录"),
    SCRIPTS("scripts", "脚本文件", "scripts 目录"),
    LOGS("log", "日志文件", "任务运行日志"),
    DEPENDENCIES("deps", "依赖文件", "已安装依赖"),
    SYSTEM_LOGS("syslog", "系统日志", "青龙系统日志"),
    DEPENDENCY_CACHE("dep_cache", "依赖缓存", "Node.js 与 Python 缓存"),
    REMOTE_SCRIPT_CACHE("raw", "远程脚本缓存", "下载的远程脚本"),
    REPOSITORY_CACHE("repo", "远程仓库缓存", "拉取的仓库数据"),
    SSH_CACHE("ssh.d", "SSH 文件缓存", "SSH 相关文件")
}

@Serializable
data class BackupExportRequest(
    val type: List<String>
)

enum class DependencyCacheType(val apiValue: String, val displayName: String) {
    NODE("node", "Node.js 缓存"),
    PYTHON("python3", "Python 缓存")
}
