package com.autopanel.app.navigation

import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object HomeRoute
@Serializable data class ScriptsRoute(
    val openScriptPath: String? = null,
    val requestId: Long = 0L
)
@Serializable object EnvRoute
@Serializable object SettingsRoute
@Serializable object ConfigRoute
