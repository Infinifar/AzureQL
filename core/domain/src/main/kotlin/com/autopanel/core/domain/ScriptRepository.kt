package com.autopanel.core.domain

import com.autopanel.core.model.ScriptFile

data class ScriptDraft(
    val cacheToken: String,
    val filename: String,
    val path: String,
    val sourceKey: String,
    val sizeBytes: Long,
    val characterCount: Long,
    val pageCount: Int,
    val hasUtf8Bom: Boolean,
    val isUtf8Valid: Boolean,
    val editorUri: String,
    val sourceSizeBytes: Long?,
    val sourceModifiedTime: Double?,
    val originalSha256: String
)

data class ScriptDraftPage(
    val index: Int,
    val totalPages: Int,
    val content: String
)

data class ScriptServerVersion(
    val sizeBytes: Long?,
    val modifiedTime: Double?
)

enum class ScriptDraftUploadResult { SAVED, CONFLICT, PENDING_UPLOAD }

interface ScriptRepository {
    suspend fun getCachedScripts(): List<ScriptFile>?
    suspend fun getScripts(): Result<List<ScriptFile>>
    suspend fun getScriptContent(filename: String, path: String): Result<String>
    suspend fun prepareDraft(script: ScriptFile): Result<ScriptDraft>
    suspend fun refreshDraft(draft: ScriptDraft): Result<ScriptDraft>
    suspend fun readDraftPage(draft: ScriptDraft, pageIndex: Int): Result<ScriptDraftPage>
    suspend fun readDraftText(draft: ScriptDraft, maxBytes: Long): Result<String>
    suspend fun replaceDraftText(
        draft: ScriptDraft,
        content: String,
        preserveUtf8Bom: Boolean
    ): Result<ScriptDraft>
    suspend fun hasDraftChanges(draft: ScriptDraft): Result<Boolean>
    suspend fun snapshotDraft(draft: ScriptDraft): Result<Long>
    suspend fun restoreDraftSnapshot(draft: ScriptDraft): Result<Unit>
    suspend fun uploadDraft(
        draft: ScriptDraft,
        force: Boolean = false
    ): Result<ScriptDraftUploadResult>
    suspend fun discardDraft(draft: ScriptDraft)
    suspend fun exportScript(filename: String, path: String, destinationUri: String): Result<Unit>
    suspend fun addScript(filename: String, path: String, content: String): Result<Unit>
    suspend fun updateScript(filename: String, path: String, content: String): Result<Unit>
    suspend fun deleteScript(filename: String, path: String, isDir: Boolean): Result<Unit>
}
