package com.autopanel.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class InteractionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun switchPrimaryTabs() = measureFrames {
        switchPrimaryTabs()
    }

    @Test
    fun scrollTaskListWith500Or1000Items() {
        val taskCount = BenchmarkArguments.requiredInt(
            "azureql.benchmark.taskCount",
            minimum = 500
        )
        measureFrames(
            setupJourney = {
                openBottomTab("任务", "Tasks")
                preloadTaskItems(taskCount)
            }
        ) {
            scrollForward(repetitions = 12)
        }
    }

    @Test
    fun expandAndScrollLargeScriptDirectory() {
        val directoryPath = BenchmarkArguments.required("azureql.benchmark.scriptDirectory")
        measureFrames(
            setupJourney = {
                openBottomTab("脚本", "Scripts")
                positionScriptDirectory(directoryPath)
            }
        ) {
            clickVisibleScriptDirectory(directoryPath)
            scrollForward(repetitions = 12)
        }
    }

    @Test
    fun openOneMiBLog() = measureLargeLog("azureql.benchmark.log1MiB")

    @Test
    fun openFiveMiBLog() = measureLargeLog("azureql.benchmark.log5MiB")

    @Test
    fun openTwentyMiBLog() = measureLargeLog("azureql.benchmark.log20MiB")

    @Test
    fun openAndPageTenMiBScript() = measureLargeScript(
        nameKey = "azureql.benchmark.script10MiB",
        parentKey = "azureql.benchmark.script10MiBParent"
    )

    @Test
    fun openAndPageFiftyMiBScript() = measureLargeScript(
        nameKey = "azureql.benchmark.script50MiB",
        parentKey = "azureql.benchmark.script50MiBParent"
    )

    @Test
    fun pollSubscriptionLogForSixtySeconds() {
        val subscription = BenchmarkArguments.required("azureql.benchmark.subscription")
        measureFrames(
            iterations = 1,
            setupJourney = {
                openBottomTab("脚本", "Scripts")
                openSettingsRow("订阅", "Subscriptions")
                positionNamedItem(subscription)
            }
        ) {
            clickVisibleNamedItem(subscription)
            waitForSubscriptionLogReady()
            waitForPolling(durationMs = 60_000)
        }
    }

    private fun measureLargeLog(argumentKey: String) {
        val logName = BenchmarkArguments.required(argumentKey)
        measureFrames(
            setupJourney = {
                openBottomTab("设置", "Settings")
                openSettingsRow("任务日志", "Task logs")
                positionNamedItem(logName)
            }
        ) {
            clickVisibleNamedItem(logName)
            waitForTaskLogReady()
            scrollForward(repetitions = 4)
        }
    }

    private fun measureLargeScript(nameKey: String, parentKey: String) {
        val scriptName = BenchmarkArguments.required(nameKey)
        val parentPath = BenchmarkArguments.optional(parentKey)
        measureFrames(
            setupJourney = {
                openBottomTab("脚本", "Scripts")
                positionScriptItem(scriptName, parentPath)
            }
        ) {
            clickVisibleNamedItem(scriptName)
            waitForPagedScriptReady()
            pageScriptForward(repetitions = 3)
        }
    }

    private fun measureFrames(
        iterations: Int = 5,
        setupJourney: AzureQlJourneys.() -> Unit = {},
        journey: AzureQlJourneys.() -> Unit
    ) {
        prepareFreshInstallForBenchmark()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Disable,
                warmupIterations = 3
            ),
            // App startup is intentionally outside FrameTimingMetric. Cold-start performance
            // belongs to StartupBenchmark; these scenarios measure only ready-state interaction.
            startupMode = null,
            iterations = iterations,
            setupBlock = {
                AzureQlJourneys(this).apply {
                    startAndWait()
                    returnToHome()
                    setupJourney()
                }
            }
        ) {
            AzureQlJourneys(this).journey()
        }
    }
}
