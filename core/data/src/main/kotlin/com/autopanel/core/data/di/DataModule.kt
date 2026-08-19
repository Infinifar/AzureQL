package com.autopanel.core.data.di

import com.autopanel.core.data.repository.*
import com.autopanel.core.domain.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindEnvRepository(impl: EnvRepositoryImpl): EnvRepository

    @Binds
    @Singleton
    abstract fun bindScriptRepository(impl: ScriptRepositoryImpl): ScriptRepository

    @Binds
    @Singleton
    abstract fun bindDependencyRepository(impl: DependencyRepositoryImpl): DependencyRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: LogRepositoryImpl): LogRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository
}
