package com.autopanel.core.data.repository

import com.autopanel.core.data.remote.AutoPanelApiService
import com.autopanel.core.domain.ConfigRepository
import com.autopanel.core.model.DependencyCacheType
import com.autopanel.core.model.SystemConfig
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val apiProvider: Provider<AutoPanelApiService>
) : ConfigRepository {

    private val api: AutoPanelApiService
        get() = apiProvider.get()

    override suspend fun getConfigContent(name: String): Result<String> {
        return try {
            val res = api.getConfigContent(name)
            if (res.code == 200) Result.success(res.data ?: "")
            else Result.failure(Exception(res.message ?: "获取配置内容失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun saveConfig(name: String, content: String): Result<Unit> {
        return try {
            val res = api.saveConfig(mapOf("name" to name, "content" to content))
            if (res.code == 200) Result.success(Unit)
            else Result.failure(Exception(res.message ?: "保存配置失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun updateDependencySettings(config: SystemConfig): Result<Unit> {
        return try {
            val proxy = api.updateDependenceProxy(
                mapOf("dependenceProxy" to config.dependenceProxy.orEmpty())
            )
            if (proxy.code != 200) {
                return Result.failure(Exception(proxy.message ?: "更新依赖代理失败"))
            }

            val node = api.updateNodeMirror(mapOf("nodeMirror" to config.nodeMirror.orEmpty()))
            node.body()?.close()
            node.errorBody()?.close()
            if (!node.isSuccessful) {
                return Result.failure(Exception("更新 Node.js 镜像失败（HTTP ${node.code()}）"))
            }

            val python = api.updatePythonMirror(
                mapOf("pythonMirror" to config.pythonMirror.orEmpty())
            )
            if (python.code != 200) {
                return Result.failure(Exception(python.message ?: "更新 Python 镜像失败"))
            }

            val linux = api.updateLinuxMirror(mapOf("linuxMirror" to config.linuxMirror.orEmpty()))
            linux.body()?.close()
            linux.errorBody()?.close()
            if (!linux.isSuccessful) {
                return Result.failure(Exception("更新 Linux 镜像失败（HTTP ${linux.code()}）"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun cleanDependencyCache(type: DependencyCacheType): Result<Unit> {
        return try {
            val response = api.cleanDependence(mapOf("type" to type.apiValue))
            if (response.code == 200) Result.success(Unit)
            else Result.failure(Exception(response.message ?: "清理依赖缓存失败"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}
