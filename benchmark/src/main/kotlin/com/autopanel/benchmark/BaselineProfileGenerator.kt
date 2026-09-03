package com.autopanel.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        prepareFreshInstallForBenchmark()
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true
        ) {
            val journeys = AzureQlJourneys(this)
            journeys.startAndWait()
            journeys.clickTextIfPresent("任务", "Tasks")
            journeys.clickTextIfPresent("环境", "Environment")
            journeys.clickTextIfPresent("脚本", "Scripts")
            journeys.scrollForward(repetitions = 4)
        }
    }
}
