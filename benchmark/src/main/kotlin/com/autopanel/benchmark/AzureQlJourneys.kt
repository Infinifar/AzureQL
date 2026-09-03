package com.autopanel.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val TARGET_PACKAGE = "com.autopanel.app"
private const val UI_TIMEOUT_MS = 15_000L

/**
 * Some OEM builds ignore ProfileInstaller's explicit benchmark broadcast while a freshly
 * installed package is still marked as never launched. Launching it once clears that package
 * state; the following force-stop keeps the first measured startup cold and outside the metric.
 */
internal fun prepareFreshInstallForBenchmark() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val launchResult = device.executeShellCommand(
        "am start -W -n $TARGET_PACKAGE/.MainActivity"
    )
    check("Status: ok" in launchResult) {
        "Could not prepare the freshly installed AzureQL target: $launchResult"
    }
    check(device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_TIMEOUT_MS)) {
        "AzureQL did not become visible while preparing the benchmark target"
    }
    device.waitForIdle()
    val receiverResult = device.executeShellCommand(
        "am broadcast -a androidx.profileinstaller.action.BENCHMARK_OPERATION " +
            "-e EXTRA_BENCHMARK_OPERATION DROP_SHADER_CACHE " +
            "$TARGET_PACKAGE/androidx.profileinstaller.ProfileInstallReceiver"
    )
    check("result=14" in receiverResult) {
        "AzureQL ProfileInstaller receiver is not ready after the preparation launch: $receiverResult"
    }
    device.executeShellCommand("am force-stop $TARGET_PACKAGE")
}

internal class AzureQlJourneys private constructor(
    private val device: UiDevice,
    private val startTarget: () -> Unit
) {
    constructor(scope: MacrobenchmarkScope) : this(
        device = scope.device,
        startTarget = scope::startActivityAndWait
    )

    fun startAndWait() {
        startTarget()
        check(device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), UI_TIMEOUT_MS)) {
            "AzureQL did not become visible within $UI_TIMEOUT_MS ms"
        }
        device.waitForIdle()
    }

    /** Dismisses a dialog/nested route left by the previous iteration, then selects Home. */
    fun returnToHome() {
        repeat(MAX_BACK_NAVIGATION_ATTEMPTS) {
            if (findText("首页", "Home") != null && clickTextIfPresent("首页", "Home")) {
                if (waitForBottomDestination("首页")) return
            }
            device.pressBack()
            device.waitForIdle()
        }
        error("Could not return to AzureQL Home; ensure a saved benchmark account is logged in")
    }

    fun switchPrimaryTabs() {
        openBottomTab("任务", "Tasks")
        openBottomTab("环境", "Environment")
        openBottomTab("设置", "Settings")
        openSettingsRow("依赖管理", "Dependencies")
        device.pressBack()
        openBottomTab("脚本", "Scripts")
    }

    fun openBottomTab(chinese: String, english: String) {
        repeat(MAX_NAVIGATION_RETRIES) {
            if (isBottomDestinationVisible(chinese)) return
            check(clickTextIfPresent(chinese, english)) {
                "Could not find bottom tab '$chinese' or '$english'"
            }
            if (waitForBottomDestination(chinese)) return
            // Bottom navigation restores each tab's nested back stack. A benchmark setup needs
            // the tab root, so pop a restored child route (for example Task logs -> Settings).
            if (!isAnyBottomDestinationVisible()) {
                device.pressBack()
                device.waitForIdle()
                if (waitForBottomDestination(chinese)) return
            }
        }
        error("Bottom tab '$chinese' or '$english' did not navigate to its destination")
    }

    fun openSettingsRow(chinese: String, english: String) {
        clickRequiredText(chinese, english)
    }

    fun clickVisibleNamedItem(name: String) {
        repeat(MAX_STALE_CLICK_RETRIES) {
            val item = device.findObject(By.text(name))
                ?: error("Benchmark fixture '$name' is no longer visible")
            try {
                item.click()
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                SystemClock.sleep(STALE_RETRY_SETTLE_MS)
            }
        }
        error("Benchmark fixture '$name' remained stale after $MAX_STALE_CLICK_RETRIES retries")
    }

    fun openNamedItem(name: String) {
        positionNamedItem(name)
        clickVisibleNamedItem(name)
    }

    /** Positions an item before the measured block so list search does not pollute its metric. */
    fun positionNamedItem(name: String) {
        device.findObject(By.text(name))?.let { return }
        device.wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)
        val foundFromBeginning = runCatching {
            UiScrollable(UiSelector().scrollable(true)).apply {
                setAsVerticalList()
                setMaxSearchSwipes(MAX_ITEM_SEARCH_SWIPES)
                // Compose keeps each tab's LazyColumn position while switching tabs. Explicitly
                // rewind before searching so a fixture above the retained viewport is reachable.
                scrollToBeginning(MAX_ITEM_SEARCH_SWIPES)
            }.scrollIntoView(UiSelector().text(name))
        }.getOrDefault(false)
        if (foundFromBeginning && device.findObject(By.text(name)) != null) return

        // OEM accessibility implementations occasionally reject UiScrollable actions. Keep a
        // bounded gesture fallback after explicitly returning to the start of the current list.
        repeat(MAX_ITEM_SEARCH_SWIPES) { swipeBackward() }
        device.findObject(By.text(name))?.let { return }
        repeat(MAX_ITEM_SEARCH_SWIPES) {
            swipeForward()
            device.findObject(By.text(name))?.let { return }
        }
        error("Benchmark fixture '$name' was not found after $MAX_ITEM_SEARCH_SWIPES swipes")
    }

    /** Expands optional parent path segments, then positions the requested script file. */
    fun positionScriptItem(name: String, parentPath: String?) {
        parentPath.pathSegments().forEach { directory ->
            positionScriptDirectorySegment(directory)
            clickVisibleNamedItem(directory)
        }
        positionNamedItem(name)
    }

    /** Expands parent segments but leaves the final directory collapsed and visible. */
    fun positionScriptDirectory(directoryPath: String) {
        val segments = directoryPath.pathSegments()
        require(segments.isNotEmpty()) { "Script directory path must not be blank" }
        segments.dropLast(1).forEach { directory ->
            positionScriptDirectorySegment(directory)
            clickVisibleNamedItem(directory)
        }
        positionScriptDirectorySegment(segments.last())
    }

    /** Script roots arrive asynchronously after the tab itself becomes idle. */
    private fun positionScriptDirectorySegment(name: String) {
        if (device.wait(Until.hasObject(By.text(name)), UI_TIMEOUT_MS)) return
        positionNamedItem(name)
    }

    fun clickVisibleScriptDirectory(directoryPath: String) {
        val name = directoryPath.pathSegments().lastOrNull()
            ?: error("Script directory path must not be blank")
        clickVisibleNamedItem(name)
    }

    fun clickTextIfPresent(chinese: String, english: String): Boolean {
        repeat(MAX_STALE_CLICK_RETRIES) {
            val target = findText(chinese, english) ?: return false
            try {
                target.click()
                device.waitForIdle()
                return true
            } catch (_: StaleObjectException) {
                SystemClock.sleep(STALE_RETRY_SETTLE_MS)
            }
        }
        return false
    }

    fun scrollForward(repetitions: Int) {
        repeat(repetitions) { swipeForward() }
        device.waitForIdle()
    }

    fun waitForTaskLogReady() {
        check(
            waitForEitherText(
                "仅显示最新 256 KiB；服务端原始内容未被改写",
                "Showing the latest 256 KiB; server content is unchanged"
            )
        ) {
            "Large task log did not finish loading as a bounded 256 KiB window"
        }
    }

    fun waitForPagedScriptReady() {
        check(waitForEnabledDescription("下一段", "Next section")) {
            "Large script did not finish loading as a multi-page local draft"
        }
    }

    fun pageScriptForward(repetitions: Int) {
        repeat(repetitions) { pageIndex ->
            val button = requireEnabledDescription("下一段", "Next section")
            button.click()
            val expectedPage = pageIndex + 2
            check(waitForPage(expectedPage)) {
                "Large script preview did not advance to page $expectedPage"
            }
        }
        device.waitForIdle()
    }

    fun waitForSubscriptionLogReady() {
        check(waitForEnabledDescription("刷新日志", "Refresh log")) {
            "Subscription log did not finish its initial request"
        }
    }

    /** Loads every 50-item task page before FrameTimingMetric starts, then returns to the top. */
    fun preloadTaskItems(targetItems: Int) {
        val additionalPages = ((targetItems + TASK_PAGE_SIZE - 1) / TASK_PAGE_SIZE - 1)
            .coerceAtLeast(0)
        repeat(additionalPages) { pageIndex ->
            check(clickByScrollingForward("加载更多", "Load more")) {
                    "Could not load task page ${pageIndex + 2}; " +
                        "ensure at least $targetItems tasks exist"
            }
            SystemClock.sleep(LOAD_MORE_SETTLE_MS)
            device.waitForIdle()
        }

        // Loading ends at the bottom of the list. Move back to a stable first-page viewport
        // outside the measured block so every iteration measures the same 500/1000-item state.
        repeat(additionalPages * MAX_SWIPES_PER_PAGE + MAX_SWIPES_PER_PAGE) {
            swipeBackward()
        }
        device.waitForIdle()
    }

    private fun clickByScrollingForward(chinese: String, english: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            repeat(MAX_STALE_CLICK_RETRIES) retry@{
                val target = findText(chinese, english) ?: return@retry
                try {
                    if (!target.isEnabled) return@retry
                    target.click()
                    return true
                } catch (_: StaleObjectException) {
                    SystemClock.sleep(STALE_RETRY_SETTLE_MS)
                }
            }
            // While the next API page is loading the button is temporarily absent/disabled.
            // Keep the viewport following the growing list until the next enabled button lands.
            swipeForward()
        }
        return false
    }

    private fun swipeForward() {
        val x = device.displayWidth / 2
        val startY = (device.displayHeight * 0.78f).toInt()
        val endY = (device.displayHeight * 0.24f).toInt()
        device.swipe(x, startY, x, endY, 12)
        SystemClock.sleep(SWIPE_SETTLE_MS)
    }

    private fun swipeBackward() {
        val x = device.displayWidth / 2
        val startY = (device.displayHeight * 0.24f).toInt()
        val endY = (device.displayHeight * 0.78f).toInt()
        device.swipe(x, startY, x, endY, 12)
        SystemClock.sleep(SWIPE_SETTLE_MS)
    }

    fun waitForPolling(durationMs: Long) {
        SystemClock.sleep(durationMs)
        device.waitForIdle()
    }

    fun pressBack() {
        device.pressBack()
        device.waitForIdle()
    }

    private fun requireText(chinese: String, english: String): UiObject2 =
        device.wait(Until.findObject(By.text(chinese)), UI_TIMEOUT_MS)
            ?: device.wait(Until.findObject(By.text(english)), UI_TIMEOUT_MS)
            ?: error("Could not find '$chinese' or '$english'; ensure a saved benchmark account is logged in")

    private fun clickRequiredText(chinese: String, english: String) {
        repeat(MAX_STALE_CLICK_RETRIES) {
            try {
                requireText(chinese, english).click()
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                SystemClock.sleep(STALE_RETRY_SETTLE_MS)
            }
        }
        error("'$chinese' or '$english' remained stale after $MAX_STALE_CLICK_RETRIES retries")
    }

    private fun findText(chinese: String, english: String): UiObject2? =
        device.findObject(By.text(chinese)) ?: device.findObject(By.text(english))

    private fun waitForEitherText(chinese: String, english: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + CONTENT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (findText(chinese, english) != null) return true
            SystemClock.sleep(STATE_POLL_MS)
        }
        return false
    }

    private fun waitForBottomDestination(tabChinese: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + NAVIGATION_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (isBottomDestinationVisible(tabChinese)) return true
            SystemClock.sleep(STATE_POLL_MS)
        }
        return false
    }

    private fun isBottomDestinationVisible(tabChinese: String): Boolean = when (tabChinese) {
        "首页" -> findText("任务总览", "Task overview") != null
        "任务" -> findText("任务管理", "Tasks") != null &&
            (device.findObject(By.desc("搜索")) != null ||
                device.findObject(By.desc("Search")) != null)
        "脚本" -> findText("脚本管理", "Scripts") != null &&
            findText("订阅", "Subscriptions") != null
        "环境" -> findText("环境变量", "Variables") != null
        "设置" -> findText("服务器管理", "Server management") != null
        else -> false
    }

    private fun isAnyBottomDestinationVisible(): Boolean =
        listOf("首页", "任务", "脚本", "环境", "设置").any(::isBottomDestinationVisible)

    private fun waitForEnabledDescription(chinese: String, english: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + CONTENT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val target = device.findObject(By.desc(chinese)) ?: device.findObject(By.desc(english))
            if (target?.isEnabled == true) return true
            SystemClock.sleep(STATE_POLL_MS)
        }
        return false
    }

    private fun requireEnabledDescription(chinese: String, english: String): UiObject2 {
        check(waitForEnabledDescription(chinese, english)) {
            "Could not find enabled '$chinese' or '$english'"
        }
        return device.findObject(By.desc(chinese)) ?: device.findObject(By.desc(english))
            ?: error("Enabled '$chinese' or '$english' disappeared")
    }

    private fun waitForPage(page: Int): Boolean {
        val pageLabel = Pattern.compile("(?:第\\s*$page\\s*/\\s*\\d+\\s*段|Section\\s+$page\\s*/\\s*\\d+)")
        return device.wait(Until.hasObject(By.text(pageLabel)), CONTENT_TIMEOUT_MS)
    }

    companion object {
        fun forFixtureReadiness(): AzureQlJourneys {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            return AzureQlJourneys(device) {
                // Match Macrobenchmark's per-iteration lifecycle. Reusing an existing Activity
                // would retain TaskViewModel.currentPage and make fixture pagination non-repeatable.
                device.executeShellCommand("am force-stop $TARGET_PACKAGE")
                val result = device.executeShellCommand(
                    "am start -W -n $TARGET_PACKAGE/.MainActivity"
                )
                check("Status: ok" in result) { "Could not start AzureQL: $result" }
            }
        }
    }
}

private fun String?.pathSegments(): List<String> = this
    ?.split('/', '\\')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

private const val TASK_PAGE_SIZE = 50
private const val MAX_SWIPES_PER_PAGE = 16
private const val MAX_ITEM_SEARCH_SWIPES = 120
private const val MAX_BACK_NAVIGATION_ATTEMPTS = 5
private const val MAX_NAVIGATION_RETRIES = 5
private const val MAX_STALE_CLICK_RETRIES = 5
private const val LOAD_MORE_SETTLE_MS = 750L
private const val STALE_RETRY_SETTLE_MS = 100L
private const val SWIPE_SETTLE_MS = 120L
private const val STATE_POLL_MS = 100L
private const val NAVIGATION_TIMEOUT_MS = 5_000L
private const val CONTENT_TIMEOUT_MS = 120_000L
