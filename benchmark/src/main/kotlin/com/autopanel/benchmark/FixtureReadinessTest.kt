package com.autopanel.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only smoke test for every server fixture required by InteractionBenchmark. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class FixtureReadinessTest {
    @Test
    fun verifyAllConfiguredFixtures() {
        val taskCount = BenchmarkArguments.requiredInt(
            "azureql.benchmark.taskCount",
            minimum = 500
        )
        val directoryPath = BenchmarkArguments.required("azureql.benchmark.scriptDirectory")
        val logNames = listOf(
            BenchmarkArguments.required("azureql.benchmark.log1MiB"),
            BenchmarkArguments.required("azureql.benchmark.log5MiB"),
            BenchmarkArguments.required("azureql.benchmark.log20MiB")
        )
        val scriptFixtures = listOf(
            BenchmarkArguments.required("azureql.benchmark.script10MiB") to
                BenchmarkArguments.optional("azureql.benchmark.script10MiBParent"),
            BenchmarkArguments.required("azureql.benchmark.script50MiB") to
                BenchmarkArguments.optional("azureql.benchmark.script50MiBParent")
        )
        val subscription = BenchmarkArguments.required("azureql.benchmark.subscription")

        AzureQlJourneys.forFixtureReadiness().apply {
            startAndWait()
            returnToHome()

            openBottomTab("任务", "Tasks")
            preloadTaskItems(taskCount)

            returnToHome()
            openBottomTab("脚本", "Scripts")
            positionScriptDirectory(directoryPath)

            scriptFixtures.forEach { (name, parentPath) ->
                returnToHome()
                openBottomTab("脚本", "Scripts")
                positionScriptItem(name, parentPath)
            }

            returnToHome()
            openBottomTab("设置", "Settings")
            openSettingsRow("任务日志", "Task logs")
            logNames.forEach(::positionNamedItem)

            returnToHome()
            openBottomTab("脚本", "Scripts")
            openSettingsRow("订阅", "Subscriptions")
            positionNamedItem(subscription)
        }
    }
}
