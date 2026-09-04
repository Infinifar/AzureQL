package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.data.cache.ResponseCache
import com.autopanel.core.data.script.ScriptDraftStore
import com.autopanel.core.data.script.isServerVersionUnchanged
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptDraftPage
import com.autopanel.core.domain.ScriptDraftUploadResult
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.domain.ScriptServerVersion
import com.autopanel.core.model.ScriptAddRequest
import com.autopanel.core.model.ScriptDeleteRequest
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.ScriptUpdateRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.security.MessageDigest
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ScriptRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>,
    private val responseCache: ResponseCache,
    private val draftStore: ScriptDraftStore
) : ScriptRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getCachedScripts(): List<ScriptFile>? =
        responseCache.read(ResponseCache.SCRIPTS, ListSerializer(ScriptFile.serializer()))
            ?.let { scripts -> withContext(Dispatchers.Default) { sortScripts(scripts) } }

    override suspend fun getScripts(): Result<List<ScriptFile>> {
        return try {
            val res = api.getScripts()
            if (res.code == 200) {
                val value = withContext(Dispatchers.Default) { sortScripts(res.data.orEmpty()) }
                responseCache.write(
                    ResponseCache.SCRIPTS,
                    ListSerializer(ScriptFile.serializer()),
                    value
                )
                Result.success(value)
            } else Result.failure(Exception(res.message ?: "获取脚本列表失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun getScriptContent(filename: String, path: String): Result<String> {
        return try {
            val res = api.getScriptContent(filename, path)
            if (res.code == 200) {
                Result.success(res.data ?: "")
            } else {
                Result.failure(Exception(res.message ?: "获取脚本内容失败"))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(Exception("读取脚本 $filename 失败: ${e.message ?: "未知错误"}", e))
        }
    }

    override suspend fun prepareDraft(script: ScriptFile): Result<ScriptDraft> {
        val filename = script.title?.takeIf(String::isNotBlank)
            ?: return Result.failure(IllegalArgumentException("脚本文件名为空"))
        return try {
            val currentResult = fetchCurrentServerFile(script)
            val current = currentResult.getOrNull()
            val persisted = draftStore.findPersisted(script)
            if (persisted != null) {
                val hasLocalChanges = draftStore.hasChanges(persisted)
                val unchanged = isServerVersionUnchanged(
                    persisted.sourceSizeBytes,
                    persisted.sourceModifiedTime,
                    ScriptServerVersion(current?.size, current?.mtime)
                )
                // A cached draft is the only usable source while the server is temporarily
                // unreachable. Keep it instead of attempting a download that can only fail;
                // a successful server response with a missing file still falls through and
                // refreshes the cache as before.
                if (hasLocalChanges || unchanged || currentResult.isFailure) {
                    return Result.success(persisted)
                }
            }
            val response = api.downloadScript(ScriptDeleteRequest(filename, script.parent.orEmpty()))
            val body = requireDownloadBody(response, filename)
            Result.success(
                draftStore.create(
                    script,
                    body,
                    ScriptServerVersion(current?.size, current?.mtime)
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(Exception("缓存脚本 $filename 失败: ${error.message ?: "未知错误"}", error))
        }
    }

    override suspend fun refreshDraft(draft: ScriptDraft): Result<ScriptDraft> = resultOfSuspend {
        draftStore.refresh(draft)
    }

    override suspend fun readDraftPage(
        draft: ScriptDraft,
        pageIndex: Int
    ): Result<ScriptDraftPage> = resultOfSuspend {
        draftStore.readPage(draft, pageIndex)
    }

    override suspend fun readDraftText(draft: ScriptDraft, maxBytes: Long): Result<String> =
        resultOfSuspend { draftStore.readText(draft, maxBytes) }

    override suspend fun replaceDraftText(
        draft: ScriptDraft,
        content: String,
        preserveUtf8Bom: Boolean
    ): Result<ScriptDraft> = resultOfSuspend {
        draftStore.replaceText(draft, content, preserveUtf8Bom)
    }

    override suspend fun hasDraftChanges(draft: ScriptDraft): Result<Boolean> =
        resultOfSuspend { draftStore.hasChanges(draft) }

    override suspend fun snapshotDraft(draft: ScriptDraft): Result<Long> =
        resultOfSuspend { draftStore.snapshot(draft) }

    override suspend fun restoreDraftSnapshot(draft: ScriptDraft): Result<Unit> =
        resultOfSuspend { draftStore.restoreSnapshot(draft) }

    override suspend fun uploadDraft(
        draft: ScriptDraft,
        force: Boolean
    ): Result<ScriptDraftUploadResult> {
        return try {
            val readyDraft = draftStore.refresh(draft)
            require(readyDraft.isUtf8Valid) { "文件不是有效的 UTF-8 文本，已阻止回传" }
            if (!force && serverChangedSinceDownload(readyDraft)) {
                return Result.success(ScriptDraftUploadResult.CONFLICT)
            }
            uploadMultipart(readyDraft)
            if (!verifyUploadedVersion(readyDraft)) {
                return Result.success(ScriptDraftUploadResult.PENDING_UPLOAD)
            }
            responseCache.invalidate(ResponseCache.SCRIPTS)
            Result.success(ScriptDraftUploadResult.SAVED)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(Exception("保存脚本 ${draft.filename} 失败: ${error.message ?: "未知错误"}", error))
        }
    }

    override suspend fun discardDraft(draft: ScriptDraft) {
        try {
            draftStore.discard(draft)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Temporary draft cleanup is best effort; scheduled maintenance is the fallback.
        }
    }

    override suspend fun exportScript(
        filename: String,
        path: String,
        destinationUri: String
    ): Result<Unit> {
        return try {
            val response = api.downloadScript(ScriptDeleteRequest(filename, path))
            val body = requireDownloadBody(response, filename)
            draftStore.export(body, destinationUri)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(Exception("下载脚本 $filename 失败: ${error.message ?: "未知错误"}", error))
        }
    }

    override suspend fun addScript(filename: String, path: String, content: String): Result<Unit> {
        return try {
            val res = api.addScript(ScriptAddRequest(filename, path, content))
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.SCRIPTS)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "添加脚本失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateScript(filename: String, path: String, content: String): Result<Unit> {
        return try {
            val res = api.updateScript(ScriptUpdateRequest(filename, path, content))
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.SCRIPTS)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "保存脚本失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(Exception("保存脚本 $filename 失败: ${e.message ?: "未知错误"}", e))
        }
    }

    override suspend fun deleteScript(filename: String, path: String, isDir: Boolean): Result<Unit> {
        return try {
            val res = api.deleteScript(
                ScriptDeleteRequest(filename, path, if (isDir) "directory" else "file")
            )
            if (res.code == 200) {
                responseCache.invalidate(ResponseCache.SCRIPTS)
                Result.success(Unit)
            }
            else Result.failure(Exception(res.message ?: "删除脚本失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun serverChangedSinceDownload(draft: ScriptDraft): Boolean {
        val response = api.getScripts()
        if (response.code != 200) {
            throw Exception(response.message ?: "保存前检查服务端版本失败")
        }
        val current = response.data.orEmpty().findByNormalizedPath(draft.sourceKey) ?: return true
        val hasComparableSize = draft.sourceSizeBytes != null && current.size != null
        val hasComparableModifiedTime = draft.sourceModifiedTime != null && current.mtime != null
        if (hasComparableSize && draft.sourceSizeBytes != current.size) return true
        if (hasComparableModifiedTime &&
            abs(requireNotNull(draft.sourceModifiedTime) - requireNotNull(current.mtime)) > MTIME_TOLERANCE
        ) {
            return true
        }
        if (hasComparableSize && hasComparableModifiedTime) return false

        val currentBody = requireDownloadBody(
            api.downloadScript(ScriptDeleteRequest(draft.filename, draft.path)),
            draft.filename
        )
        return currentBody.sha256() != draft.originalSha256
    }

    private suspend fun verifyUploadedVersion(draft: ScriptDraft): Boolean {
        val current = fetchCurrentServerFile(draft.scriptFile()).getOrNull() ?: return false
        return current.size != null && current.size == draft.sizeBytes
    }

    private suspend fun fetchCurrentServerFile(script: ScriptFile): Result<ScriptFile?> = try {
        val response = api.getScripts()
        if (response.code != 200) {
            Result.failure(IllegalStateException(response.message ?: "获取脚本列表失败"))
        } else {
            Result.success(response.data.orEmpty().findByNormalizedPath(script.normalizedPath()))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun ScriptDraft.scriptFile() = ScriptFile(
        title = filename,
        key = sourceKey,
        type = "file",
        parent = path,
        size = sourceSizeBytes,
        mtime = sourceModifiedTime
    )

    private suspend fun uploadMultipart(draft: ScriptDraft) {
        val upload = draftStore.createUpload(draft)
        try {
            val response = api.uploadScriptFile(
                file = upload.part,
                filename = draft.filename.toRequestBody(TEXT_MEDIA_TYPE),
                path = draft.path.toRequestBody(TEXT_MEDIA_TYPE)
            )
            requireApiSuccess(response, "保存脚本失败")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cleanupTemporaryUpload(upload.temporaryFilename)
            throw error
        }
    }

    private suspend fun cleanupTemporaryUpload(filename: String) {
        try {
            api.deleteScript(ScriptDeleteRequest(filename = filename, path = ""))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The upload may have failed before multer created the temporary file.
        }
    }

    private fun requireDownloadBody(response: Response<ResponseBody>, filename: String): ResponseBody {
        if (!response.isSuccessful) {
            val detail = response.errorBody()?.use { it.string().take(ERROR_BODY_LIMIT) }
            throw Exception(detail?.serverMessageOrNull() ?: "HTTP ${response.code()}")
        }
        val body = response.body() ?: throw Exception("服务端未返回脚本内容")
        val disposition = response.headers()["Content-Disposition"].orEmpty()
        if (!disposition.contains("attachment", ignoreCase = true) &&
            !disposition.contains("filename", ignoreCase = true)
        ) {
            val detail = body.use { it.string().take(ERROR_BODY_LIMIT) }
            throw Exception(detail.serverMessageOrNull() ?: "服务端未返回 $filename 的文件流")
        }
        return body
    }

    private fun requireApiSuccess(response: Response<ResponseBody>, fallbackMessage: String) {
        val payload = if (response.isSuccessful) {
            response.body()?.use { it.string().take(ERROR_BODY_LIMIT) }
        } else {
            response.errorBody()?.use { it.string().take(ERROR_BODY_LIMIT) }
        }.orEmpty()
        if (!response.isSuccessful) {
            throw Exception(payload.serverMessageOrNull() ?: "$fallbackMessage（HTTP ${response.code()}）")
        }
        val apiCode = payload.serverCodeOrNull()
        if (apiCode != null && apiCode != 200) {
            throw Exception(payload.serverMessageOrNull() ?: "$fallbackMessage（code=$apiCode）")
        }
    }

    private fun List<ScriptFile>.findByNormalizedPath(path: String): ScriptFile? {
        for (file in this) {
            if (!file.isDirectory && file.normalizedPath() == path) return file
            file.children?.findByNormalizedPath(path)?.let { return it }
        }
        return null
    }

    private fun sortScripts(list: List<ScriptFile>): List<ScriptFile> =
        list.sortedWith(compareByDescending<ScriptFile> { it.isDirectory }.thenBy { it.title })
            .map { file ->
                file.children?.let { children -> file.copy(children = sortScripts(children)) } ?: file
            }

    private companion object {
        const val ERROR_BODY_LIMIT = 8 * 1024
        const val MTIME_TOLERANCE = 0.000_001
        val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()
    }
}

private suspend inline fun <T> resultOfSuspend(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

private fun ScriptFile.normalizedPath(): String {
    val raw = key?.takeIf(String::isNotBlank)
        ?: listOf(parent.orEmpty(), title.orEmpty()).filter(String::isNotBlank).joinToString("/")
    return raw.replace('\\', '/').removePrefix("./").trimStart('/').replace(Regex("/+"), "/")
}

private fun String.serverMessageOrNull(): String? {
    val match = Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(this) ?: return null
    return match.groupValues[1].replace("\\n", " ").replace("\\\"", "\"")
}

private fun String.serverCodeOrNull(): Int? =
    Regex("\\\"code\\\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toIntOrNull()

private suspend fun ResponseBody.sha256(): String = withContext(Dispatchers.IO) {
    use { body ->
        val digest = MessageDigest.getInstance("SHA-256")
        body.byteStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
