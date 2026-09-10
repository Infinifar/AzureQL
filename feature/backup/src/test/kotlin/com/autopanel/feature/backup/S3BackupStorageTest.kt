package com.autopanel.feature.backup

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class S3BackupStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var signer: AwsV4Signer
    private lateinit var storage: OkHttpS3BackupStorage

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        signer = AwsV4Signer().apply {
            clock = Clock.fixed(Instant.parse("2026-09-06T08:30:00Z"), ZoneOffset.UTC)
        }
        storage = OkHttpS3BackupStorage(OkHttpClient(), signer)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `connection test signs a head bucket request`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = kotlinx.coroutines.runBlocking { storage.testConnection(connection()) }

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("HEAD", request.method)
        assertEquals("/backup-bucket", request.path)
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            request.getHeader("x-amz-content-sha256")
        )
        assertTrue(request.getHeader("Authorization").orEmpty().contains("/us-east-1/s3/aws4_request"))
    }

    @Test
    fun `upload streams archive to configured prefix without overwriting`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val source = temporaryFolder.newFile("backup.tgz").apply {
            writeBytes("archive-content".toByteArray())
        }
        val progress = mutableListOf<Pair<Long, Long>>()

        val result = kotlinx.coroutines.runBlocking {
            storage.uploadBackup(connection(), source, "azureql_backup.tgz") { sent, total ->
                progress += sent to total
            }
        }

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/backup-bucket/AzureQL/azureql_backup.tgz", request.path)
        assertEquals("*", request.getHeader("If-None-Match"))
        assertEquals("archive-content", request.body.readUtf8())
        assertEquals(source.length() to source.length(), progress.last())
        assertTrue(request.getHeader("Authorization").orEmpty().contains("if-none-match"))
    }

    @Test
    fun `auto region retries once with bucket response region`() {
        server.enqueue(
            MockResponse().setResponseCode(301).addHeader("x-amz-bucket-region", "eu-west-1")
        )
        server.enqueue(MockResponse().setResponseCode(200))

        val result = kotlinx.coroutines.runBlocking {
            storage.testConnection(connection(region = "auto"))
        }

        assertTrue(result.isSuccess)
        assertTrue(server.takeRequest().getHeader("Authorization").orEmpty().contains("/auto/"))
        assertTrue(server.takeRequest().getHeader("Authorization").orEmpty().contains("/eu-west-1/"))
    }

    @Test
    fun `list filters configured prefix and download streams signed object`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <IsTruncated>false</IsTruncated>
                  <Contents><Key>AzureQL/azureql_backup_20260910.tgz</Key>
                    <LastModified>2026-09-10T08:00:00.000Z</LastModified><Size>7</Size></Contents>
                  <Contents><Key>AzureQL/readme.txt</Key><Size>3</Size></Contents>
                  <Contents><Key>AzureQL/bad%5Cname.tgz</Key><Size>3</Size></Contents>
                  <Contents><Key>other/hidden.tgz</Key><Size>4</Size></Contents>
                </ListBucketResult>
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("archive"))
        val destination = temporaryFolder.newFile("download.tgz")

        val files = kotlinx.coroutines.runBlocking { storage.listBackups(connection()).getOrThrow() }
        assertEquals(1, files.size)
        assertEquals("AzureQL/azureql_backup_20260910.tgz", files.single().remoteId)
        assertEquals(7L, files.single().sizeBytes)

        val download = kotlinx.coroutines.runBlocking {
            storage.downloadBackup(
                connection(), files.single().remoteId, destination, 1024
            ) { _, _ -> }
        }
        assertTrue(download.isSuccess)
        server.takeRequest().also { request ->
            assertEquals("GET", request.method)
            assertTrue(request.path.orEmpty().contains("list-type=2"))
            assertTrue(request.getHeader("Authorization").orEmpty().contains("/s3/aws4_request"))
        }
        server.takeRequest().also { request ->
            assertEquals("GET", request.method)
            assertEquals("/backup-bucket/AzureQL/azureql_backup_20260910.tgz", request.path)
        }
        assertEquals("archive", destination.readText())
    }

    @Test
    fun `download rejects object outside configured prefix`() {
        val destination = temporaryFolder.newFile("rejected.tgz")
        val result = kotlinx.coroutines.runBlocking {
            storage.downloadBackup(connection(), "other/backup.tgz", destination, 1024) { _, _ -> }
        }
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `signer matches the AWS S3 published authorization example`() {
        val exampleSigner = AwsV4Signer().apply {
            clock = Clock.fixed(Instant.parse("2013-05-24T00:00:00Z"), ZoneOffset.UTC)
        }
        val request = exampleSigner.sign(
            connection = S3Connection(
                endpoint = "https://s3.amazonaws.com",
                accessKeyId = "AKIAIOSFODNN7EXAMPLE",
                secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                bucket = "examplebucket",
                region = "us-east-1",
                pathStyle = false,
                remoteDirectory = "AzureQL"
            ),
            region = "us-east-1",
            method = "GET",
            url = "https://examplebucket.s3.amazonaws.com/test.txt".toHttpUrl(),
            payloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            headers = mapOf("range" to "bytes=0-9")
        )

        assertTrue(
            request.header("Authorization").orEmpty().endsWith(
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"
            )
        )
    }

    @Test
    fun `validation rejects invalid bucket region and traversal`() {
        assertEquals(
            "S3 存储桶名称无效",
            validateS3Settings("https://s3.example.com", "bad bucket", "auto", "AzureQL")
        )
        assertEquals(
            "S3 区域格式无效",
            validateS3Settings("https://s3.example.com", "bucket", "US_EAST", "AzureQL")
        )
        assertEquals(
            "S3 远程目录不能包含 . 或 .. 路径段",
            validateS3Settings("https://s3.example.com", "bucket", "auto", "AzureQL/../other")
        )
    }

    private fun connection(region: String = "us-east-1") = S3Connection(
        endpoint = server.url("/").toString().trimEnd('/'),
        accessKeyId = "access",
        secretAccessKey = "secret",
        bucket = "backup-bucket",
        region = region,
        pathStyle = true,
        remoteDirectory = "AzureQL"
    )
}
