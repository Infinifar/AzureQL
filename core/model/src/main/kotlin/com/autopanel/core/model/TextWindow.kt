package com.autopanel.core.model

import java.nio.charset.StandardCharsets

const val MAX_LOG_WINDOW_BYTES: Int = 256 * 1024

/** A UTF-8-safe bounded view over server text. The content itself is never rewritten. */
data class TextWindowSlice(
    val content: String,
    val contentBytes: Int,
    val droppedPrefixBytes: Int = 0,
    val droppedSuffixBytes: Int = 0
) {
    val truncated: Boolean get() = droppedPrefixBytes > 0 || droppedSuffixBytes > 0
}

fun String.boundedUtf8Tail(maxBytes: Int = MAX_LOG_WINDOW_BYTES): TextWindowSlice {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val bytes = toByteArray(StandardCharsets.UTF_8)
    if (bytes.size <= maxBytes) return TextWindowSlice(this, bytes.size)

    var start = bytes.size - maxBytes
    while (start < bytes.size && bytes[start].toInt() and UTF8_CONTINUATION_MASK == UTF8_CONTINUATION) {
        start++
    }
    return TextWindowSlice(
        content = String(bytes, start, bytes.size - start, StandardCharsets.UTF_8),
        contentBytes = bytes.size - start,
        droppedPrefixBytes = start
    )
}

fun String.boundedUtf8Head(maxBytes: Int = MAX_LOG_WINDOW_BYTES): TextWindowSlice {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val bytes = toByteArray(StandardCharsets.UTF_8)
    if (bytes.size <= maxBytes) return TextWindowSlice(this, bytes.size)

    var end = maxBytes
    while (end > 0 && bytes[end].toInt() and UTF8_CONTINUATION_MASK == UTF8_CONTINUATION) {
        end--
    }
    return TextWindowSlice(
        content = String(bytes, 0, end, StandardCharsets.UTF_8),
        contentBytes = end,
        droppedSuffixBytes = bytes.size - end
    )
}

private const val UTF8_CONTINUATION_MASK = 0xC0
private const val UTF8_CONTINUATION = 0x80
