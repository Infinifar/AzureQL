package com.autopanel.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autopanel.core.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean?> = sessionManager.tokenFlow
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val darkMode: StateFlow<String> = sessionManager.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    fun logout() {
        viewModelScope.launch { sessionManager.clearSession() }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch { sessionManager.setDarkMode(mode) }
    }
}
