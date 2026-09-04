package com.autopanel.feature.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupDocumentCleanupTest {
    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()

    @Before
    fun setUp() {
        mockkStatic(DocumentsContract::class)
        every { context.contentResolver } returns resolver
    }

    @After
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    @Test
    fun `document uri is deleted through DocumentsContract`() {
        every { DocumentsContract.isDocumentUri(context, uri) } returns true
        every { DocumentsContract.deleteDocument(resolver, uri) } returns true

        assertTrue(deleteBackupDocument(context, uri))

        verify(exactly = 1) { DocumentsContract.deleteDocument(resolver, uri) }
        verify(exactly = 0) { resolver.delete(any(), any(), any()) }
    }

    @Test
    fun `failed document deletion falls back to content resolver`() {
        every { DocumentsContract.isDocumentUri(context, uri) } returns true
        every { DocumentsContract.deleteDocument(resolver, uri) } throws SecurityException("denied")
        every { resolver.delete(uri, null, null) } returns 1

        assertTrue(deleteBackupDocument(context, uri))

        verify(exactly = 1) { resolver.delete(uri, null, null) }
    }

    @Test
    fun `cleanup failure is reported without throwing`() {
        every { DocumentsContract.isDocumentUri(context, uri) } returns false
        every { resolver.delete(uri, null, null) } returns 0

        assertFalse(deleteBackupDocument(context, uri))
    }
}
