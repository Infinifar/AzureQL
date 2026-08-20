package com.autopanel.core.ui.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

object DeviceAuthenticator {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isAvailable(activity: Activity): Boolean =
        activity.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        onResult: (AuthenticationResult) -> Unit
    ) {
        if (!isAvailable(activity)) {
            onResult(AuthenticationResult.Error("设备未设置可用的生物识别或锁屏凭据"))
            return
        }

        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onResult(AuthenticationResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onResult(AuthenticationResult.Error(errString?.toString().orEmpty()))
                }

                override fun onAuthenticationFailed() {
                    onResult(AuthenticationResult.Failed)
                }
            }
        )
    }
}

sealed interface AuthenticationResult {
    data object Success : AuthenticationResult
    data object Failed : AuthenticationResult
    data class Error(val message: String) : AuthenticationResult
}
