package com.autopanel.feature.backup

import java.io.IOException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupFailureMessageTest {

    @Test
    fun `invalid archive is classified without echoing source details`() {
        val message = safeBackupFailureMessage(
            IllegalArgumentException("invalid /data/ql/backup.tgz token=secret"),
            "上传备份失败"
        )

        assertEquals("备份格式错误，请选择有效且完整的 .tgz/.gz 文件", message)
        assertSensitiveDetailsAbsent(message)
    }

    @Test
    fun `network failure is classified without exposing url credentials`() {
        val message = safeBackupFailureMessage(
            IOException("https://admin:password@example.test/api?token=secret"),
            "上传备份失败"
        )

        assertEquals("网络连接失败，请检查服务器状态和网络后重试", message)
        assertSensitiveDetailsAbsent(message)
    }

    @Test
    fun `response parsing failure has a distinct safe message`() {
        val message = safeBackupFailureMessage(
            SerializationException("Unexpected token in /data/ql/db.sqlite"),
            "上传备份失败"
        )

        assertEquals("服务器响应解析失败，请确认青龙版本兼容", message)
        assertSensitiveDetailsAbsent(message)
    }

    @Test
    fun `http failure preserves only status code`() {
        val message = safeBackupFailureMessage(
            IllegalStateException("HTTP 500 at /data/ql?token=secret"),
            "数据激活失败"
        )

        assertEquals("数据激活失败（HTTP 500）", message)
        assertSensitiveDetailsAbsent(message)
    }

    @Test
    fun `unknown failure uses caller supplied safe fallback`() {
        val message = safeBackupFailureMessage(
            IllegalStateException("Bearer secret from C:\\ql\\data\\db.sqlite"),
            "备份任务失败"
        )

        assertEquals("备份任务失败", message)
        assertSensitiveDetailsAbsent(message)
    }

    private fun assertSensitiveDetailsAbsent(message: String) {
        listOf("secret", "password", "Bearer", "/data/", "C:\\").forEach { sensitive ->
            assertFalse(message.contains(sensitive, ignoreCase = true))
        }
    }
}
