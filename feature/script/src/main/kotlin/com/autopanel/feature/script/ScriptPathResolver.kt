package com.autopanel.feature.script

import com.autopanel.core.model.ScriptFile

internal fun ScriptFile.currentScriptPath(): String {
    val rawPath = key?.takeIf(String::isNotBlank)
        ?: listOfNotNull(parent?.takeIf(String::isNotBlank), title?.takeIf(String::isNotBlank))
            .joinToString("/")
    return normalizeScriptManagerPath(rawPath)
}

internal fun ScriptFile.scriptActionKey(): String =
    "${if (isDirectory) "directory" else "file"}:${currentScriptPath()}"

internal fun findScriptByPath(files: List<ScriptFile>, requestedPath: String): ScriptFile? {
    val target = normalizeScriptManagerPath(requestedPath)
    if (target.isBlank()) return null

    fun find(nodes: List<ScriptFile>, inheritedParent: String): ScriptFile? {
        nodes.forEach { node ->
            val explicitPath = node.key?.takeIf(String::isNotBlank)?.let(::normalizeScriptManagerPath)
            val declaredPath = node.parent?.takeIf(String::isNotBlank)?.let { parent ->
                normalizeScriptManagerPath("$parent/${node.title.orEmpty()}")
            }
            val inferredPath = normalizeScriptManagerPath(
                listOf(inheritedParent, node.title.orEmpty()).filter(String::isNotBlank).joinToString("/")
            )
            val actualPath = explicitPath ?: declaredPath ?: inferredPath
            if (!node.isDirectory && actualPath == target) {
                return node.copy(
                    key = node.key ?: actualPath,
                    parent = actualPath.substringBeforeLast('/', missingDelimiterValue = "")
                )
            }
            if (node.isDirectory) {
                find(node.children.orEmpty(), actualPath)?.let { return it }
            }
        }
        return null
    }

    return find(files, "")
}

private fun normalizeScriptManagerPath(rawPath: String): String = rawPath
    .trim()
    .replace('\\', '/')
    .replace(Regex("/+"), "/")
    .removePrefix("./")
    .trimStart('/')
