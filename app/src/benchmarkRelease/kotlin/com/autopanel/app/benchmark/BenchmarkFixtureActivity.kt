package com.autopanel.app.benchmark

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.domain.LogRepository
import com.autopanel.core.domain.ScriptRepository
import com.autopanel.core.domain.SubscriptionRepository
import com.autopanel.core.domain.TaskRepository
import com.autopanel.core.model.LogFile
import com.autopanel.core.model.ScriptFile
import com.autopanel.core.model.TaskDraft
import com.autopanel.core.model.TaskInfo
import com.autopanel.core.model.TaskScheduleType
import com.autopanel.core.model.TaskStatus
import com.autopanel.core.model.flattenLogFiles
import com.autopanel.core.model.toDraft
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Signature-protected fixture controller compiled only into benchmarkRelease.
 * Production release APKs never contain this activity or its permission.
 */
@AndroidEntryPoint
class BenchmarkFixtureActivity : ComponentActivity() {
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var scriptRepository: ScriptRepository
    @Inject lateinit var logRepository: LogRepository
    @Inject lateinit var subscriptionRepository: SubscriptionRepository
    @Inject lateinit var retrofitClient: AutoPanelRetrofitClient

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            text = STATUS_RUNNING
        }
        setContentView(statusView)
        updateStatus(STATUS_RUNNING)

        lifecycleScope.launch {
            runCatching {
                retrofitClient.prepareCurrent() ?: error("No saved host")
                when (intent.getStringExtra(EXTRA_ACTION)) {
                    ACTION_SEED -> seedToTarget(intent.getIntExtra(EXTRA_TARGET_TOTAL, 500))
                    ACTION_CLEANUP -> cleanup()
                    ACTION_DISCOVER -> discoverFixtures()
                    ACTION_SEED_CONTENT -> seedBenchmarkContent()
                    ACTION_START_SUBSCRIPTION -> startBenchmarkSubscription()
                    ACTION_CLEANUP_CONTENT -> cleanupBenchmarkContent()
                    else -> error("Unknown benchmark fixture action")
                }
            }.onFailure { error ->
                updateStatus("$STATUS_ERROR ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
            }
        }
    }

    private suspend fun seedToTarget(targetTotal: Int) {
        require(targetTotal >= 500) { "Target task count must be at least 500" }
        val currentTotal = taskRepository.getTasks(page = 1, size = 1).getOrThrow().second
        val needed = (targetTotal - currentTotal).coerceAtLeast(0)
        updateStatus("$STATUS_RUNNING current=$currentTotal target=$targetTotal needed=$needed")
        var ids = emptyList<Int>()
        try {
            if (needed > 0) createFixtures(needed)
        } finally {
            // A failed or cancelled seed can leave a partial batch on the server. Discover,
            // disable and record every fixture before propagating the failure so cleanup is safe.
            ids = withContext(NonCancellable) { disableAndRecordFixtures() }
        }
        require(ids.size >= needed) {
            "Created fixture IDs are incomplete: expected at least $needed, found ${ids.size}"
        }

        val finalTotal = taskRepository.getTasks(page = 1, size = 1).getOrThrow().second
        check(finalTotal >= targetTotal) {
            "Task total $finalTotal did not reach target $targetTotal"
        }
        updateStatus("$STATUS_READY total=$finalTotal fixtures=${ids.size}")
    }

    private suspend fun disableAndRecordFixtures(): List<Int> {
        val ids = findFixtureTasks().mapNotNull { it.id }.distinct()
        ids.chunked(API_BATCH_SIZE).forEach { batch ->
            taskRepository.disableTasks(batch).getOrThrow()
        }
        writeFixtureIds(ids)
        return ids
    }

    private suspend fun createFixtures(count: Int) {
        val runId = System.currentTimeMillis()
        (0 until count).chunked(CREATE_CONCURRENCY).forEachIndexed { batchIndex, indexes ->
            coroutineScope {
                indexes.map { index ->
                    async(Dispatchers.IO) {
                        taskRepository.addTask(
                            TaskDraft(
                                name = "$FIXTURE_PREFIX${runId}_${index.toString().padStart(4, '0')}",
                                command = "echo AzureQL benchmark fixture",
                                scheduleType = TaskScheduleType.NORMAL,
                                schedule = "0 0 1 1 *"
                            )
                        ).getOrThrow()
                    }
                }.awaitAll()
            }
            updateStatus(
                "$STATUS_RUNNING created=" +
                    "${((batchIndex + 1) * CREATE_CONCURRENCY).coerceAtMost(count)}/$count"
            )
        }
    }

    private suspend fun cleanup() {
        val storedIds = readFixtureIds()
        val discoveredIds = findFixtureTasks().mapNotNull { it.id }
        val ids = (storedIds + discoveredIds).distinct()
        ids.chunked(API_BATCH_SIZE).forEach { batch ->
            taskRepository.deleteTasks(batch).getOrThrow()
        }

        val remaining = findFixtureTasks()
        check(remaining.isEmpty()) { "${remaining.size} benchmark fixtures remain after cleanup" }
        withContext(Dispatchers.IO) { fixtureIdFile().delete() }
        val finalTotal = taskRepository.getTasks(page = 1, size = 1).getOrThrow().second
        updateStatus("$STATUS_CLEAN total=$finalTotal deleted=${ids.size}")
    }

    /** Reads fixture metadata only; no script/log body or credential enters the result. */
    private suspend fun discoverFixtures() {
        val taskTotal = taskRepository.getTasks(page = 1, size = 1).getOrThrow().second
        val scriptTree = scriptRepository.getScripts().getOrThrow()
        val scripts = flattenScripts(scriptTree)
        val logs = flattenLogFiles(logRepository.getLogFiles().getOrThrow())
        val subscriptions = subscriptionRepository.getSubscriptions().getOrThrow()
        val directories = collectDirectories(scriptTree)

        val discovery = buildString {
            appendLine(STATUS_DISCOVERY)
            appendLine("tasks.total=$taskTotal")
            appendLine("script.directory=${directories.maxByOrNull { it.descendantFiles }?.path.orEmpty()}")
            appendLine("script.10MiB=${scripts.closestTo(10L * MIB)?.describeScript().orEmpty()}")
            appendLine("script.50MiB=${scripts.closestTo(50L * MIB)?.describeScript().orEmpty()}")
            appendLine("log.1MiB=${logs.closestTo(1L * MIB)?.describeLog().orEmpty()}")
            appendLine("log.5MiB=${logs.closestTo(5L * MIB)?.describeLog().orEmpty()}")
            appendLine("log.20MiB=${logs.closestTo(20L * MIB)?.describeLog().orEmpty()}")
            subscriptions
                .sortedWith(compareByDescending<com.autopanel.core.model.SubscriptionInfo> {
                    it.status == 0 || it.status == 3
                }.thenByDescending { it.id ?: 0 })
                .take(MAX_DISCOVERY_SUBSCRIPTIONS)
                .forEachIndexed { index, subscription ->
                    appendLine(
                        "subscription.$index=" +
                            "name=${subscription.name ?: subscription.alias.orEmpty()}|" +
                            "status=${subscription.status}|disabled=${subscription.disabled}"
                    )
                }
        }.trimEnd()
        updateStatus(discovery)
    }

    private suspend fun seedBenchmarkContent() {
        cleanupBenchmarkContentResources()
        deleteFixtureTasks()

        val setupTask = createFixtureTask(
            name = CONTENT_SETUP_TASK_NAME,
            command = CONTENT_SETUP_COMMAND,
            logName = "AZUREQL_BENCH_CONTENT_SETUP"
        )
        runAndAwaitTask(setupTask, CONTENT_READY_MARKER)

        LOG_FIXTURES.forEach { fixture ->
            updateStatus("$STATUS_RUNNING preparing=${fixture.name}")
            val task = createFixtureTask(
                name = fixture.taskName,
                command = fixture.command,
                logName = fixture.logName
            )
            runAndAwaitTask(task, fixture.marker)
        }

        val sourceSubscription = subscriptionRepository.getSubscriptions().getOrThrow()
            .firstOrNull {
                !it.disabled &&
                    !it.url.isNullOrBlank() &&
                    (it.type == "public-repo" || it.type == "single-file")
            }
            ?: error("No enabled public/single-file subscription is available to clone")
        subscriptionRepository.addSubscription(
            sourceSubscription.toDraft().copy(
                id = null,
                name = BENCHMARK_SUBSCRIPTION_NAME,
                alias = BENCHMARK_SUBSCRIPTION_NAME,
                subBefore = "sleep $SUBSCRIPTION_HOLD_SECONDS",
                autoAddCron = false,
                autoDelCron = false
            )
        ).getOrThrow()
        check(findBenchmarkSubscription() != null) {
            "Benchmark subscription was not visible after creation"
        }

        val scripts = flattenScripts(scriptRepository.getScripts().getOrThrow())
        check(scripts.any { it.title == SCRIPT_10_MIB_NAME && it.size == 10L * MIB }) {
            "$SCRIPT_10_MIB_NAME was not created at exactly 10 MiB"
        }
        check(scripts.any { it.title == SCRIPT_50_MIB_NAME && it.size == 50L * MIB }) {
            "$SCRIPT_50_MIB_NAME was not created at exactly 50 MiB"
        }
        updateStatus("$STATUS_CONTENT_READY tasks=${LOG_FIXTURES.size + 1}")
    }

    private suspend fun startBenchmarkSubscription() {
        val subscription = findBenchmarkSubscription()
            ?: error("Benchmark subscription is missing; seed content first")
        val id = subscription.id ?: error("Benchmark subscription ID is missing")
        if (subscription.status == 0 || subscription.status == 3) {
            subscriptionRepository.stopSubscription(id).getOrThrow()
            delay(500)
        }
        subscriptionRepository.runSubscription(id).getOrThrow()
        withTimeout(SUBSCRIPTION_START_TIMEOUT_MS) {
            while (true) {
                val current = findBenchmarkSubscription()
                    ?: error("Benchmark subscription disappeared after start")
                if (current.status == 0 || current.status == 3) return@withTimeout
                delay(250)
            }
        }
        updateStatus("$STATUS_SUBSCRIPTION_RUNNING name=$BENCHMARK_SUBSCRIPTION_NAME")
    }

    private suspend fun cleanupBenchmarkContent() {
        cleanupBenchmarkContentResources()
        updateStatus(STATUS_CONTENT_CLEAN)
    }

    private suspend fun cleanupBenchmarkContentResources() {
        subscriptionRepository.getSubscriptions().getOrThrow()
            .filter { it.name == BENCHMARK_SUBSCRIPTION_NAME || it.alias == BENCHMARK_SUBSCRIPTION_NAME }
            .forEach { subscription ->
                val id = subscription.id ?: return@forEach
                if (subscription.status == 0 || subscription.status == 3) {
                    runCatching { subscriptionRepository.stopSubscription(id).getOrThrow() }
                    delay(250)
                }
                subscriptionRepository.deleteSubscription(id).getOrThrow()
            }

        val scriptTree = scriptRepository.getScripts().getOrThrow()
        scriptTree.filter { it.isDirectory && it.title in BENCHMARK_SCRIPT_DIRECTORIES }
            .forEach { directory ->
                scriptRepository.deleteScript(
                    filename = directory.title.orEmpty(),
                    path = directory.parent.orEmpty(),
                    isDir = true
                ).getOrThrow()
            }

        flattenLogFiles(logRepository.getLogFiles().getOrThrow())
            .filter { log ->
                log.parent.orEmpty().contains(BENCHMARK_PREFIX) ||
                    log.title.orEmpty().contains(BENCHMARK_PREFIX)
            }
            .forEach { logRepository.deleteLog(it).getOrThrow() }
    }

    private suspend fun createFixtureTask(name: String, command: String, logName: String): TaskInfo {
        taskRepository.addTask(
            TaskDraft(
                name = name,
                command = command,
                scheduleType = TaskScheduleType.NORMAL,
                schedule = "0 0 1 1 *",
                logName = logName
            )
        ).getOrThrow()
        return withTimeout(FIXTURE_LOOKUP_TIMEOUT_MS) {
            while (true) {
                findFixtureTask(name)?.let { return@withTimeout it }
                delay(250)
            }
            @Suppress("UNREACHABLE_CODE")
            error("Fixture task lookup timed out")
        }
    }

    private suspend fun runAndAwaitTask(task: TaskInfo, completionMarker: String) {
        val id = task.id ?: error("Fixture task '${task.name}' has no ID")
        taskRepository.enableTasks(listOf(id)).getOrThrow()
        taskRepository.runTasks(listOf(id)).getOrThrow()
        delay(500)
        withTimeout(CONTENT_TASK_TIMEOUT_MS) {
            while (true) {
                val current = findFixtureTask(task.name.orEmpty())
                    ?: error("Fixture task '${task.name}' disappeared while running")
                if (current.statusCode == TaskStatus.IDLE) {
                    val log = taskRepository.getTaskLog(id).getOrThrow()
                    if (completionMarker in log) return@withTimeout
                }
                delay(750)
            }
        }
    }

    private suspend fun findFixtureTask(name: String): TaskInfo? = taskRepository.getTasks(
        search = name,
        page = 1,
        size = 100
    ).getOrThrow().first.firstOrNull { it.name == name }

    private suspend fun deleteFixtureTasks() {
        val tasks = findFixtureTasks()
        val runningIds = tasks.filter {
            it.statusCode == TaskStatus.RUNNING || it.statusCode == TaskStatus.QUEUED
        }.mapNotNull(TaskInfo::id)
        if (runningIds.isNotEmpty()) {
            taskRepository.stopTasks(runningIds).getOrThrow()
            delay(500)
        }
        val ids = tasks.mapNotNull(TaskInfo::id)
        ids.chunked(API_BATCH_SIZE).forEach { taskRepository.deleteTasks(it).getOrThrow() }
    }

    private suspend fun findBenchmarkSubscription() =
        subscriptionRepository.getSubscriptions().getOrThrow().firstOrNull {
            it.name == BENCHMARK_SUBSCRIPTION_NAME || it.alias == BENCHMARK_SUBSCRIPTION_NAME
        }

    private suspend fun findFixtureTasks() = taskRepository.getTasks(
        search = FIXTURE_PREFIX,
        page = 1,
        size = MAX_FIXTURE_QUERY_SIZE
    ).getOrThrow().first.filter { it.name?.startsWith(FIXTURE_PREFIX) == true }

    private suspend fun writeFixtureIds(ids: List<Int>) = withContext(Dispatchers.IO) {
        fixtureIdFile().writeText(ids.joinToString(separator = "\n"))
    }

    private suspend fun readFixtureIds(): List<Int> = withContext(Dispatchers.IO) {
        fixtureIdFile().takeIf(File::exists)
            ?.readLines()
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
    }

    private fun fixtureIdFile() = File(filesDir, FIXTURE_ID_FILE)

    private fun updateStatus(status: String) {
        statusView.text = status
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(BENCHMARK_PACKAGE)
                .putExtra(EXTRA_STATUS, status)
        )
    }

    companion object {
        const val ACTION_STATUS = "com.autopanel.app.benchmark.FIXTURE_STATUS"
        const val EXTRA_ACTION = "azureql.benchmark.fixture.action"
        const val EXTRA_TARGET_TOTAL = "azureql.benchmark.fixture.target_total"
        const val EXTRA_STATUS = "azureql.benchmark.fixture.status"
        const val ACTION_SEED = "seed"
        const val ACTION_CLEANUP = "cleanup"
        const val ACTION_DISCOVER = "discover"
        const val ACTION_SEED_CONTENT = "seed_content"
        const val ACTION_START_SUBSCRIPTION = "start_subscription"
        const val ACTION_CLEANUP_CONTENT = "cleanup_content"
        const val STATUS_RUNNING = "AZUREQL_BENCH_RUNNING"
        const val STATUS_READY = "AZUREQL_BENCH_READY"
        const val STATUS_CLEAN = "AZUREQL_BENCH_CLEAN"
        const val STATUS_DISCOVERY = "AZUREQL_BENCH_DISCOVERY"
        const val STATUS_CONTENT_READY = "AZUREQL_BENCH_CONTENT_READY"
        const val STATUS_SUBSCRIPTION_RUNNING = "AZUREQL_BENCH_SUBSCRIPTION_RUNNING"
        const val STATUS_CONTENT_CLEAN = "AZUREQL_BENCH_CONTENT_CLEAN"
        const val STATUS_ERROR = "AZUREQL_BENCH_ERROR"

        private const val BENCHMARK_PREFIX = "AZUREQL_BENCH_"
        private const val FIXTURE_PREFIX = "AZUREQL_BENCH_20260902_"
        private const val CONTENT_SETUP_TASK_NAME = "AZUREQL_BENCH_CONTENT_SETUP"
        private const val BENCHMARK_SUBSCRIPTION_NAME = "AZUREQL_BENCH_SUBSCRIPTION"
        private const val SCRIPT_10_MIB_NAME = "azureql-bench-10m.py"
        private const val SCRIPT_50_MIB_NAME = "azureql-bench-50m.py"
        private const val CONTENT_READY_MARKER = "AZUREQL_BENCH_CONTENT_READY"
        private const val SUBSCRIPTION_HOLD_SECONDS = 600
        private const val FIXTURE_ID_FILE = "benchmark-task-ids.txt"
        private const val CREATE_CONCURRENCY = 4
        private const val API_BATCH_SIZE = 100
        private const val MAX_FIXTURE_QUERY_SIZE = 1_000
        private const val MAX_DISCOVERY_SUBSCRIPTIONS = 20
        private const val MIB = 1024 * 1024
        private const val FIXTURE_LOOKUP_TIMEOUT_MS = 30_000L
        private const val CONTENT_TASK_TIMEOUT_MS = 5 * 60_000L
        private const val SUBSCRIPTION_START_TIMEOUT_MS = 30_000L
        private const val BENCHMARK_PACKAGE = "com.autopanel.benchmark"

        private val BENCHMARK_SCRIPT_DIRECTORIES = setOf(
            "AZUREQL_BENCH_FILES",
            "AZUREQL_BENCH_TREE"
        )
        private val CONTENT_SETUP_COMMAND = """
            python3 -c "from pathlib import Path; root=Path('/ql/data/scripts'); files=root/'AZUREQL_BENCH_FILES'; tree=root/'AZUREQL_BENCH_TREE'; files.mkdir(parents=True, exist_ok=True); tree.mkdir(parents=True, exist_ok=True); (files/'$SCRIPT_10_MIB_NAME').write_bytes(b'#'*(10*1024*1024)); (files/'$SCRIPT_50_MIB_NAME').write_bytes(b'#'*(50*1024*1024)); [(tree/f'fixture_{i:04d}.py').write_text(f'# AzureQL fixture {i}\\n', encoding='utf-8') for i in range(500)]; print('$CONTENT_READY_MARKER')"
        """.trimIndent()
        private val LOG_FIXTURES = listOf(
            LogFixture("1MiB", "AZUREQL_BENCH_LOG_1M", 1, "AZUREQL_BENCH_LOG_READY_1M"),
            LogFixture("5MiB", "AZUREQL_BENCH_LOG_5M", 5, "AZUREQL_BENCH_LOG_READY_5M"),
            LogFixture("20MiB", "AZUREQL_BENCH_LOG_20M", 20, "AZUREQL_BENCH_LOG_READY_20M")
        )
    }
}

private data class LogFixture(
    val name: String,
    val logName: String,
    val sizeMiB: Int,
    val marker: String
) {
    val taskName: String get() = "AZUREQL_BENCH_LOG_$name"
    val command: String
        get() = "python3 -c \"import sys; sys.stdout.write('L'*($sizeMiB*1024*1024)); " +
            "sys.stdout.write('\\n$marker\\n')\""
}

private data class ScriptCandidate(
    val title: String,
    val parent: String,
    val size: Long?
)

private data class ScriptDirectoryCandidate(
    val path: String,
    val descendantFiles: Int
)

private fun flattenScripts(nodes: List<ScriptFile>): List<ScriptCandidate> = buildList {
    fun walk(items: List<ScriptFile>) {
        items.forEach { item ->
            if (item.isDirectory) {
                walk(item.children.orEmpty())
            } else if (!item.title.isNullOrBlank()) {
                add(
                    ScriptCandidate(
                        title = item.title.orEmpty(),
                        parent = item.parent.orEmpty(),
                        size = item.size
                    )
                )
            }
        }
    }
    walk(nodes)
}

private fun collectDirectories(nodes: List<ScriptFile>): List<ScriptDirectoryCandidate> = buildList {
    fun fileCount(item: ScriptFile): Int = if (item.isDirectory) {
        item.children.orEmpty().sumOf(::fileCount)
    } else {
        1
    }

    fun walk(items: List<ScriptFile>) {
        items.forEach { item ->
            if (item.isDirectory) {
                add(
                    ScriptDirectoryCandidate(
                        path = item.key ?: listOf(item.parent, item.title)
                            .filterNot { it.isNullOrBlank() }
                            .map { it.orEmpty() }
                            .joinToString("/"),
                        descendantFiles = fileCount(item)
                    )
                )
                walk(item.children.orEmpty())
            }
        }
    }
    walk(nodes)
}

private fun <T> List<T>.closestTo(targetBytes: Long, size: (T) -> Long?): T? =
    filter { size(it) != null }.minByOrNull { kotlin.math.abs(size(it)!! - targetBytes) }

private fun List<ScriptCandidate>.closestTo(targetBytes: Long): ScriptCandidate? =
    closestTo(targetBytes, ScriptCandidate::size)

private fun List<LogFile>.closestTo(targetBytes: Long): LogFile? =
    closestTo(targetBytes, LogFile::size)

private fun ScriptCandidate.describeScript(): String =
    "name=$title|parent=$parent|size=${size ?: -1}"

private fun LogFile.describeLog(): String =
    "name=${title.orEmpty()}|parent=${parent.orEmpty()}|size=${size ?: -1}"
