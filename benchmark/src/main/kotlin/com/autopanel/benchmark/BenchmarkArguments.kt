package com.autopanel.benchmark

import androidx.test.platform.app.InstrumentationRegistry

internal object BenchmarkArguments {
    private val arguments
        get() = InstrumentationRegistry.getArguments()

    fun required(key: String): String = arguments.getString(key)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error(
            "Missing instrumentation argument '$key'. " +
                "See benchmark/README.md for the required QingLong fixture names."
        )

    fun requiredInt(key: String, minimum: Int): Int {
        val value = required(key).toIntOrNull()
            ?: error("Instrumentation argument '$key' must be an integer")
        require(value >= minimum) { "$key must be >= $minimum, but was $value" }
        return value
    }

    fun optional(key: String): String? = arguments.getString(key)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}
