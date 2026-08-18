package com.qinglong.core.domain

import com.qinglong.core.model.DependencyInfo

interface DependencyRepository {
    suspend fun getDependencies(search: String = "", type: String = ""): Result<List<DependencyInfo>>
    suspend fun addDependencies(deps: List<Pair<String, String>>): Result<Unit>
    suspend fun reinstallDependencies(ids: List<Int>): Result<Unit>
    suspend fun deleteDependencies(ids: List<Int>): Result<Unit>
    suspend fun getDependenceLog(id: Int): Result<String>
}
