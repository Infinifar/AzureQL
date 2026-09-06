package com.autopanel.feature.script

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptLanguageModeTest {

    @Test
    fun `detects supported script extensions case insensitively`() {
        val expected = mapOf(
            "demo.PY" to ScriptLanguageMode.PYTHON,
            "demo.js" to ScriptLanguageMode.JAVASCRIPT,
            "demo.mjs" to ScriptLanguageMode.JAVASCRIPT,
            "demo.cjs" to ScriptLanguageMode.JAVASCRIPT,
            "demo.TS" to ScriptLanguageMode.TYPESCRIPT,
            "demo.sh" to ScriptLanguageMode.SHELL,
            "demo.bash" to ScriptLanguageMode.SHELL,
            "demo.json" to ScriptLanguageMode.JSON,
            "demo.yml" to ScriptLanguageMode.YAML,
            "demo.YAML" to ScriptLanguageMode.YAML
        )

        expected.forEach { (filename, mode) ->
            assertEquals(filename, mode, detectScriptLanguage(filename))
        }
    }

    @Test
    fun `uses plain text for unknown extension or extensionless file`() {
        assertEquals(ScriptLanguageMode.PLAIN_TEXT, detectScriptLanguage("config.toml"))
        assertEquals(ScriptLanguageMode.PLAIN_TEXT, detectScriptLanguage("Dockerfile"))
        assertEquals(ScriptLanguageMode.PLAIN_TEXT, detectScriptLanguage(".env"))
    }
}
