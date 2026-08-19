package com.autopanel.feature.script

import kotlinx.serialization.Serializable

@Serializable
data object ScriptRoute

@Serializable
data class ScriptEditorRoute(
    val filename: String,
    val path: String = ""
)
