package com.autopanel.core.domain

import com.autopanel.core.model.SystemConfig

interface ConfigRepository {
    suspend fun getConfigContent(name: String = "config.sh"): Result<String>
    suspend fun saveConfig(name: String, content: String): Result<Unit>
    suspend fun getSystemConfig(): Result<SystemConfig>
    suspend fun updateSystemConfig(config: SystemConfig): Result<Unit>
}
