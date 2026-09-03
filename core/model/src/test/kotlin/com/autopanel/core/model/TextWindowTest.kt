package com.autopanel.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextWindowTest {
    @Test
    fun `tail preserves utf8 boundaries and reports dropped bytes`() {
        val result = "前-abc-后".boundedUtf8Tail(maxBytes = 6)

        assertEquals("bc-后", result.content)
        assertEquals(6, result.contentBytes)
        assertEquals(5, result.droppedPrefixBytes)
        assertTrue(result.truncated)
    }

    @Test
    fun `head preserves utf8 boundaries`() {
        val result = "前-abc-后".boundedUtf8Head(maxBytes = 7)

        assertEquals("前-abc", result.content)
        assertEquals(7, result.contentBytes)
        assertEquals(4, result.droppedSuffixBytes)
    }

    @Test
    fun `small input is returned unchanged`() {
        val result = "原始 server output\n".boundedUtf8Tail()

        assertEquals("原始 server output\n", result.content)
        assertFalse(result.truncated)
    }
}
