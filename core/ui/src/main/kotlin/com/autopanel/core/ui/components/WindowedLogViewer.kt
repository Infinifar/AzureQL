package com.autopanel.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/**
 * Renders already-bounded server text in a small number of lazy blocks. The caller owns loading,
 * paging and the byte window; this component never translates or rewrites the supplied content.
 */
@Composable
fun WindowedLogViewer(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { content.toDisplayBlocks() }
    Box(modifier = modifier) {
        SelectionContainer {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = blocks,
                    key = { index, _ -> index }
                ) { _, block ->
                    Text(
                        text = block,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun String.toDisplayBlocks(targetChars: Int = DISPLAY_BLOCK_CHARS): List<String> {
    if (isEmpty()) return emptyList()
    val result = ArrayList<String>((length / targetChars) + 1)
    var start = 0
    while (start < length) {
        val targetEnd = (start + targetChars).coerceAtMost(length)
        val newlineEnd = if (targetEnd < length) indexOf('\n', targetEnd) else -1
        val end = if (newlineEnd >= 0 && newlineEnd - targetEnd <= MAX_NEWLINE_LOOKAHEAD) {
            newlineEnd + 1
        } else {
            targetEnd
        }
        result += substring(start, end)
        start = end
    }
    return result
}

private const val DISPLAY_BLOCK_CHARS = 4 * 1024
private const val MAX_NEWLINE_LOOKAHEAD = 512
