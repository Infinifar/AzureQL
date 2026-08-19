package com.autopanel.core.domain

import com.autopanel.core.model.EnvInfo

interface EnvRepository {
    suspend fun getEnvs(search: String = ""): Result<List<EnvInfo>>
    suspend fun addEnvs(envs: List<Triple<String, String, String?>>): Result<Unit>
    suspend fun updateEnv(id: Int, name: String, value: String, remarks: String?): Result<Unit>
    suspend fun deleteEnvs(ids: List<Int>): Result<Unit>
    suspend fun enableEnvs(ids: List<Int>): Result<Unit>
    suspend fun disableEnvs(ids: List<Int>): Result<Unit>
}
