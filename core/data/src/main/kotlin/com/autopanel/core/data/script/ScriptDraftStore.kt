package com.autopanel.core.data.script

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.domain.ScriptDraftPage
import com.autopanel.core.domain.ScriptServerVersion
import com.autopanel.core.model.ScriptFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil

@Singleton
class ScriptDraftStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val json: Json
) {
    suspend fun create(
        script: ScriptFile,
        body: ResponseBody,
        serverVersion: ScriptServerVersion? = null
    ): ScriptDraft = withContext(Dispatchers.IO) {
        val filename = script.title?.takeIf(String::isNotBlank) ?: error("脚本文件名为空")
        val path = script.parent.orEmpty()
        val sourceKey = script.normalizedScriptPath()
        val scope = currentScope() ?: error("登录会话不可用")
        val token = buildCacheToken(scope, sourceKey, filename)
        val destination = resolveToken(token)
        val temporary = File(rootDirectory(), "$token.part")
        destination.parentFile?.mkdirs()
        temporary.delete()

        try {
            val declaredLength = body.contentLength()
            require(declaredLength <= MAX_DRAFT_BYTES || declaredLength < 0L) {
                "脚本超过 50 MB，无法创建本地预览缓存"
            }
            body.use { responseBody ->
                responseBody.byteStream().buffered().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_DRAFT_BYTES) {
                                "脚本超过 50 MB，无法创建本地预览缓存"
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            moveReplacing(temporary, destination)
            val analysis = analyzeUtf8(destination)
            val contentHash = sha256(destination)
            val sourceSize = serverVersion?.sizeBytes ?: script.size
            val sourceMtime = serverVersion?.modifiedTime ?: script.mtime
            writeMetadata(
                destination,
                DraftMetadata(
                    serverSizeBytes = sourceSize,
                    serverModifiedTime = sourceMtime,
                    sha256 = contentHash
                )
            )
            ScriptDraft(
                cacheToken = token,
                filename = filename,
                path = path,
                sourceKey = sourceKey,
                sizeBytes = destination.length(),
                characterCount = analysis.characterCount,
                pageCount = analysis.pageCount,
                hasUtf8Bom = analysis.hasUtf8Bom,
                isUtf8Valid = analysis.isUtf8Valid,
                editorUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.script-files",
                    destination
                ).toString(),
                sourceSizeBytes = sourceSize,
                sourceModifiedTime = sourceMtime,
                originalSha256 = contentHash
            )
        } finally {
            temporary.delete()
        }
    }

    suspend fun findPersisted(script: ScriptFile): ScriptDraft? = withContext(Dispatchers.IO) {
        val filename = script.title?.takeIf(String::isNotBlank) ?: return@withContext null
        val path = script.parent.orEmpty()
        val sourceKey = script.normalizedScriptPath()
        val scope = currentScope() ?: return@withContext null
        val token = buildCacheToken(scope, sourceKey, filename)
        val file = resolveToken(token)
        if (!file.isFile) return@withContext null
        val metadata = readMetadata(file) ?: return@withContext null
        val analysis = analyzeUtf8(file)
        ScriptDraft(
            cacheToken = token,
            filename = filename,
            path = path,
            sourceKey = sourceKey,
            sizeBytes = file.length(),
            characterCount = analysis.characterCount,
            pageCount = analysis.pageCount,
            hasUtf8Bom = analysis.hasUtf8Bom,
            isUtf8Valid = analysis.isUtf8Valid,
            editorUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.script-files",
                file
            ).toString(),
            sourceSizeBytes = metadata.serverSizeBytes,
            sourceModifiedTime = metadata.serverModifiedTime,
            originalSha256 = metadata.sha256
        )
    }

    suspend fun refresh(draft: ScriptDraft): ScriptDraft = withContext(Dispatchers.IO) {
        val file = resolveDraft(draft)
        val analysis = analyzeUtf8(file)
        draft.copy(
            sizeBytes = file.length(),
            characterCount = analysis.characterCount,
            pageCount = analysis.pageCount,
            hasUtf8Bom = analysis.hasUtf8Bom,
            isUtf8Valid = analysis.isUtf8Valid
        )
    }

    suspend fun readPage(draft: ScriptDraft, pageIndex: Int): ScriptDraftPage =
        withContext(Dispatchers.IO) {
            require(draft.isUtf8Valid) { "文件不是有效的 UTF-8 文本" }
            val index = pageIndex.coerceIn(0, (draft.pageCount - 1).coerceAtLeast(0))
            ScriptDraftPage(
                index = index,
                totalPages = draft.pageCount.coerceAtLeast(1),
                content = readUtf8Page(resolveDraft(draft), draft.hasUtf8Bom, index)
            )
        }

    suspend fun readText(draft: ScriptDraft, maxBytes: Long): String = withContext(Dispatchers.IO) {
        decodeUtf8File(resolveDraft(draft), maxBytes, dropBom = true)
    }

    suspend fun replaceText(
        draft: ScriptDraft,
        content: String,
        preserveUtf8Bom: Boolean
    ): ScriptDraft = withContext(Dispatchers.IO) {
        val destination = resolveDraft(draft)
        val temporary = File(destination.parentFile, "${destination.name}.edit")
        temporary.delete()
        try {
            val encoded = Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(content))
            val encodedSize = encoded.remaining().toLong() +
                if (preserveUtf8Bom) UTF8_BOM_BYTES.size.toLong() else 0L
            require(encodedSize <= MAX_EDITABLE_BYTES) {
                "本地修改超过 10 MB，无法安全回传"
            }
            temporary.outputStream().buffered().use { output ->
                if (preserveUtf8Bom) output.write(UTF8_BOM_BYTES)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (encoded.hasRemaining()) {
                    val count = minOf(encoded.remaining(), buffer.size)
                    encoded.get(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            moveReplacing(temporary, destination)
            refresh(draft)
        } finally {
            temporary.delete()
        }
    }

    internal suspend fun createUpload(draft: ScriptDraft): ScriptDraftUpload = withContext(Dispatchers.IO) {
        val file = resolveDraft(draft)
        require(draft.isUtf8Valid) { "文件不是有效的 UTF-8 文本，已阻止回传" }
        require(file.length() <= MAX_EDITABLE_BYTES) { "本地修改超过 10 MB，无法安全回传" }
        val temporaryFilename = "azureql-${UUID.randomUUID()}.upload"
        ScriptDraftUpload(
            temporaryFilename = temporaryFilename,
            part = MultipartBody.Part.createFormData(
                name = "file",
                filename = temporaryFilename,
                body = file.asRequestBody(BINARY_MEDIA_TYPE)
            )
        )
    }

    suspend fun hasChanges(draft: ScriptDraft): Boolean = withContext(Dispatchers.IO) {
        sha256(resolveDraft(draft)) != draft.originalSha256
    }

    suspend fun snapshot(draft: ScriptDraft): Long = withContext(Dispatchers.IO) {
        val file = resolveDraft(draft)
        val backup = snapshotFile(file)
        val temporary = File(backup.parentFile, "${backup.name}$TEMP_SUFFIX")
        temporary.delete()
        file.copyTo(temporary, overwrite = true)
        moveReplacing(temporary, backup)
        file.length()
    }

    suspend fun restoreSnapshot(draft: ScriptDraft) = withContext(Dispatchers.IO) {
        val file = resolveDraft(draft)
        val backup = snapshotFile(file)
        require(backup.isFile) { "本地编辑前的备份不存在，无法还原" }
        val temporary = File(file.parentFile, "${file.name}$RESTORE_SUFFIX")
        temporary.delete()
        backup.copyTo(temporary, overwrite = true)
        moveReplacing(temporary, file)
        backup.delete()
        Unit
    }

    suspend fun export(body: ResponseBody, destinationUri: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(destinationUri)
        body.use { responseBody ->
            val output = context.contentResolver.openOutputStream(uri, "rwt")
                ?: context.contentResolver.openOutputStream(uri, "w")
                ?: error("无法写入所选位置")
            output.buffered().use { sink ->
                responseBody.byteStream().buffered().use { source -> source.copyTo(sink) }
            }
        }
    }

    suspend fun discard(draft: ScriptDraft) = withContext(Dispatchers.IO) {
        val file = resolveDraft(draft, requireExists = false)
        file.delete()
        metadataFile(file).delete()
        snapshotFile(file).delete()
        Unit
    }

    suspend fun prune(nowMillis: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val root = rootDirectory()
        val cutoff = nowMillis - RETENTION_MILLIS
        var deleted = 0
        val contentFiles = root.listFiles().orEmpty().filter { file ->
            file.isFile && TOKEN_PATTERN.matches(file.name)
        }
        for (file in contentFiles) {
            if (file.lastModified() < cutoff) {
                if (file.delete()) deleted++
                metadataFile(file).delete()
                snapshotFile(file).delete()
            }
        }
        var remaining = contentFiles
            .filter { it.isFile && it.lastModified() >= cutoff }
            .sortedBy { it.lastModified() }
        var totalBytes = remaining.sumOf { it.length() }
        while (remaining.isNotEmpty() &&
            (totalBytes > MAX_CACHE_BYTES || remaining.size > MAX_CACHE_FILES)
        ) {
            val oldest = remaining.first()
            if (oldest.delete()) deleted++
            metadataFile(oldest).delete()
            snapshotFile(oldest).delete()
            totalBytes -= oldest.length()
            remaining = remaining.drop(1)
        }
        for (sidecar in root.listFiles().orEmpty()) {
            if (!sidecar.isFile) continue
            val contentName = sidecarContentName(sidecar.name) ?: continue
            if (!TOKEN_PATTERN.matches(contentName)) continue
            if (!File(root, contentName).isFile && sidecar.delete()) deleted++
        }
        deleted
    }

    private suspend fun currentScope(): String? {
        val session = sessionManager.getSession()
        val host = session.host?.trim()?.trimEnd('/')?.lowercase()?.takeIf(String::isNotEmpty)
            ?: return null
        val username = session.username?.trim()?.lowercase().orEmpty()
        return sha256("$host\u0000$username\u0000${session.authMode.name}")
    }

    private fun rootDirectory(): File = File(context.cacheDir, ROOT_DIRECTORY).apply { mkdirs() }

    private fun resolveDraft(draft: ScriptDraft, requireExists: Boolean = true): File {
        val file = resolveToken(draft.cacheToken)
        if (requireExists) require(file.isFile) { "本地脚本缓存已失效，请重新打开脚本" }
        return file
    }

    private fun resolveToken(token: String): File {
        require(TOKEN_PATTERN.matches(token)) { "无效的本地脚本缓存标识" }
        val root = rootDirectory().canonicalFile
        val file = File(root, token).canonicalFile
        require(file.parentFile == root) { "无效的本地脚本缓存路径" }
        return file
    }

    private fun buildCacheToken(scope: String, sourceKey: String, filename: String): String {
        val prefix = sha256("$scope\u0000$sourceKey").take(24)
        val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
            .takeLast(64)
            .ifBlank { "script.txt" }
        return "$prefix-$safeName"
    }

    private fun metadataFile(contentFile: File): File =
        File(contentFile.parentFile, "${contentFile.name}$META_SUFFIX")

    private fun snapshotFile(contentFile: File): File =
        File(contentFile.parentFile, "${contentFile.name}$BACKUP_SUFFIX")

    private fun sidecarContentName(fileName: String): String? {
        for (suffix in SIDECAR_SUFFIXES) {
            if (fileName.endsWith(suffix)) return fileName.removeSuffix(suffix)
        }
        return null
    }

    private fun writeMetadata(contentFile: File, metadata: DraftMetadata) {
        val metaFile = metadataFile(contentFile)
        val temporary = File(metaFile.parentFile, "${metaFile.name}$TEMP_SUFFIX")
        temporary.writeText(json.encodeToString(metadata), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                metaFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), metaFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readMetadata(contentFile: File): DraftMetadata? = runCatching {
        json.decodeFromString<DraftMetadata>(metadataFile(contentFile).readText(Charsets.UTF_8))
    }.getOrNull()

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    internal companion object {
        const val PAGE_CHARACTERS = 8 * 1024
        const val MAX_EDITABLE_BYTES = 10L * 1024L * 1024L
        const val MAX_CACHE_FILES = 64
        const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
        const val MTIME_TOLERANCE = 0.000_001
        private const val MAX_DRAFT_BYTES = 50L * 1024L * 1024L
        private const val ROOT_DIRECTORY = "script-drafts"
        private const val META_SUFFIX = ".meta"
        private const val BACKUP_SUFFIX = ".bak"
        private const val PART_SUFFIX = ".part"
        private const val TEMP_SUFFIX = ".tmp"
        private const val RESTORE_SUFFIX = ".restore"
        private val SIDECAR_SUFFIXES = listOf(
            META_SUFFIX,
            BACKUP_SUFFIX,
            PART_SUFFIX,
            "$META_SUFFIX$TEMP_SUFFIX",
            "$BACKUP_SUFFIX$TEMP_SUFFIX",
            RESTORE_SUFFIX
        )
        private val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(8)
        private val TOKEN_PATTERN = Regex("[a-f0-9]{24}-[A-Za-z0-9._-]{1,64}")
        private val UTF8_BOM_BYTES = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}

@Serializable
internal data class DraftMetadata(
    val serverSizeBytes: Long? = null,
    val serverModifiedTime: Double? = null,
    val sha256: String
)

internal fun isServerVersionUnchanged(
    storedSizeBytes: Long?,
    storedModifiedTime: Double?,
    current: ScriptServerVersion?
): Boolean {
    if (current == null) return false
    val storedSize = storedSizeBytes ?: return false
    val currentSize = current.sizeBytes ?: return false
    if (storedSize != currentSize) return false
    val storedMtime = storedModifiedTime
    val currentMtime = current.modifiedTime
    if (storedMtime != null && currentMtime != null &&
        abs(storedMtime - currentMtime) > ScriptDraftStore.MTIME_TOLERANCE
    ) {
        return false
    }
    return true
}

internal data class ScriptDraftUpload(
    val temporaryFilename: String,
    val part: MultipartBody.Part
)

internal data class Utf8Analysis(
    val characterCount: Long,
    val pageCount: Int,
    val hasUtf8Bom: Boolean,
    val isUtf8Valid: Boolean
)

internal fun analyzeUtf8(file: File): Utf8Analysis {
    val hasBom = file.inputStream().buffered().use { input ->
        val prefix = ByteArray(3)
        input.read(prefix) == 3 && prefix.contentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
    }
    return try {
        strictUtf8Reader(file, hasBom).use { reader ->
            val buffer = CharArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                total += count
            }
            Utf8Analysis(
                characterCount = total,
                pageCount = ceil(total.toDouble() / ScriptDraftStore.PAGE_CHARACTERS)
                    .toInt()
                    .coerceAtLeast(1),
                hasUtf8Bom = hasBom,
                isUtf8Valid = true
            )
        }
    } catch (_: CharacterCodingException) {
        Utf8Analysis(0, 1, hasBom, false)
    }
}

internal fun readUtf8Page(file: File, hasUtf8Bom: Boolean, pageIndex: Int): String {
    strictUtf8Reader(file, hasUtf8Bom).use { reader ->
        var remaining = pageIndex.toLong() * ScriptDraftStore.PAGE_CHARACTERS
        val discard = CharArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0L) {
            val count = reader.read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
            if (count < 0) return ""
            remaining -= count
        }
        val page = CharArray(ScriptDraftStore.PAGE_CHARACTERS)
        var total = 0
        while (total < page.size) {
            val count = reader.read(page, total, page.size - total)
            if (count < 0) break
            total += count
        }
        return String(page, 0, total)
    }
}

private fun decodeUtf8File(file: File, maxBytes: Long, dropBom: Boolean): String {
    require(file.length() <= maxBytes) { "脚本超过 ${maxBytes / 1024 / 1024} MB，无法安全载入编辑器" }
    val decoded = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(file.readBytes()))
        .toString()
    return if (dropBom && decoded.startsWith('\uFEFF')) decoded.drop(1) else decoded
}

private fun strictUtf8Reader(file: File, skipBom: Boolean): InputStreamReader {
    val input = FileInputStream(file)
    if (skipBom) {
        repeat(3) {
            if (input.read() < 0) error("UTF-8 BOM 不完整")
        }
    }
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return InputStreamReader(input, decoder)
}

private fun ScriptFile.normalizedScriptPath(): String {
    val raw = key?.takeIf(String::isNotBlank)
        ?: listOf(parent.orEmpty(), title.orEmpty()).filter(String::isNotBlank).joinToString("/")
    return raw.replace('\\', '/').removePrefix("./").trimStart('/').replace(Regex("/+"), "/")
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
