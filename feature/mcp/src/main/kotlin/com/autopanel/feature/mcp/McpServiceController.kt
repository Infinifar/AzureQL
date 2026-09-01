package com.autopanel.feature.mcp

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface McpServiceController {
    fun start()
    fun stop()
}

@Singleton
class AndroidMcpServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : McpServiceController {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, McpForegroundService::class.java).setAction(McpForegroundService.ACTION_START)
        )
    }

    override fun stop() {
        context.stopService(Intent(context, McpForegroundService::class.java))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class McpServiceControllerModule {
    @Binds
    @Singleton
    abstract fun bindMcpServiceController(
        implementation: AndroidMcpServiceController
    ): McpServiceController
}
