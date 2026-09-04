package com.autopanel.feature.script

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptTextSegmentationTest {

    @Test
    fun `long single line is split at fixed glyph boundary`() {
        assertEquals("abcd\nefgh", wrapLongLines("abcdefgh", 4))
        assertEquals("abcdefgh", wrapLongLines("abcdefgh", 100))
    }

    @Test
    fun `lf and crlf newlines are preserved as visual line breaks`() {
        assertEquals("a\nb\nc", wrapLongLines("a\nb\nc", 100))
        assertEquals("a\nb", wrapLongLines("a\r\nb", 100))
    }

    @Test
    fun `surrogate pair emoji is never split in half`() {
        assertEquals("\uD83D\uDE00\n\uD83D\uDE00", wrapLongLines("\uD83D\uDE00\uD83D\uDE00", 1))
    }

    @Test
    fun `zwj family emoji and skin tone modifiers stay together`() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val thumbsUp = "\uD83D\uDC4D\uD83C\uDFFD"
        assertEquals(family, wrapLongLines(family, 1))
        assertEquals(thumbsUp, wrapLongLines(thumbsUp, 1))
    }

    @Test
    fun `regional indicator flag pair is not split`() {
        val flag = "\uD83C\uDDFA\uD83C\uDDF8"
        assertEquals(flag, wrapLongLines(flag, 1))
    }

    @Test
    fun `chinese characters survive wrapping unchanged`() {
        assertEquals("青龙面板\n青龙面板", wrapLongLines("青龙面板青龙面板", 4))
    }

    @Test
    fun `combined content preserves every glyph in order`() {
        val input = "龙🐉a\nbcd\r\nefg"
        val wrapped = wrapLongLines(input, 100)
        val stripped = wrapped.replace("\n", "")
        assertEquals("龙🐉abcdefg", stripped)
    }
}
