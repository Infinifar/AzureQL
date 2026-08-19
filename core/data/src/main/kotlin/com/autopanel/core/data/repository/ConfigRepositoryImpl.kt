package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.model.SystemConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val api: AutoPanelApiService
) : ConfigRepository {

    override suspend fun getConfigContent(name: String): Result<String> {
        return try {
            val res = api.getConfigContent(name)
            if (res.code == 200) Result.success(res.data ?: "")
            else Result.failure(Exception(res.message ?: "获取配置内容失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveConfig(name: String, content: String): Result<Unit> {
        return try {
            val res = api.saveConfig(mapOf("name" to name, "content" to content))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "保存配置失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSystemConfig(): Result<SystemConfig> {
        return try {
            val res = api.getSystemConfig()
            if (res.code == 200) {
                val configData = res.data
                val info = configData?.info
                if (info != null) Result.success(info)
                else Result.failure(Exception(res.message ?: "系统配置为空"))
            } else {
                Result.failure(Exception(res.message ?: "获取系统配置失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSystemConfig(config: SystemConfig): Result<Unit> {
        return try {
            config.logRemoveFrequency?.let {
                val res = api.updateLogRemoveFrequency(mapOf("logRemoveFrequency" to it))
                if (res.code != 200) return Result.failure(Exception(res.message ?: "更新日志频率失败"))
            }
            config.cronConcurrency?.let {
                val res = api.updateCronConcurrency(mapOf("cronConcurrency" to it))
                if (res.code != 200) return Result.failure(Exception(res.message ?: "更新并发数失败"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
