package com.autopanel.feature.backup

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

internal interface WebDavBackupStorage {
    suspend fun testConnection(connection: WebDavConnection): Result<Unit>

    suspend fun uploadBackup(
        connection: WebDavConnection,
        source: File,
        fileName: String,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<Unit>

    suspend fun listBackups(connection: WebDavConnection): Result<List<NetworkBackupFile>>

    suspend fun downloadBackup(
        connection: WebDavConnection,
        remoteId: String,
        destination: File,
        maxBytes: Long,
        onProgress: (bytesTransferred: Long, totalBytes: Long?) -> Unit
    ): Result<Unit>
}

internal class OkHttpWebDavBackupStorage @Inject constructor(
    @WebDavHttpClient private val client: OkHttpClient
) : WebDavBackupStorage {
    override suspend fun testConnection(connection: WebDavConnection): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val baseUrl = connection.baseUrl()
        execute(
            connection.authorized(
                Request.Builder()
                    .url(baseUrl)
                    .header("Depth", "0")
                    .method("PROPFIND", PROPFIND_BODY)
            ).build(),
            acceptedCodes = PROPFIND_SUCCESS
        ).close()
        ensureRemoteDirectory(connection)
    } }

    override suspend fun uploadBackup(
        connection: WebDavConnection,
        source: File,
        fileName: String,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        require(source.isFile) { "待上传的备份文件不存在" }
        require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName) { "备份文件名无效" }
        ensureRemoteDirectory(connection)
        val target = connection.resourceUrl(fileName)
        val request = connection.authorized(
            Request.Builder()
                .url(target)
                .header("If-None-Match", "*")
                .put(FileProgressRequestBody(source, onProgress))
        ).build()
        try {
            execute(request, PUT_SUCCESS).close()
        } catch (error: Exception) {
            runCatching {
                execute(
                    connection.authorized(Request.Builder().url(target).delete()).build(),
                    DELETE_SUCCESS
                ).close()
            }
            throw error
        }
    } }

    override suspend fun listBackups(
        connection: WebDavConnection
    ): Result<List<NetworkBackupFile>> = withContext(Dispatchers.IO) { runCatching {
        validateWebDavSettings(
            connection.serverUrl,
            connection.username,
            connection.remoteDirectory
        )?.let { throw IllegalArgumentException(it) }
        val directoryUrl = connection.resourceUrl().newBuilder().addPathSegment("").build()
        val response = execute(
            connection.authorized(
                Request.Builder()
                    .url(directoryUrl)
                    .header("Depth", "1")
                    .method("PROPFIND", BACKUP_LIST_PROPFIND_BODY)
            ).build(),
            PROPFIND_SUCCESS
        )
        response.use {
            val bytes = it.body?.byteStream()?.use { input ->
                input.readBytesUpTo(MAX_LIST_RESPONSE_BYTES + 1)
            } ?: throw IOException("WebDAV 列表响应为空")
            if (bytes.size > MAX_LIST_RESPONSE_BYTES) throw IOException("WebDAV 列表响应过大")
            parseWebDavBackups(bytes, directoryUrl)
        }
    } }

    override suspend fun downloadBackup(
        connection: WebDavConnection,
        remoteId: String,
        destination: File,
        maxBytes: Long,
        onProgress: (bytesTransferred: Long, totalBytes: Long?) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        require(maxBytes > 0) { "备份大小上限无效" }
        val fileName = validateRemoteBackupFileName(remoteId)
        val request = connection.authorized(
            Request.Builder().url(connection.resourceUrl(fileName)).get()
        ).build()
        execute(request, GET_SUCCESS).use { response ->
            val body = response.body ?: throw IOException("WebDAV 下载响应为空")
            val total = body.contentLength().takeIf { it >= 0 }
            if (total != null && total > maxBytes) {
                throw IllegalArgumentException("备份文件超过大小上限，未开始下载")
            }
            destination.parentFile?.mkdirs()
            body.byteStream().buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    copyBackupStream(input, output, total, maxBytes, onProgress)
                }
            }
        }
    } }

    private fun ensureRemoteDirectory(connection: WebDavConnection) {
        val segments = connection.directorySegments()
        segments.indices.forEach { index ->
            val url = connection.resourceUrl(*segments.take(index + 1).toTypedArray(), includeDirectory = false)
            val exists = runCatching {
                execute(
                    connection.authorized(
                        Request.Builder()
                            .url(url)
                            .header("Depth", "0")
                            .method("PROPFIND", PROPFIND_BODY)
                    ).build(),
                    PROPFIND_SUCCESS
                ).close()
            }.isSuccess
            if (!exists) {
                execute(
                    connection.authorized(
                        Request.Builder().url(url).method("MKCOL", EMPTY_BODY)
                    ).build(),
                    MKCOL_SUCCESS
                ).close()
            }
        }
    }

    private fun execute(request: Request, acceptedCodes: Set<Int>): Response {
        val response = client.newCall(request).execute()
        if (response.code !in acceptedCodes) {
            val code = response.code
            response.close()
            throw WebDavHttpException(code)
        }
        return response
    }
}

private fun parseWebDavBackups(bytes: ByteArray, directoryUrl: HttpUrl): List<NetworkBackupFile> {
    val document = parseNetworkStorageXml(bytes)
    val expectedPrefix = directoryUrl.encodedPath.trimEnd('/') + "/"
    val responses = document.getElementsByTagNameNS("DAV:", "response")
    return buildList {
        for (index in 0 until responses.length) {
            val element = responses.item(index) as? org.w3c.dom.Element ?: continue
            val href = element.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: continue
            val resolved = directoryUrl.resolve(href) ?: continue
            if (resolved.scheme != directoryUrl.scheme || resolved.host != directoryUrl.host ||
                resolved.port != directoryUrl.port || !resolved.encodedPath.startsWith(expectedPrefix)
            ) continue
            val relativePath = resolved.encodedPath.removePrefix(expectedPrefix)
            if (relativePath.isBlank() || '/' in relativePath) continue
            val fileName = resolved.pathSegments.lastOrNull()?.let { value ->
                runCatching { validateRemoteBackupFileName(value) }.getOrNull()
            } ?: continue
            val size = element.getElementsByTagNameNS("DAV:", "getcontentlength")
                .item(0)?.textContent?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
            val modified = element.getElementsByTagNameNS("DAV:", "getlastmodified")
                .item(0)?.textContent?.trim()?.let { value ->
                    runCatching {
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli()
                    }.getOrNull()
                }
            add(NetworkBackupFile(NetworkStorageProvider.WEBDAV, fileName, fileName, size, modified))
        }
    }.sortedWith(compareByDescending<NetworkBackupFile> { it.modifiedAtEpochMillis ?: Long.MIN_VALUE }
        .thenByDescending(NetworkBackupFile::fileName))
}

private class FileProgressRequestBody(
    private val file: File,
    private val onProgress: (Long, Long) -> Unit
) : RequestBody() {
    override fun contentType() = "application/gzip".toMediaType()
    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        var transferred = 0L
        file.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, 64L * 1024L)
                if (read == -1L) break
                transferred += read
                sink.emitCompleteSegments()
                onProgress(transferred, contentLength())
            }
        }
    }
}

internal class WebDavHttpException(val statusCode: Int) : Exception("HTTP $statusCode")

internal fun webDavFailureMessage(error: Throwable?, fallback: String): String = when (error) {
    is WebDavHttpException -> when (error.statusCode) {
        401, 403 -> "WebDAV 认证失败，请检查用户名和密码"
        404 -> "WebDAV 地址或远程目录不存在"
        409 -> "WebDAV 上级目录不存在或不允许创建目录"
        412 -> "同名备份已存在，请稍后重试"
        507 -> "WebDAV 存储空间不足"
        else -> "$fallback（HTTP ${error.statusCode}）"
    }
    is SSLHandshakeException -> "WebDAV TLS 证书验证失败"
    is SocketTimeoutException -> "WebDAV 连接超时，请稍后重试"
    is IllegalArgumentException -> error.message ?: fallback
    is IOException -> "$fallback，请检查网络连接"
    else -> fallback
}

internal fun validateWebDavSettings(
    serverUrl: String,
    username: String,
    remoteDirectory: String
): String? {
    val url = serverUrl.trim().toHttpUrlOrNullCompat()
        ?: return "请输入有效的 WebDAV 地址"
    if (url.scheme != "https" && url.scheme != "http") return "WebDAV 地址仅支持 HTTP 或 HTTPS"
    if (remoteDirectory.trim().trim('/').isBlank()) return "请输入远程目录"
    val invalidSegment = remoteDirectory.split('/', '\\')
        .map(String::trim)
        .any { it == "." || it == ".." }
    if (invalidSegment) return "远程目录不能包含 . 或 .. 路径段"
    if (username.contains('\n') || username.contains('\r')) return "用户名包含无效字符"
    return null
}

private fun String.toHttpUrlOrNullCompat(): HttpUrl? = trim().toHttpUrlOrNull()

private fun WebDavConnection.baseUrl(): HttpUrl = serverUrl.toHttpUrlOrNullCompat()
    ?: throw IllegalArgumentException("WebDAV 地址无效")

private fun WebDavConnection.directorySegments(): List<String> = remoteDirectory
    .split('/', '\\')
    .map(String::trim)
    .filter(String::isNotEmpty)

private fun WebDavConnection.resourceUrl(
    vararg segments: String,
    includeDirectory: Boolean = true
): HttpUrl {
    val builder = baseUrl().newBuilder()
    val path = if (includeDirectory) directorySegments() + segments else segments.toList()
    path.forEach(builder::addPathSegment)
    return builder.build()
}

private fun WebDavConnection.authorized(builder: Request.Builder): Request.Builder = builder.apply {
    if (username.isNotBlank()) {
        header("Authorization", Credentials.basic(username, password, StandardCharsets.UTF_8))
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class WebDavHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal object WebDavNetworkModule {
    @Provides
    @Singleton
    @WebDavHttpClient
    fun provideWebDavHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.HOURS)
        .writeTimeout(2, TimeUnit.HOURS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WebDavStorageModule {
    @Binds
    abstract fun bindWebDavBackupStorage(
        implementation: OkHttpWebDavBackupStorage
    ): WebDavBackupStorage
}

private val PROPFIND_BODY = object : RequestBody() {
    override fun contentType() = "application/xml; charset=utf-8".toMediaType()
    override fun contentLength() = 0L
    override fun writeTo(sink: BufferedSink) = Unit
}
private val BACKUP_LIST_PROPFIND_BODY = """
    <?xml version="1.0" encoding="utf-8" ?>
    <D:propfind xmlns:D="DAV:">
      <D:prop><D:getcontentlength/><D:getlastmodified/><D:resourcetype/></D:prop>
    </D:propfind>
""".trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
private val EMPTY_BODY = object : RequestBody() {
    override fun contentType() = null
    override fun contentLength() = 0L
    override fun writeTo(sink: BufferedSink) = Unit
}
private val PROPFIND_SUCCESS = setOf(200, 207)
private val MKCOL_SUCCESS = setOf(200, 201, 204, 405)
private val PUT_SUCCESS = setOf(200, 201, 204)
private val DELETE_SUCCESS = setOf(200, 202, 204, 404)
private val GET_SUCCESS = setOf(200)
private const val MAX_LIST_RESPONSE_BYTES = 2 * 1024 * 1024
