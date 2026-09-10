package com.autopanel.feature.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NetworkBackupStorageTest {
    @Test
    fun `stream copy enforces actual byte limit when content length is unknown`() {
        val output = ByteArrayOutputStream()

        val error = assertThrows(IllegalArgumentException::class.java) {
            copyBackupStream(
                input = ByteArrayInputStream(ByteArray(12)),
                output = output,
                totalBytes = null,
                maxBytes = 10,
                onProgress = { _, _ -> }
            )
        }

        assertEquals("备份数据超过大小上限，下载已中止", error.message)
        assertEquals(0, output.size())
    }

    @Test
    fun `remote file validation rejects traversal and unsupported suffix`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRemoteBackupFileName("../backup.tgz")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRemoteBackupFileName("notes.txt")
        }
        assertEquals("backup.tar.gz", validateRemoteBackupFileName("backup.tar.gz"))
    }

    @Test
    fun `network XML rejects doctypes and malformed utf8`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseNetworkStorageXml("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x/>".toByteArray())
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            parseNetworkStorageXml(byteArrayOf(0xC3.toByte(), 0x28))
        }
    }
}
