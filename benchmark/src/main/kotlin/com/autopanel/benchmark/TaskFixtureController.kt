package com.autopanel.benchmark

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TaskFixtureController {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun seedTasksToFiveHundred() {
        launchFixture(action = ACTION_SEED, targetTotal = 500, expectedStatus = STATUS_READY)
    }

    @Test
    fun cleanupTasks() {
        launchFixture(action = ACTION_CLEANUP, expectedStatus = STATUS_CLEAN)
    }

    @Test
    fun discoverFixtures() {
        launchFixture(action = ACTION_DISCOVER, expectedStatus = STATUS_DISCOVERY)
    }

    @Test
    fun seedContentFixtures() {
        launchFixture(action = ACTION_SEED_CONTENT, expectedStatus = STATUS_CONTENT_READY)
    }

    @Test
    fun startSubscriptionFixture() {
        launchFixture(
            action = ACTION_START_SUBSCRIPTION,
            expectedStatus = STATUS_SUBSCRIPTION_RUNNING
        )
    }

    @Test
    fun cleanupContentFixtures() {
        launchFixture(action = ACTION_CLEANUP_CONTENT, expectedStatus = STATUS_CONTENT_CLEAN)
    }

    private fun launchFixture(
        action: String,
        targetTotal: Int? = null,
        expectedStatus: String
    ) {
        val statuses = LinkedBlockingQueue<String>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra(EXTRA_STATUS)?.let(statuses::offer)
            }
        }
        registerStatusReceiver(receiver)

        val intent = Intent().apply {
            component = ComponentName(TARGET_PACKAGE, FIXTURE_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_ACTION, action)
            targetTotal?.let { putExtra(EXTRA_TARGET_TOTAL, it) }
        }
        try {
            instrumentation.context.startActivity(intent)
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FIXTURE_TIMEOUT_MS)
            while (true) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                check(remainingNanos > 0) { "Benchmark fixture timed out" }
                val status = statuses.poll(remainingNanos, TimeUnit.NANOSECONDS)
                    ?: error("Benchmark fixture did not report status")
                println(status)
                if (status.startsWith(STATUS_DISCOVERY)) {
                    File(
                        instrumentation.context.getExternalFilesDir(null),
                        DISCOVERY_FILE_NAME
                    ).writeText(status)
                }
                when {
                    status.startsWith(expectedStatus) -> return
                    status.startsWith(STATUS_ERROR) -> error("Benchmark fixture failed: $status")
                }
            }
        } finally {
            instrumentation.context.unregisterReceiver(receiver)
        }
    }

    private fun registerStatusReceiver(receiver: BroadcastReceiver) {
        val context = instrumentation.context
        val filter = IntentFilter(ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
    }
}

private const val FIXTURE_ACTIVITY =
    "com.autopanel.app.benchmark.BenchmarkFixtureActivity"
private const val EXTRA_ACTION = "azureql.benchmark.fixture.action"
private const val EXTRA_TARGET_TOTAL = "azureql.benchmark.fixture.target_total"
private const val EXTRA_STATUS = "azureql.benchmark.fixture.status"
private const val ACTION_STATUS = "com.autopanel.app.benchmark.FIXTURE_STATUS"
private const val ACTION_SEED = "seed"
private const val ACTION_CLEANUP = "cleanup"
private const val ACTION_DISCOVER = "discover"
private const val ACTION_SEED_CONTENT = "seed_content"
private const val ACTION_START_SUBSCRIPTION = "start_subscription"
private const val ACTION_CLEANUP_CONTENT = "cleanup_content"
private const val STATUS_READY = "AZUREQL_BENCH_READY"
private const val STATUS_CLEAN = "AZUREQL_BENCH_CLEAN"
private const val STATUS_DISCOVERY = "AZUREQL_BENCH_DISCOVERY"
private const val STATUS_CONTENT_READY = "AZUREQL_BENCH_CONTENT_READY"
private const val STATUS_SUBSCRIPTION_RUNNING = "AZUREQL_BENCH_SUBSCRIPTION_RUNNING"
private const val STATUS_CONTENT_CLEAN = "AZUREQL_BENCH_CONTENT_CLEAN"
private const val STATUS_ERROR = "AZUREQL_BENCH_ERROR"
private const val FIXTURE_TIMEOUT_MS = 10 * 60 * 1_000L
private const val DISCOVERY_FILE_NAME = "fixture-discovery.txt"
