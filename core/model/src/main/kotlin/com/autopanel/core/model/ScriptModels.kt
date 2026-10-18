package com.autopanel.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScriptFile(
    val title: String? = null,
    val key: String? = null,
    val type: String? = null,          // "file" | "directory"
    val parent: String? = null,
    @SerialName("isLeaf") val isLeaf: Boolean? = null,
    @SerialName("isDir") val isDir: Boolean? = null,
    val children: List<ScriptFile>? = null,
    val size: Long? = null,
    @SerialName("mtime") val mtime: Double? = null,
    @SerialName("createTime") val createTime: Long? = null
) {
    val isDirectory: Boolean get() = type == "directory" || isDir == true || isLeaf == false
}

@Serializable
data class ScriptUpdateRequest(
    val filename: String,
    val path: String = "",
    val content: String,
    val skipHistory: Boolean? = null,
    val expectedHash: String? = null
)

@Serializable
data class ScriptMutationResult(
    val content: String = "",
    val changed: Boolean = false,
    val historyRecorded: Boolean? = null,
    val cleanupPending: Boolean = false
)

@Serializable
data class ScriptHistoryVersion(
    val id: String,
    val createdAt: String,
    val source: String,
    val hash: String,
    val size: Long? = null,
    val identical: Boolean? = null,
    val content: String? = null
)

@Serializable
data class ScriptHistoryList(
    val versions: List<ScriptHistoryVersion> = emptyList(),
    val currentHash: String,
    val limit: Int = 20
)

@Serializable
data class ScriptHistoryDetail(
    val version: ScriptHistoryVersion,
    val current: String,
    val currentHash: String
)

@Serializable
data class ScriptHistoryRestoreRequest(
    val filename: String,
    val path: String = "",
    val id: String,
    val expectedHash: String
)

@Serializable
data class ScriptDeleteRequest(
    val filename: String,
    val path: String = "",
    val type: String = "file"   // "file" or "directory"
)

@Serializable
data class ScriptAddRequest(
    val filename: String,
    val path: String = "",
    val content: String = ""
)

@Serializable
data class ScriptDirectoryRequest(
    val filename: String,
    val path: String = "",
    val directory: String
)
