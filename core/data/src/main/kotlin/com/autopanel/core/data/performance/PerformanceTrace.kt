package com.autopanel.core.data.performance

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

private val asyncTraceCookie = AtomicInteger()

internal fun <T> performanceTrace(name: String, block: () -> T): T {
    val tracing = tryBeginSection(name)
    return try {
        block()
    } finally {
        if (tracing) Trace.endSection()
    }
}

internal suspend fun <T> performanceTraceAsync(
    name: String,
    block: suspend () -> T
): T {
    val cookie = asyncTraceCookie.incrementAndGet()
    val tracing = tryBeginAsyncSection(name, cookie)
    return try {
        block()
    } finally {
        if (tracing) Trace.endAsyncSection(name, cookie)
    }
}

/** Android framework methods are unimplemented stubs in local JVM tests. */
private fun tryBeginSection(name: String): Boolean = try {
    Trace.beginSection(name)
    true
} catch (_: RuntimeException) {
    false
}

private fun tryBeginAsyncSection(name: String, cookie: Int): Boolean = try {
    Trace.beginAsyncSection(name, cookie)
    true
} catch (_: RuntimeException) {
    false
}
