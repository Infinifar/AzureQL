package com.autopanel.core.mcp

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class McpEngineModule {
    @Binds
    @Singleton
    abstract fun bindMcpServerEngine(implementation: KotlinSdkMcpServerEngine): McpServerEngine

    @Binds
    @Singleton
    abstract fun bindMcpAgentStore(implementation: AndroidMcpAgentStore): McpAgentStore

    @Binds
    @Singleton
    abstract fun bindMcpAuditLogger(implementation: PersistentMcpAuditLogger): McpAuditLogger

    @Binds
    @Singleton
    abstract fun bindMcpAuditReader(implementation: PersistentMcpAuditLogger): McpAuditReader

    @Binds
    @Singleton
    abstract fun bindMcpOperationManager(
        implementation: PersistentMcpOperationManager
    ): McpOperationManager
}
