package com.autopanel.feature.task

private val SCRIPT_SUFFIXES = setOf("py", "js", "mjs", "cjs", "ts", "sh", "bash")
private val SCRIPT_RUNNERS = setOf("task", "python", "python3", "node", "nodejs", "bash", "sh", "bun")

/** Returns a Scripts-module relative path only when one script target can be identified safely. */
internal fun resolveTaskScriptPath(command: String): String? {
    val tokens = tokenizeSingleCommand(command) ?: return null
    if (tokens.isEmpty()) return null

    val executable = tokens.first().replace('\\', '/').substringAfterLast('/').lowercase()
    val candidateTokens = when {
        executable in SCRIPT_RUNNERS -> tokens.drop(1)
        looksLikeScriptPath(tokens.first()) -> tokens
        else -> return null
    }
    val candidates = candidateTokens.filter(::looksLikeScriptPath)
    if (candidates.size != 1) return null
    return normalizeScriptPath(candidates.single())
}

private fun looksLikeScriptPath(value: String): Boolean {
    val clean = value.substringBefore('?').substringBefore('#')
    return clean.substringAfterLast('.', missingDelimiterValue = "").lowercase() in SCRIPT_SUFFIXES
}

private fun normalizeScriptPath(value: String): String? {
    var normalized = value.trim().substringBefore('?').substringBefore('#')
        .replace('\\', '/').replace(Regex("/+"), "/")
    normalized = when {
        normalized.startsWith("/ql/data/scripts/", ignoreCase = true) -> normalized.drop(17)
        normalized.startsWith("/ql/scripts/", ignoreCase = true) -> normalized.drop(12)
        normalized.startsWith("ql/data/scripts/", ignoreCase = true) -> normalized.drop(16)
        normalized.startsWith("data/scripts/", ignoreCase = true) -> normalized.drop(13)
        normalized.startsWith("scripts/", ignoreCase = true) -> normalized.drop(8)
        normalized.startsWith('/') -> return null
        else -> normalized
    }
    while (normalized.startsWith("./")) normalized = normalized.drop(2)
    if (normalized.matches(Regex("^[A-Za-z]:/.*")) || normalized.startsWith("~/")) return null
    val segments = normalized.split('/').filter(String::isNotEmpty)
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
    return segments.joinToString("/").takeIf(::looksLikeScriptPath)
}

private fun tokenizeSingleCommand(command: String): List<String>? {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaping = false

    fun flush() {
        if (current.isNotEmpty()) {
            tokens += current.toString()
            current.clear()
        }
    }

    command.trim().forEach { char ->
        if (escaping) {
            current.append(char)
            escaping = false
            return@forEach
        }
        when {
            quote == '"' && char == '\\' -> escaping = true
            quote != null && char == quote -> quote = null
            quote != null -> current.append(char)
            char == '\'' || char == '"' -> quote = char
            char.isWhitespace() -> flush()
            char in ";&|><" -> return null
            else -> current.append(char)
        }
    }
    if (escaping || quote != null) return null
    flush()
    return tokens
}
