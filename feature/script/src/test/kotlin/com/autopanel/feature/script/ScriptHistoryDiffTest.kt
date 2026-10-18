package com.autopanel.feature.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptHistoryDiffTest {
    @Test
    fun `diff marks replaced line as removal and addition`() {
        val rows = buildScriptHistoryDiff("one\ntwo\nthree", "one\nnew\nthree")

        assertEquals(
            listOf(
                ScriptDiffKind.CONTEXT,
                ScriptDiffKind.REMOVED,
                ScriptDiffKind.ADDED,
                ScriptDiffKind.CONTEXT
            ),
            rows.map(ScriptDiffRow::kind)
        )
        assertTrue(rows.any { it.kind == ScriptDiffKind.ADDED && it.text == "new" })
        assertTrue(rows.any { it.kind == ScriptDiffKind.REMOVED && it.text == "two" })
    }

    @Test
    fun `identical content produces context rows only`() {
        val rows = buildScriptHistoryDiff("print(1)\n", "print(1)\n")

        assertTrue(rows.all { it.kind == ScriptDiffKind.CONTEXT })
    }
}
