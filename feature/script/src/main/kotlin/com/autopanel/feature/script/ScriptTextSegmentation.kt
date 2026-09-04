package com.autopanel.feature.script

const val MAX_GLYPHS_PER_LINE = 1024

internal fun wrapLongLines(text: String, maxGlyphsPerLine: Int = MAX_GLYPHS_PER_LINE): String {
    if (maxGlyphsPerLine <= 0 || text.isEmpty()) return text
    val result = StringBuilder(text.length + 16)
    var lineGlyphs = 0
    var index = 0
    var previousCodePoint = -1
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val charCount = Character.charCount(codePoint)
        if (codePoint == '\n'.code || codePoint == '\r'.code) {
            if (codePoint == '\r'.code && index + 1 < text.length && text[index + 1] == '\n') {
                result.append('\n')
                index += 2
            } else {
                result.append('\n')
                index += charCount
            }
            lineGlyphs = 0
            previousCodePoint = codePoint
            continue
        }
        if (lineGlyphs >= maxGlyphsPerLine && canBreakBefore(codePoint, previousCodePoint)) {
            result.append('\n')
            lineGlyphs = 0
        }
        result.appendCodePoint(codePoint)
        lineGlyphs++
        previousCodePoint = codePoint
        index += charCount
    }
    return result.toString()
}

private fun canBreakBefore(codePoint: Int, previousCodePoint: Int): Boolean {
    if (previousCodePoint < 0) return false
    if (previousCodePoint == ZERO_WIDTH_JOINER) return false
    if (isGraphemeExtend(codePoint)) return false
    if (isRegionalIndicator(codePoint) && isRegionalIndicator(previousCodePoint)) return false
    return true
}

private fun isGraphemeExtend(codePoint: Int): Boolean =
    codePoint == ZERO_WIDTH_JOINER ||
        codePoint in VARIATION_SELECTORS ||
        codePoint in VARIATION_SELECTOR_SUPPLEMENT ||
        codePoint in EMOJI_MODIFIERS ||
        codePoint in COMBINING_DIACRITICAL_MARKS ||
        codePoint in COMBINING_DIACRITICAL_MARKS_SUPPLEMENT ||
        codePoint in COMBINING_MARKS_FOR_SYMBOLS

private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

private const val ZERO_WIDTH_JOINER = 0x200D
private val VARIATION_SELECTORS = 0xFE00..0xFE0F
private val VARIATION_SELECTOR_SUPPLEMENT = 0xE0100..0xE01EF
private val EMOJI_MODIFIERS = 0x1F3FB..0x1F3FF
private val COMBINING_DIACRITICAL_MARKS = 0x0300..0x036F
private val COMBINING_DIACRITICAL_MARKS_SUPPLEMENT = 0x1DC0..0x1DFF
private val COMBINING_MARKS_FOR_SYMBOLS = 0x20D0..0x20FF
