package com.autopanel.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.data.remote.AutoPanelRetrofitClient
import com.autopanel.core.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val retrofitClient: AutoPanelRetrofitClient
) : ViewModel() {

    /** One eagerly loaded snapshot gates splash removal and the first rendered app frame. */
    val startupState: StateFlow<AppStartupState> = flow {
        try {
            retrofitClient.prepareCurrent()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A failed warm-up must not leave the splash visible forever. Feature repositories
            // still surface the actionable TLS/network error when the user retries.
        }
        emitAll(
            combine(
                sessionManager.tokenFlow,
                sessionManager.darkModeFlow,
                sessionManager.themeColorFlow,
                sessionManager.dynamicColorFlow
            ) { token, darkMode, themeColor, dynamicColor ->
                AppStartupState(
                    isReady = true,
                    isLoggedIn = token != null,
                    darkMode = darkMode,
                    themeColor = themeColor,
                    dynamicColor = dynamicColor
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppStartupState()
    )

    fun logout() {
        viewModelScope.launch { sessionManager.clearSession() }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch { sessionManager.setDarkMode(mode) }
    }
}

data class AppStartupState(
    val isReady: Boolean = false,
    val isLoggedIn: Boolean = false,
    val darkMode: String = "system",
    val themeColor: String? = null,
    val dynamicColor: Boolean = false
)
