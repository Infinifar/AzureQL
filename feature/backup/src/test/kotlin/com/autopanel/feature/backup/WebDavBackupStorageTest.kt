package com.autopanel.feature.backup

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WebDavBackupStorageTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: OkHttpWebDavBackupStorage

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = OkHttpWebDavBackupStorage(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `test connection creates missing nested directory with encoded segments`() = runTest {
        server.enqueue(MockResponse().setResponseCode(207))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))

        val result = storage.testConnection(connection("AzureQL/每日备份"))

        assertTrue(result.isSuccess)
        assertEquals("PROPFIND", server.takeRequest().method)
        server.takeRequest().also {
            assertEquals("PROPFIND", it.method)
            assertEquals("/dav/AzureQL", it.path)
        }
        assertEquals("MKCOL", server.takeRequest().method)
        server.takeRequest().also {
            assertEquals("PROPFIND", it.method)
            assertEquals("/dav/AzureQL/%E6%AF%8F%E6%97%A5%E5%A4%87%E4%BB%BD", it.path)
        }
        assertEquals("MKCOL", server.takeRequest().method)
    }

    @Test
    fun `upload streams archive with basic auth and reports completion`() = runTest {
        server.enqueue(MockResponse().setResponseCode(207))
        server.enqueue(MockResponse().setResponseCode(201))
        val source = File.createTempFile("webdav-upload", ".tgz").apply {
            writeBytes(ByteArray(96 * 1024) { (it % 251).toByte() })
        }
        var progress = 0L

        try {
            val result = storage.uploadBackup(
                connection = connection("AzureQL"),
                source = source,
                fileName = "azureql_backup_20260906.tgz",
                onProgress = { transferred, _ -> progress = transferred }
            )

            assertTrue(result.isSuccess)
            server.takeRequest().also { assertEquals("PROPFIND", it.method) }
            server.takeRequest().also { request ->
                assertEquals("PUT", request.method)
                assertEquals("/dav/AzureQL/azureql_backup_20260906.tgz", request.path)
                assertTrue(request.getHeader("Authorization").orEmpty().startsWith("Basic "))
                assertEquals(source.length(), request.bodySize)
            }
            assertEquals(source.length(), progress)
        } finally {
            source.delete()
        }
    }

    @Test
    fun `failed upload deletes partial remote file and keeps http status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(207))
        server.enqueue(MockResponse().setResponseCode(507))
        server.enqueue(MockResponse().setResponseCode(204))
        val source = File.createTempFile("webdav-failed-upload", ".tgz").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        try {
            val result = storage.uploadBackup(
                connection = connection("AzureQL"),
                source = source,
                fileName = "azureql_backup_failed.tgz",
                onProgress = { _, _ -> }
            )

            assertTrue(result.isFailure)
            assertEquals(507, (result.exceptionOrNull() as WebDavHttpException).statusCode)
            assertEquals(
                "WebDAV 存储空间不足",
                webDavFailureMessage(result.exceptionOrNull(), "上传到网络存储失败")
            )
            assertEquals("PROPFIND", server.takeRequest().method)
            assertEquals("PUT", server.takeRequest().method)
            server.takeRequest().also { request ->
                assertEquals("DELETE", request.method)
                assertEquals("/dav/AzureQL/azureql_backup_failed.tgz", request.path)
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun `authentication failure has a credential-specific safe message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = storage.testConnection(connection("AzureQL"))

        assertTrue(result.isFailure)
        assertEquals(
            "WebDAV 认证失败，请检查用户名和密码",
            webDavFailureMessage(result.exceptionOrNull(), "WebDAV 连接测试失败")
        )
    }

    @Test
    fun `list returns only immediate supported backups and download streams selected file`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <D:multistatus xmlns:D="DAV:">
                  <D:response><D:href>/dav/AzureQL/</D:href></D:response>
                  <D:response><D:href>azureql_backup_20260910.tgz</D:href>
                    <D:propstat><D:prop><D:getcontentlength>7</D:getcontentlength>
                    <D:getlastmodified>Thu, 10 Sep 2026 08:00:00 GMT</D:getlastmodified></D:prop></D:propstat>
                  </D:response>
                  <D:response><D:href>/dav/AzureQL/readme.txt</D:href></D:response>
                  <D:response><D:href>/dav/AzureQL/bad%5Cname.tgz</D:href></D:response>
                  <D:response><D:href>/dav/AzureQL/nested/hidden.tgz</D:href></D:response>
                </D:multistatus>
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("archive"))
        val destination = File.createTempFile("webdav-download", ".tgz")

        try {
            val files = storage.listBackups(connection("AzureQL")).getOrThrow()
            assertEquals(1, files.size)
            assertEquals("azureql_backup_20260910.tgz", files.single().remoteId)
            assertEquals(7L, files.single().sizeBytes)

            storage.downloadBackup(
                connection("AzureQL"),
                files.single().remoteId,
                destination,
                1024
            ) { _, _ -> }.getOrThrow()

            server.takeRequest().also { request ->
                assertEquals("PROPFIND", request.method)
                assertEquals("1", request.getHeader("Depth"))
            }
            server.takeRequest().also { request ->
                assertEquals("GET", request.method)
                assertEquals("/dav/AzureQL/azureql_backup_20260910.tgz", request.path)
            }
            assertEquals("archive", destination.readText())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `download rejects a remote path outside configured directory`() = runTest {
        val destination = File.createTempFile("webdav-reject", ".tgz")
        try {
            val result = storage.downloadBackup(
                connection("AzureQL"),
                "../other/backup.tgz",
                destination,
                1024
            ) { _, _ -> }
            assertTrue(result.isFailure)
            assertEquals(0, server.requestCount)
        } finally {
            destination.delete()
        }
    }

    @Test
    fun `validation rejects traversal and accepts http webdav`() {
        assertEquals(
            "远程目录不能包含 . 或 .. 路径段",
            validateWebDavSettings("https://dav.example.com", "alice", "AzureQL/../other")
        )
        assertNull(validateWebDavSettings("http://192.168.1.2:8080/dav", "", "AzureQL"))
    }

    private fun connection(directory: String) = WebDavConnection(
        serverUrl = server.url("/dav").toString().trimEnd('/'),
        username = "alice",
        password = "secret",
        remoteDirectory = directory
    )
}
