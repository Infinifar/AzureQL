package com.autopanel.feature.backup

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException

internal interface S3BackupStorage {
    suspend fun testConnection(connection: S3Connection): Result<Unit>

    suspend fun uploadBackup(
        connection: S3Connection,
        source: File,
        fileName: String,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<Unit>
}

internal class OkHttpS3BackupStorage @Inject constructor(
    @S3HttpClient private val client: OkHttpClient,
    private val signer: AwsV4Signer
) : S3BackupStorage {
    override suspend fun testConnection(connection: S3Connection): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                validateS3Settings(
                    connection.endpoint,
                    connection.bucket,
                    connection.region,
                    connection.remoteDirectory
                )?.let { throw IllegalArgumentException(it) }
                executeWithRegion(connection) { resolvedRegion ->
                    signer.sign(
                        connection = connection,
                        region = resolvedRegion,
                        method = "HEAD",
                        url = connection.objectUrl(null),
                        payloadHash = EMPTY_SHA256
                    )
                }.use { response ->
                    if (response.code !in setOf(200, 204)) throw S3HttpException(response.code)
                }
            }
        }

    override suspend fun uploadBackup(
        connection: S3Connection,
        source: File,
        fileName: String,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(source.isFile) { "待上传的备份文件不存在" }
            require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName) {
                "备份文件名无效"
            }
            validateS3Settings(
                connection.endpoint,
                connection.bucket,
                connection.region,
                connection.remoteDirectory
            )?.let { throw IllegalArgumentException(it) }

            val payloadHash = source.sha256Hex()
            val key = (connection.remoteDirectory.split('/', '\\') + fileName)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .joinToString("/")
            val body = S3FileRequestBody(source, onProgress)
            executeWithRegion(connection) { resolvedRegion ->
                signer.sign(
                    connection = connection,
                    region = resolvedRegion,
                    method = "PUT",
                    url = connection.objectUrl(key),
                    payloadHash = payloadHash,
                    headers = mapOf(
                        "content-length" to source.length().toString(),
                        "content-type" to "application/gzip",
                        "if-none-match" to "*"
                    ),
                    body = body
                )
            }.use { response ->
                if (response.code !in setOf(200, 201, 204)) throw S3HttpException(response.code)
            }
        }
    }

    private fun executeWithRegion(
        connection: S3Connection,
        request: (String) -> Request
    ): okhttp3.Response {
        val initialRegion = connection.region.takeUnless { it.equals("auto", true) }
            ?: if (connection.endpoint.toHttpUrlOrNull()?.host?.endsWith("amazonaws.com") == true) {
                DEFAULT_SIGNING_REGION
            } else {
                "auto"
            }
        val first = client.newCall(request(initialRegion)).execute()
        val redirectedRegion = first.header("x-amz-bucket-region")
            ?.takeIf { connection.region.equals("auto", true) && it != initialRegion }
        if (redirectedRegion == null || first.isSuccessful) return first
        first.close()
        return client.newCall(request(redirectedRegion)).execute()
    }
}

@Singleton
internal class AwsV4Signer @Inject constructor() {
    internal var clock: Clock = Clock.systemUTC()

    fun sign(
        connection: S3Connection,
        region: String,
        method: String,
        url: HttpUrl,
        payloadHash: String,
        headers: Map<String, String> = emptyMap(),
        body: RequestBody? = null
    ): Request {
        val now = Instant.now(clock)
        val dateStamp = DATE_FORMATTER.format(now)
        val amzDate = TIME_FORMATTER.format(now)
        val normalizedHeaders = headers.entries.associate { (name, value) ->
            name.lowercase(Locale.US) to value.trim().replace(HEADER_WHITESPACE, " ")
        }.toMutableMap().apply {
            put("host", url.hostHeader())
            put("x-amz-content-sha256", payloadHash)
            put("x-amz-date", amzDate)
        }
        val signedHeaders = normalizedHeaders.keys.sorted().joinToString(";")
        val canonicalHeaders = normalizedHeaders.entries.sortedBy { it.key }
            .joinToString("") { (name, value) -> "$name:$value\n" }
        val canonicalRequest = listOf(
            method,
            url.encodedPath.ifEmpty { "/" },
            url.canonicalQuery(),
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")
        val credentialScope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            canonicalRequest.sha256Hex()
        ).joinToString("\n")
        val signingKey = signatureKey(connection.secretAccessKey, dateStamp, region)
        val signature = hmac(signingKey, stringToSign).toHex()
        val authorization = "$ALGORITHM Credential=${connection.accessKeyId}/$credentialScope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        return Request.Builder()
            .url(url)
            .method(method, body)
            .apply { normalizedHeaders.forEach { (name, value) -> header(name, value) } }
            .header("Authorization", authorization)
            .build()
    }

    private fun signatureKey(secret: String, date: String, region: String): ByteArray {
        val dateKey = hmac("AWS4$secret".toByteArray(), date)
        val regionKey = hmac(dateKey, region)
        val serviceKey = hmac(regionKey, "s3")
        return hmac(serviceKey, "aws4_request")
    }
}

internal class S3HttpException(val statusCode: Int) : Exception("HTTP $statusCode")

internal fun s3FailureMessage(error: Throwable?, fallback: String): String = when (error) {
    is S3HttpException -> when (error.statusCode) {
        301, 307 -> "S3 区域不匹配，请检查区域设置"
        400 -> "S3 请求无效，请检查端点、区域与路径样式"
        401, 403 -> "S3 认证或权限校验失败，请检查访问密钥和存储桶权限"
        404 -> "S3 端点或存储桶不存在"
        409 -> "S3 写入冲突，请稍后重试"
        412 -> "同名备份已存在，请稍后重试"
        507 -> "S3 存储空间不足"
        else -> "$fallback（HTTP ${error.statusCode}）"
    }
    is SSLHandshakeException -> "S3 TLS 证书验证失败"
    is SocketTimeoutException -> "S3 连接超时，请稍后重试"
    is IllegalArgumentException -> error.message ?: fallback
    is IOException -> "$fallback，请检查网络连接"
    else -> fallback
}

internal fun validateS3Settings(
    endpoint: String,
    bucket: String,
    region: String,
    remoteDirectory: String
): String? {
    val url = endpoint.trim().toHttpUrlOrNull() ?: return "请输入有效的 S3 端点"
    if (url.scheme != "https" && url.scheme != "http") return "S3 端点仅支持 HTTP 或 HTTPS"
    if (url.encodedQuery != null || url.fragment != null) return "S3 端点不能包含查询参数或片段"
    if (bucket.trim().isBlank()) return "请输入 S3 存储桶名称"
    if (bucket.any { it == '/' || it == '\\' || it.isWhitespace() }) return "S3 存储桶名称无效"
    val normalizedRegion = region.trim()
    if (!normalizedRegion.equals("auto", true) && !S3_REGION.matches(normalizedRegion)) {
        return "S3 区域格式无效"
    }
    if (remoteDirectory.trim().trim('/').isBlank()) return "请输入 S3 远程目录"
    if (remoteDirectory.split('/', '\\').map(String::trim).any { it == "." || it == ".." }) {
        return "S3 远程目录不能包含 . 或 .. 路径段"
    }
    return null
}

private fun S3Connection.objectUrl(key: String?): HttpUrl {
    val base = endpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("S3 端点无效")
    val builder = base.newBuilder()
    if (pathStyle) {
        builder.addPathSegment(bucket)
    } else {
        builder.host("$bucket.${base.host}")
    }
    key?.split('/')?.filter(String::isNotEmpty)?.forEach(builder::addPathSegment)
    return builder.build()
}

private fun HttpUrl.hostHeader(): String {
    val formattedHost = if (':' in host) "[$host]" else host
    return if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) {
        formattedHost
    } else {
        "$formattedHost:$port"
    }
}

private fun HttpUrl.canonicalQuery(): String = encodedQuery.orEmpty()

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun String.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(key, "HmacSHA256"))
    doFinal(value.toByteArray(Charsets.UTF_8))
}

private class S3FileRequestBody(
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class S3HttpClient

@Module
@InstallIn(SingletonComponent::class)
internal object S3NetworkModule {
    @Provides
    @Singleton
    @S3HttpClient
    fun provideS3HttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.HOURS)
        .writeTimeout(2, TimeUnit.HOURS)
        .followRedirects(false)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class S3StorageModule {
    @Binds
    abstract fun bindS3BackupStorage(implementation: OkHttpS3BackupStorage): S3BackupStorage
}

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
private val HEADER_WHITESPACE = Regex("\\s+")
private val S3_REGION = Regex("[a-z0-9][a-z0-9-]{0,62}")
private const val ALGORITHM = "AWS4-HMAC-SHA256"
private const val DEFAULT_SIGNING_REGION = "us-east-1"
private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
