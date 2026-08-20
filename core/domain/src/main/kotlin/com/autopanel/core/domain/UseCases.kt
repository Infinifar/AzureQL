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

class LoginClientCredentialsUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(clientId: String, clientSecret: String): LoginResult {
        return authRepository.loginWithClientCredentials(clientId, clientSecret)
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
        remember: Boolean = false,
        allowInsecureHttp: Boolean = false,
        isClientCredentials: Boolean = false
    ) {
        authRepository.saveCredentials(
            host = host,
            username = username,
            password = password,
            token = token,
            alias = alias,
            remember = remember,
            allowInsecureHttp = allowInsecureHttp,
            isClientCredentials = isClientCredentials
        )
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
