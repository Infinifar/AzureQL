package com.autopanel.core.domain

import com.autopanel.core.model.DependencyInfo

interface DependencyRepository {
    suspend fun getDependencies(search: String = "", type: String = ""): Result<List<DependencyInfo>>
    suspend fun addDependency(name: String, type: String): Result<List<DependencyInfo>>
    suspend fun reinstallDependencies(ids: List<Int>): Result<List<DependencyInfo>>
    suspend fun deleteDependencies(ids: List<Int>): Result<List<DependencyInfo>>
    suspend fun getDependenceLog(id: Int): Result<String>
}
