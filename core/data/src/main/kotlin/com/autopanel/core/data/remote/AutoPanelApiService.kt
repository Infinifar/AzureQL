package com.autopanel.core.data.remote

import com.autopanel.core.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.Streaming

interface AutoPanelApiService {

    companion object {
        const val LONG_RUNNING_HEADER = "X-AutoPanel-Long-Running"
        const val NO_AUTH_HEADER = "X-AutoPanel-No-Auth"
    }

    // ── Health ──
    @GET("api/health")
    suspend fun healthCheck(): ApiResponse<Unit>

    // ── Auth ──
    @Headers("$NO_AUTH_HEADER: true")
    @POST("api/user/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginData>

    @Headers("$NO_AUTH_HEADER: true")
    @GET("open/auth/token")
    suspend fun loginWithClientCredentials(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String
    ): ApiResponse<LoginData>

    @POST("api/user/logout")
    suspend fun logout(): ApiResponse<Unit>

    @Headers("$NO_AUTH_HEADER: true")
    @PUT("api/user/two-factor/login")
    suspend fun loginTwoFactor(@Body request: TwoFactorRequest): ApiResponse<LoginData>

    // ── User ──
    @GET("api/user")
    suspend fun getUserInfo(): ApiResponse<UserInfo>

    @PUT("api/user")
    suspend fun updateAccount(@Body body: Map<String, String>): ApiResponse<Unit>

    @GET("api/user/two-factor/init")
    suspend fun initializeTwoFactor(): ApiResponse<TwoFactorSetup>

    @PUT("api/user/two-factor/active")
    suspend fun activateTwoFactor(@Body body: Map<String, String>): ApiResponse<Boolean>

    @PUT("api/user/two-factor/deactivate")
    suspend fun deactivateTwoFactor(): ApiResponse<Boolean>

    @GET("api/user/login-log")
    suspend fun getLoginLogs(): ApiResponse<List<LoginLogEntry>>

    // ── System ──
    @GET("api/system")
    suspend fun getSystemInfo(): ApiResponse<SystemInfo>

    @GET("api/system/config")
    suspend fun getSystemConfig(): ApiResponse<SystemConfigData>

    @PUT("api/update/reload")
    suspend fun reloadSystem(): ApiResponse<Unit>

    @PUT("api/system/config/log-remove-frequency")
    suspend fun updateLogRemoveFrequency(@Body body: Map<String, Int>): ApiResponse<Unit>

    @PUT("api/system/config/cron-concurrency")
    suspend fun updateCronConcurrency(@Body body: Map<String, Int>): ApiResponse<Unit>

    @PUT("api/system/config/dependence-proxy")
    suspend fun updateDependenceProxy(@Body body: Map<String, String>): ApiResponse<Unit>

    @Streaming
    @PUT("api/system/config/node-mirror")
    suspend fun updateNodeMirror(@Body body: Map<String, String>): Response<ResponseBody>

    @PUT("api/system/config/python-mirror")
    suspend fun updatePythonMirror(@Body body: Map<String, String>): ApiResponse<Unit>

    @Streaming
    @PUT("api/system/config/linux-mirror")
    suspend fun updateLinuxMirror(@Body body: Map<String, String>): Response<ResponseBody>

    @PUT("api/system/config/dependence-clean")
    suspend fun cleanDependence(@Body body: Map<String, String>): ApiResponse<Unit>

    @Streaming
    @Headers("$LONG_RUNNING_HEADER: true")
    @PUT("api/system/data/export")
    suspend fun exportData(@Body body: BackupExportRequest): Response<ResponseBody>

    @Multipart
    @Headers("$LONG_RUNNING_HEADER: true")
    @PUT("api/system/data/import")
    suspend fun importData(@Part data: MultipartBody.Part): ApiResponse<JsonElement>

    @PUT("api/update/data")
    suspend fun activateImportedData(): ApiResponse<Unit>

    // ── Dashboard ──
    @GET("api/dashboard/overview")
    suspend fun getDashboardOverview(): ApiResponse<DashboardOverview>

    @GET("api/dashboard/trend")
    suspend fun getDashboardTrend(@Query("days") days: Int = 7): ApiResponse<List<DashboardTrendItem>>

    @GET("api/dashboard/top-time")
    suspend fun getDashboardTopTime(): ApiResponse<List<DashboardTopTimeItem>>

    @GET("api/dashboard/top-count")
    suspend fun getDashboardTopCount(): ApiResponse<List<DashboardTopCountItem>>

    @GET("api/dashboard/system")
    suspend fun getDashboardSystem(): ApiResponse<DashboardSystem>

    @GET("api/dashboard/runtime")
    suspend fun getDashboardRuntime(): ApiResponse<DashboardRuntime>

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

    @Headers("$LONG_RUNNING_HEADER: true")
    @PUT("api/scripts")
    suspend fun updateScript(@Body body: ScriptUpdateRequest): ApiResponse<Unit>

    @Multipart
    @Headers("$LONG_RUNNING_HEADER: true")
    @POST("api/scripts")
    suspend fun uploadScriptFile(
        @Part file: MultipartBody.Part,
        @Part("filename") filename: RequestBody,
        @Part("path") path: RequestBody
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "api/scripts", hasBody = true)
    suspend fun deleteScript(@Body body: ScriptDeleteRequest): ApiResponse<Unit>

    @Headers("$LONG_RUNNING_HEADER: true")
    @GET("api/scripts/detail")
    suspend fun getScriptContent(
        @Query("file") filename: String,
        @Query("path") path: String = ""
    ): ApiResponse<String>

    @PUT("api/scripts/run")
    suspend fun runScript(@Body body: ScriptDeleteRequest): ApiResponse<Unit>

    @PUT("api/scripts/stop")
    suspend fun stopScript(@Body body: ScriptDeleteRequest): ApiResponse<Unit>

    @PUT("api/scripts/rename")
    suspend fun renameScript(@Body body: Map<String, String>): ApiResponse<Unit>

    @POST("api/scripts/download")
    @Streaming
    suspend fun downloadScript(@Body body: ScriptDeleteRequest): Response<ResponseBody>

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
    suspend fun reinstallDependencies(@Body ids: List<Int>): ApiResponse<List<DependencyInfo>>

    @PUT("api/dependencies/cancel")
    suspend fun cancelDependency(@Body ids: List<Int>): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "api/dependencies/force", hasBody = true)
    suspend fun deleteDependencies(@Body ids: List<Int>): ApiResponse<List<DependencyInfo>>

    @GET("api/dependencies/{id}")
    suspend fun getDependenceLog(@Path("id") id: Int): ApiResponse<DependenceLogEntry>

    // ── Subscriptions ──
    @GET("api/subscriptions")
    suspend fun getSubscriptions(): ApiResponse<List<SubscriptionInfo>>

    @GET("api/subscriptions/{id}")
    suspend fun getSubscriptionDetail(@Path("id") id: Int): ApiResponse<SubscriptionInfo>

    @POST("api/subscriptions")
    suspend fun addSubscription(@Body body: JsonObject): ApiResponse<JsonElement>

    @PUT("api/subscriptions")
    suspend fun updateSubscription(@Body body: JsonObject): ApiResponse<JsonElement>

    @HTTP(method = "DELETE", path = "api/subscriptions", hasBody = true)
    suspend fun deleteSubscriptions(
        @Body ids: List<Int>,
        @Query("force") force: Boolean = false
    ): ApiResponse<JsonElement>

    @PUT("api/subscriptions/run")
    suspend fun runSubscriptions(@Body ids: List<Int>): ApiResponse<JsonElement>

    @PUT("api/subscriptions/stop")
    suspend fun stopSubscriptions(@Body ids: List<Int>): ApiResponse<JsonElement>

    @PUT("api/subscriptions/disable")
    suspend fun disableSubscriptions(@Body ids: List<Int>): ApiResponse<JsonElement>

    @PUT("api/subscriptions/enable")
    suspend fun enableSubscriptions(@Body ids: List<Int>): ApiResponse<JsonElement>

    @GET("api/subscriptions/{id}/log")
    suspend fun getSubscriptionLog(
        @Path("id") id: Int,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int = 65_536,
        @Query("tail") tail: Boolean = false
    ): SubscriptionLogResponse

    // ── Config ──
    @POST("api/configs/save")
    suspend fun saveConfig(@Body body: Map<String, String>): ApiResponse<Unit>

    @GET("api/configs/{name}")
    suspend fun getConfigContent(@Path("name") name: String): ApiResponse<String>

    // ── Apps (应用设置) ──
    @GET("api/apps")
    suspend fun getApps(): ApiResponse<List<AppInfo>>

    @POST("api/apps")
    suspend fun createApp(@Body body: AppCreateRequest): ApiResponse<AppInfo>

    @PUT("api/apps")
    suspend fun updateApp(@Body body: AppUpdateRequest): ApiResponse<AppInfo>

    @HTTP(method = "DELETE", path = "api/apps", hasBody = true)
    suspend fun deleteApps(@Body ids: List<Int>): ApiResponse<Unit>

    @PUT("api/apps/{id}/reset-secret")
    suspend fun resetAppSecret(@Path("id") id: Int): ApiResponse<AppInfo>

    // ── Logs ──
    @GET("api/logs/")
    suspend fun getLogFiles(): ApiResponse<List<LogFile>>

    @GET("api/logs/detail")
    suspend fun getLogDetail(
        @Query("file") file: String,
        @Query("path") path: String = ""
    ): ApiResponse<String>

    @HTTP(method = "DELETE", path = "api/logs", hasBody = true)
    suspend fun deleteLog(@Body request: LogDeleteRequest): ApiResponse<Unit>

    @POST("api/logs/download")
    suspend fun downloadLog(@Body ids: List<String>): ApiResponse<Unit>
}
