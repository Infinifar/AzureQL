package com.qinglong.core.data.remote

import com.qinglong.core.model.*
import okhttp3.RequestBody
import retrofit2.http.*

interface QLApiService {

    // ── Health ──
    @GET("api/health")
    suspend fun healthCheck(): ApiResponse<Unit>

    // ── Auth ──
    @POST("api/user/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginData>

    @POST("api/user/logout")
    suspend fun logout(): ApiResponse<Unit>

    @PUT("api/user/two-factor/login")
    suspend fun loginTwoFactor(@Body request: TwoFactorRequest): ApiResponse<LoginData>

    // ── User ──
    @GET("api/user")
    suspend fun getUserInfo(): ApiResponse<UserInfo>

    @PUT("api/user")
    suspend fun updateAccount(@Body body: Map<String, String>): ApiResponse<Unit>

    @GET("api/user/login-log")
    suspend fun getLoginLogs(): ApiResponse<List<LoginLogEntry>>

    // ── System ──
    @GET("api/system")
    suspend fun getSystemInfo(): ApiResponse<SystemInfo>

    @GET("api/system/config")
    suspend fun getSystemConfig(): ApiResponse<SystemConfigData>

    @PUT("api/system/config/log-remove-frequency")
    suspend fun updateLogRemoveFrequency(@Body body: Map<String, Int>): ApiResponse<Unit>

    @PUT("api/system/config/cron-concurrency")
    suspend fun updateCronConcurrency(@Body body: Map<String, Int>): ApiResponse<Unit>

    // ── Tasks ──
    @GET("api/crons")
    suspend fun getTasks(
        @Query("searchValue") search: String = "",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): ApiResponse<TaskListData>

    @GET("api/crons/{id}")
    suspend fun getTaskDetail(@Path("id") id: Int): ApiResponse<TaskInfo>

    @POST("api/crons")
    suspend fun addTask(@Body body: TaskCreateRequest): ApiResponse<TaskInfo>

    @PUT("api/crons")
    suspend fun updateTask(@Body body: TaskUpdateRequest): ApiResponse<TaskInfo>

    @HTTP(method = "DELETE", path = "api/crons", hasBody = true)
    suspend fun deleteTasks(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/crons/run")
    suspend fun runTasks(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/crons/stop")
    suspend fun stopTasks(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/crons/enable")
    suspend fun enableTasks(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/crons/disable")
    suspend fun disableTasks(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/crons/pin")
    suspend fun pinTasks(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/crons/unpin")
    suspend fun unpinTasks(@Body ids: List<Int>): ApiResponse<Unit>

    @GET("api/crons/{id}/log")
    suspend fun getTaskLog(@Path("id") id: Int): ApiResponse<String>

    // ── Environments ──
    @GET("api/envs")
    suspend fun getEnvs(
        @Query("searchValue") search: String = ""
    ): ApiResponse<List<EnvInfo>>

    @POST("api/envs")
    suspend fun addEnvs(@Body body: List<EnvCreateRequest>): ApiResponse<List<EnvInfo>>

    @PUT("api/envs")
    suspend fun updateEnv(@Body body: EnvUpdateRequest): ApiResponse<EnvInfo>

    @HTTP(method = "DELETE", path = "api/envs", hasBody = true)
    suspend fun deleteEnvs(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/envs/enable")
    suspend fun enableEnvs(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/envs/disable")
    suspend fun disableEnvs(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/envs/pin")
    suspend fun pinEnvs(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/envs/unpin")
    suspend fun unpinEnvs(@Body ids: List<Int>): ApiResponse<Unit>

    @POST("api/envs/upload")
    suspend fun uploadEnvFile(@Body body: RequestBody): ApiResponse<Unit>

    // ── Scripts ──
    @GET("api/scripts")
    suspend fun getScripts(): ApiResponse<List<ScriptFile>>

    @POST("api/scripts")
    suspend fun addScript(@Body body: ScriptAddRequest): ApiResponse<Unit>

    @PUT("api/scripts")
    suspend fun updateScript(@Body body: ScriptUpdateRequest): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "api/scripts", hasBody = true)
    suspend fun deleteScript(@Body body: ScriptDeleteRequest): ApiResponse<Unit>

    @GET("api/scripts/{filename}")
    suspend fun getScriptContent(
        @Path("filename") filename: String,
        @Query("path") path: String = ""
    ): ApiResponse<String>

    @PUT("api/scripts/run")
    suspend fun runScript(@Body body: ScriptDeleteRequest): ApiResponse<Unit>

    @PUT("api/scripts/stop")
    suspend fun stopScript(@Body body: ScriptDeleteRequest): ApiResponse<Unit>

    @PUT("api/scripts/rename")
    suspend fun renameScript(@Body body: Map<String, String>): ApiResponse<Unit>

    @POST("api/scripts/download")
    suspend fun downloadScript(@Body body: ScriptDeleteRequest): ApiResponse<Unit>

    // ── Dependencies ──
    @GET("api/dependencies")
    suspend fun getDependencies(
        @Query("searchValue") search: String = "",
        @Query("type") type: String = ""
    ): ApiResponse<List<DependencyInfo>>

    @POST("api/dependencies")
    suspend fun addDependencies(@Body body: List<DependencyCreateRequest>): ApiResponse<List<DependencyInfo>>

    @PUT("api/dependencies")
    suspend fun updateDependency(@Body body: DependencyUpdateRequest): ApiResponse<DependencyInfo>

    @PUT("api/dependencies/reinstall")
    suspend fun reinstallDependencies(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/dependencies/cancel")
    suspend fun cancelDependency(@Body ids: List<Int>): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "api/dependencies/force", hasBody = true)
    suspend fun deleteDependencies(@Body ids: List<Int>): ApiResponse<Unit>

    @GET("api/dependencies/{id}")
    suspend fun getDependenceLog(@Path("id") id: Int): ApiResponse<DependenceLogEntry>

    // ── Subscriptions ──
    @GET("api/subscriptions")
    suspend fun getSubscriptions(): ApiResponse<List<SubscriptionInfo>>

    @GET("api/subscriptions/{id}")
    suspend fun getSubscriptionDetail(@Path("id") id: Int): ApiResponse<SubscriptionInfo>

    @POST("api/subscriptions")
    suspend fun addSubscription(@Body body: Map<String, String>): ApiResponse<Unit>

    @PUT("api/subscriptions")
    suspend fun updateSubscription(@Body body: Map<String, String>): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "api/subscriptions", hasBody = true)
    suspend fun deleteSubscriptions(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/subscriptions/run")
    suspend fun runSubscriptions(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/subscriptions/stop")
    suspend fun stopSubscriptions(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/subscriptions/disable")
    suspend fun disableSubscriptions(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/subscriptions/enable")
    suspend fun enableSubscriptions(@Body ids: List<Int>): ApiResponse<Unit>

    @GET("api/subscriptions/{id}/log")
    suspend fun getSubscriptionLog(@Path("id") id: Int): ApiResponse<String>

    // ── Config ──
    @POST("api/configs/save")
    suspend fun saveConfig(@Body body: Map<String, String>): ApiResponse<Unit>

    @GET("api/configs/{name}")
    suspend fun getConfigContent(@Path("name") name: String): ApiResponse<String>

    // ── Logs ──
    @GET("api/logs/")
    suspend fun getLogFiles(): ApiResponse<List<LogFile>>

    @GET("api/logs/detail")
    suspend fun getLogDetail(
        @Query("file") file: String,
        @Query("path") path: String = ""
    ): ApiResponse<String>

    @HTTP(method = "DELETE", path = "api/logs", hasBody = true)
    suspend fun deleteLogs(@Body ids: List<String>): ApiResponse<Unit>

    @POST("api/logs/download")
    suspend fun downloadLog(@Body ids: List<String>): ApiResponse<Unit>
}
