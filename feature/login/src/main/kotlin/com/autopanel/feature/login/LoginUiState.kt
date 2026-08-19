package com.autopanel.feature.login

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class NeedTwoFactor(val username: String, val password: String) : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}
