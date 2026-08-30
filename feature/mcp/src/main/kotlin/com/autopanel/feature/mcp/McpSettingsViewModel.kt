package com.autopanel.feature.mcp

import androidx.lifecycle.ViewModel
import com.autopanel.core.mcp.McpServerEngine
import com.autopanel.core.mcp.McpServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class McpSettingsViewModel @Inject constructor(
    engine: McpServerEngine,
    private val serviceController: McpServiceController
) : ViewModel() {
    val state: StateFlow<McpServerState> = engine.state

    fun startService() = serviceController.start()

    fun stopService() = serviceController.stop()
}
