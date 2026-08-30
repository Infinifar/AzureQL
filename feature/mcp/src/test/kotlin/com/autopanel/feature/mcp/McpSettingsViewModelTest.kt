package com.autopanel.feature.mcp

import com.autopanel.core.mcp.McpServerConfig
import com.autopanel.core.mcp.McpServerEngine
import com.autopanel.core.mcp.McpServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSettingsViewModelTest {
    @Test
    fun `screen actions delegate to service controller and expose engine state`() {
        val engine = FakeEngine()
        val controller = FakeController()
        val viewModel = McpSettingsViewModel(engine, controller)

        engine.mutableState.value = McpServerState.Running("http://127.0.0.1:18765/mcp")
        viewModel.startService()
        viewModel.stopService()

        assertEquals(engine.mutableState.value, viewModel.state.value)
        assertTrue(controller.started)
        assertTrue(controller.stopped)
    }
}

private class FakeController : McpServiceController {
    var started = false
    var stopped = false

    override fun start() {
        started = true
    }

    override fun stop() {
        stopped = true
    }
}

private class FakeEngine : McpServerEngine {
    val mutableState = MutableStateFlow<McpServerState>(McpServerState.Stopped)
    override val state: StateFlow<McpServerState> = mutableState

    override suspend fun start(config: McpServerConfig) = Unit
    override suspend fun stop() = Unit
}
