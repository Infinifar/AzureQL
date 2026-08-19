package com.autopanel.core.domain

import com.autopanel.core.model.SystemConfig
import com.autopanel.core.model.DependencyCacheType
import com.autopanel.core.model.DependencyMirrorEvent
import com.autopanel.core.model.DependencySetting
import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    suspend fun getConfigContent(name: String = "config.sh"): Result<String>
    suspend fun saveConfig(name: String, content: String): Result<Unit>
    suspend fun getSystemConfig(): Result<SystemConfig>
    suspend fun updateSystemConfig(config: SystemConfig): Result<Unit>
    suspend fun updateDependencySetting(setting: DependencySetting, value: String): Result<Unit>
    fun observeDependencyMirrorTasks(): Flow<DependencyMirrorEvent>
    suspend fun cleanDependencyCache(type: DependencyCacheType): Result<Unit>
}
