package com.autopanel.core.data.script

import android.content.Context
import com.autopanel.core.data.session.SessionManager
import com.autopanel.core.domain.ScriptDraft
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Buffer
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
}
