package com.autopanel.core.data.script

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.data.session.SessionSnapshot
import com.autopanel.core.domain.ScriptDraft
import com.autopanel.core.model.ScriptFile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ScriptDraftStoreCodecTest {

    @Test
    fun `utf8 analysis and pages preserve every character after bom`() {
        val content = buildString {
            repeat(ScriptDraftStore.PAGE_CHARACTERS + 11) { index ->
                append(if (index % 5 == 0) '龙' else 'a')
            }
        }
        val file = Files.createTempFile("script-draft", ".py").toFile()
        try {
            file.writeBytes(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    content.toByteArray(Charsets.UTF_8)
            )

            val analysis = analyzeUtf8(file)
            val restored = buildString {
                repeat(analysis.pageCount) { page ->
                    append(readUtf8Page(file, analysis.hasUtf8Bom, page))
                }
            }

            assertTrue(analysis.isUtf8Valid)
            assertTrue(analysis.hasUtf8Bom)
            assertEquals(content.length.toLong(), analysis.characterCount)
            assertEquals(2, analysis.pageCount)
            assertEquals(content, restored)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `malformed utf8 is rejected instead of replacing characters`() {
        val file = Files.createTempFile("script-draft-invalid", ".py").toFile()
        try {
            file.writeBytes(byteArrayOf(0x61, 0xC3.toByte(), 0x28))

            val analysis = analyzeUtf8(file)

            assertFalse(analysis.isUtf8Valid)
            assertEquals(0L, analysis.characterCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `multipart upload streams cached bytes through a collision safe temporary filename`() = runTest {
        val cacheDirectory = Files.createTempDirectory("script-upload-cache").toFile()
        try {
            val context = mockk<Context>()
            every { context.cacheDir } returns cacheDirectory
            every { context.packageName } returns "com.autopanel.test"
            val store = ScriptDraftStore(context, mockk<SessionManager>())
            val token = "0123456789abcdef01234567-task.py"
            val draftDirectory = cacheDirectory.resolve("script-drafts").apply { mkdirs() }
            draftDirectory.resolve(token).writeText("print('dragon')", Charsets.UTF_8)
            val draft = ScriptDraft(
                cacheToken = token,
                filename = "task.py",
                path = "",
                sourceKey = "task.py",
                sizeBytes = 15,
                characterCount = 15,
                pageCount = 1,
                hasUtf8Bom = false,
                isUtf8Valid = true,
                editorUri = "content://test/task.py",
                sourceSizeBytes = 15,
                sourceModifiedTime = null,
                originalSha256 = "original"
            )

            val upload = store.createUpload(draft)
            val disposition = upload.part.headers?.get("Content-Disposition").orEmpty()
            val sink = Buffer()
            upload.part.body.writeTo(sink)

            assertTrue(upload.temporaryFilename.startsWith("azureql-"))
            assertTrue(upload.temporaryFilename.endsWith(".upload"))
            assertTrue(disposition.contains("name=\"file\""))
            assertTrue(disposition.contains("filename=\"azureql-"))
            assertTrue(disposition.contains(".upload\""))
            assertFalse(disposition.contains("filename=\"task.py\""))
            assertEquals("print('dragon')", sink.readUtf8())
        } finally {
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun `one MiB lf unicode script round trips without bom`() = runTest {
        assertLargeScriptRoundTrip(
            targetBytes = 1 * 1024 * 1024,
            line = "print('青龙🐉')\n",
            withBom = false
        )
    }

    @Test
    fun `five MiB crlf unicode script round trips with utf8 bom`() = runTest {
        assertLargeScriptRoundTrip(
            targetBytes = 5 * 1024 * 1024,
            line = "变量 = '青龙🐉'\r\n",
            withBom = true
        )
    }

    private suspend fun assertLargeScriptRoundTrip(
        targetBytes: Int,
        line: String,
        withBom: Boolean
    ) {
        val cacheDirectory = Files.createTempDirectory("script-round-trip").toFile()
        try {
            val context = mockk<Context>()
            every { context.cacheDir } returns cacheDirectory
            every { context.packageName } returns "com.autopanel.test"
            val editorUri = mockk<Uri>()
            every { editorUri.toString() } returns "content://com.autopanel.test.script-files/round-trip.py"
            mockkStatic(FileProvider::class)
            every {
                FileProvider.getUriForFile(context, "com.autopanel.test.script-files", any())
            } returns editorUri
            val sessionManager = mockk<SessionManager>()
            coEvery { sessionManager.getSession() } returns SessionSnapshot(
                host = "https://qinglong.example",
                username = "tester"
            )
            val store = ScriptDraftStore(context, sessionManager)
            val lineBytes = line.toByteArray(Charsets.UTF_8).size
            val content = line.repeat(targetBytes / lineBytes + 1)
            val contentBytes = content.toByteArray(Charsets.UTF_8)
            val sourceBytes = if (withBom) {
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + contentBytes
            } else {
                contentBytes
            }
            val draft = store.create(
                ScriptFile(title = "round-trip.py", key = "jobs/round-trip.py", parent = "jobs"),
                sourceBytes.toResponseBody("application/octet-stream".toMediaType())
            )

            assertEquals(content, store.readText(draft, ScriptDraftStore.MAX_EDITABLE_BYTES))
            assertEquals(withBom, draft.hasUtf8Bom)

            val edited = content + "# 完成🐉${if (line.endsWith("\r\n")) "\r\n" else "\n"}"
            val refreshed = store.replaceText(draft, edited, preserveUtf8Bom = withBom)
            val upload = store.createUpload(refreshed)
            val sink = Buffer()
            upload.part.body.writeTo(sink)
            val expectedBytes = if (withBom) {
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + edited.toByteArray(Charsets.UTF_8)
            } else {
                edited.toByteArray(Charsets.UTF_8)
            }

            assertEquals(edited, store.readText(refreshed, ScriptDraftStore.MAX_EDITABLE_BYTES))
            assertArrayEquals(expectedBytes, sink.readByteArray())
        } finally {
            unmockkStatic(FileProvider::class)
            cacheDirectory.deleteRecursively()
        }
    }
}
