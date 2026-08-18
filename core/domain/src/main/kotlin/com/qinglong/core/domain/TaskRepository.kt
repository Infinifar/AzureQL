package com.qinglong.core.domain

import com.qinglong.core.model.*

interface TaskRepository {
    suspend fun getTasks(search: String = "", page: Int = 1, size: Int = 50): Result<Pair<List<TaskInfo>, Int>>
    suspend fun addTask(name: String, command: String, schedule: String): Result<Unit>
    suspend fun updateTask(id: Int, name: String, command: String, schedule: String): Result<Unit>
    suspend fun deleteTasks(ids: List<Int>): Result<Unit>
    suspend fun runTasks(ids: List<Int>): Result<Unit>
    suspend fun stopTasks(ids: List<Int>): Result<Unit>
    suspend fun enableTasks(ids: List<Int>): Result<Unit>
    suspend fun disableTasks(ids: List<Int>): Result<Unit>
    suspend fun pinTasks(ids: List<Int>): Result<Unit>
    suspend fun unpinTasks(ids: List<Int>): Result<Unit>
    suspend fun getTaskLog(id: Int): Result<String>
}
