package com.autopanel.core.data.cache

import android.content.Context
import com.autopanel.core.data.script.ScriptDraftStore
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
internal class CacheCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val responseCache: ResponseCache,
    private val scriptDraftStore: ScriptDraftStore
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = try {
        responseCache.prune()
        scriptDraftStore.prune()
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.retry()
    }
}

@Singleton
class CacheMaintenanceScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun schedule() {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            STARTUP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CacheCleanupWorker>().build()
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        )
    }

    private companion object {
        const val STARTUP_WORK_NAME = "azureql-cache-cleanup-startup"
        const val PERIODIC_WORK_NAME = "azureql-cache-cleanup-daily"
    }
}
