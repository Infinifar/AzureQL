package com.autopanel.core.domain

import com.autopanel.core.model.LoginRequest
import com.autopanel.core.model.LoginResult
import com.autopanel.core.model.TwoFactorRequest
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): LoginResult {
        return authRepository.login(LoginRequest(username, password))
    }
}

class LoginTwoFactorUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        code: String
    ): LoginResult {
        return authRepository.loginTwoFactor(TwoFactorRequest(username, password, code))
    }
}

class SaveCredentialsUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        host: String,
        username: String,
        password: String,
        token: String,
        alias: String? = null,
        remember: Boolean = false
    ) {
        authRepository.saveCredentials(host, username, password, token, alias, remember)
    }
}

class GetTokenUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): String? = authRepository.getToken()
}

class GetHostUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): String? = authRepository.getHost()
}

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() {
        authRepository.clearCredentials()
    }
}
