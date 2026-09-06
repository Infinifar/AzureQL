package com.autopanel.feature.script

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import io.github.dingyi222666.monarch.languages.JavascriptLanguage
import io.github.dingyi222666.monarch.languages.PythonLanguage
import io.github.dingyi222666.monarch.languages.ShellLanguage
import io.github.dingyi222666.monarch.languages.TypescriptLanguage
import io.github.dingyi222666.monarch.languages.YamlLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.widget.CodeEditor
import java.util.Locale

internal enum class ScriptLanguageMode(
    val displayName: String,
    val scopeName: String?
) {
    PYTHON("Python", "source.python"),
    JAVASCRIPT("JavaScript", "source.js"),
    TYPESCRIPT("TypeScript", "source.ts"),
    SHELL("Shell", "source.shell"),
    JSON("JSON", "source.js"),
    YAML("YAML", "source.yaml"),
    PLAIN_TEXT("Text", null)
}

internal fun detectScriptLanguage(filename: String): ScriptLanguageMode {
    val extension = filename.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    return when (extension) {
        "py" -> ScriptLanguageMode.PYTHON
        "js", "mjs", "cjs" -> ScriptLanguageMode.JAVASCRIPT
        "ts" -> ScriptLanguageMode.TYPESCRIPT
        "sh", "bash" -> ScriptLanguageMode.SHELL
        "json" -> ScriptLanguageMode.JSON
        "yml", "yaml" -> ScriptLanguageMode.YAML
        else -> ScriptLanguageMode.PLAIN_TEXT
    }
}

@Composable
internal fun SoraCodeEditor(
    value: String,
    languageMode: ScriptLanguageMode,
    editable: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val theme = remember(
        colors.surface,
        colors.onSurface,
        colors.primary,
        colors.secondary,
        colors.tertiary,
        colors.onSurfaceVariant
    ) {
        createEditorTheme(
            background = colors.surface,
            foreground = colors.onSurface,
            keyword = colors.primary,
            number = colors.secondary,
            string = colors.tertiary,
            comment = colors.onSurfaceVariant
        )
    }

    LaunchedEffect(editable) {
        if (!editable) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    AndroidView(
        factory = { context ->
            ScriptCodeEditorView(context).apply {
                SoraScriptLanguages.ensureRegistered()
                setTextSize(14f)
                typefaceText = Typeface.MONOSPACE
                setLineNumberEnabled(true)
                setPinLineNumber(true)
                setWordwrap(false)
                setTabWidth(4)
                setLineSpacing(2f, 1.05f)
                setBlockLineEnabled(true)
                subscribeAlways(ContentChangeEvent::class.java) { event ->
                    if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                        latestOnValueChange(text.toString())
                    }
                }
            }
        },
        modifier = modifier,
        update = { editor ->
            editor.setEditable(editable)
            if (!editable) {
                editor.clearFocus()
                editor.hideSoftInput()
            }
            if (editor.appliedThemeName != theme.name) {
                editor.colorScheme = MonarchColorScheme.create(theme)
                editor.appliedThemeName = theme.name
            }
            if (editor.appliedLanguageMode != languageMode) {
                editor.setEditorLanguage(
                    languageMode.scopeName?.let { MonarchLanguage.create(it, false) }
                        ?: EmptyLanguage()
                )
                editor.appliedLanguageMode = languageMode
            }
            if (editor.text.toString() != value) {
                val selection = editor.cursor.left.coerceAtMost(value.length)
                editor.setText(value)
                val position = editor.text.indexer.getCharPosition(selection)
                editor.setSelection(position.line, position.column)
            }
        },
        onRelease = CodeEditor::release
    )
}

private class ScriptCodeEditorView(context: Context) : CodeEditor(context) {
    var appliedLanguageMode: ScriptLanguageMode? = null
    var appliedThemeName: String? = null
}

private object SoraScriptLanguages {
    @Volatile
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            MonarchGrammarRegistry.INSTANCE.loadGrammars(
                monarchLanguages {
                    language("python") {
                        monarchLanguage = PythonLanguage
                        scopeName = ScriptLanguageMode.PYTHON.scopeName
                    }
                    language("javascript") {
                        monarchLanguage = JavascriptLanguage
                        scopeName = ScriptLanguageMode.JAVASCRIPT.scopeName
                    }
                    language("typescript") {
                        monarchLanguage = TypescriptLanguage
                        scopeName = ScriptLanguageMode.TYPESCRIPT.scopeName
                    }
                    language("shell") {
                        monarchLanguage = ShellLanguage
                        scopeName = ScriptLanguageMode.SHELL.scopeName
                    }
                    language("yaml") {
                        monarchLanguage = YamlLanguage
                        scopeName = ScriptLanguageMode.YAML.scopeName
                    }
                }
            )
            registered = true
        }
    }
}

private fun createEditorTheme(
    background: Color,
    foreground: Color,
    keyword: Color,
    number: Color,
    string: Color,
    comment: Color
): ThemeSource {
    val name = buildString {
        append("azureql-")
        append(background.toRgbHex())
        append('-')
        append(foreground.toRgbHex())
        append('-')
        append(keyword.toRgbHex())
        append('-')
        append(string.toRgbHex())
    }
    val selection = lerp(background, keyword, 0.25f)
    val currentLine = lerp(background, keyword, 0.08f)
    val rawTheme = """
        {
          "name": "$name",
          "type": "${if (background.luminance() < 0.5f) "dark" else "light"}",
          "colors": {
            "editor.background": "${background.toRgbHex()}",
            "editor.foreground": "${foreground.toRgbHex()}",
            "editorLineNumber.foreground": "${comment.toRgbHex()}",
            "editorLineNumber.activeForeground": "${foreground.toRgbHex()}",
            "editorCursor.foreground": "${keyword.toRgbHex()}",
            "editor.selectionBackground": "${selection.toRgbHex()}",
            "editor.lineHighlightBackground": "${currentLine.toRgbHex()}"
          },
          "tokenColors": [
            { "settings": { "foreground": "${foreground.toRgbHex()}", "background": "${background.toRgbHex()}" } },
            { "scope": ["comment"], "settings": { "foreground": "${comment.toRgbHex()}" } },
            { "scope": ["keyword", "type", "tag"], "settings": { "foreground": "${keyword.toRgbHex()}" } },
            { "scope": ["number", "constant", "boolean"], "settings": { "foreground": "${number.toRgbHex()}" } },
            { "scope": ["string", "attribute.value"], "settings": { "foreground": "${string.toRgbHex()}" } },
            { "scope": ["operator", "delimiter"], "settings": { "foreground": "${foreground.toRgbHex()}" } }
          ]
        }
    """.trimIndent()
    return ThemeSource(path = name, name = name, rawSource = rawTheme)
}

private fun Color.toRgbHex(): String = String.format(
    Locale.ROOT,
    "#%06X",
    toArgb() and 0x00FFFFFF
)

private fun Color.luminance(): Float =
    (red * 0.299f) + (green * 0.587f) + (blue * 0.114f)
