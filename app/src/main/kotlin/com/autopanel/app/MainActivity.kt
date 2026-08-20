package com.autopanel.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autopanel.app.navigation.HomeRoute
import com.autopanel.app.navigation.LoginRoute
import com.autopanel.app.navigation.AutoPanelNavScaffold
import com.autopanel.core.ui.theme.AutoPanelTheme
import com.autopanel.core.ui.theme.parseSeedColor
import com.autopanel.core.data.preferences.LocalAppPreferences
import com.autopanel.core.ui.security.AuthenticationResult
import com.autopanel.core.ui.security.DeviceAuthenticator
import com.autopanel.feature.login.LoginScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var localAppPreferences: LocalAppPreferences

    private var appUnlocked by mutableStateOf(true)
    private var authenticationInProgress = false
    private var authenticationError by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalAppPreferences.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUnlocked = !localAppPreferences.biometricEnabled
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val darkMode by appViewModel.darkMode.collectAsStateWithLifecycle()
            val dynamicColor by appViewModel.dynamicColor.collectAsStateWithLifecycle()
            val themeColor by appViewModel.themeColor.collectAsStateWithLifecycle()
            val darkTheme = when (darkMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            AutoPanelTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                seedColor = parseSeedColor(themeColor)
            ) {
                if (appUnlocked) {
                    AutoPanelApp(appViewModel)
                } else {
                    AppLockScreen(
                        error = authenticationError,
                        onUnlock = ::requestUnlock
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!localAppPreferences.biometricEnabled) {
            appUnlocked = true
            authenticationInProgress = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (!appUnlocked) {
            window.decorView.post(::requestUnlock)
        }
    }

    override fun onStop() {
        if (localAppPreferences.biometricEnabled && !isChangingConfigurations) {
            appUnlocked = false
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onStop()
    }

    private fun requestUnlock() {
        if (!localAppPreferences.biometricEnabled) {
            appUnlocked = true
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            return
        }
        if (authenticationInProgress) return

        authenticationInProgress = true
        authenticationError = null
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        DeviceAuthenticator.authenticate(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle)
        ) { result ->
            when (result) {
                AuthenticationResult.Success -> {
                    authenticationInProgress = false
                    appUnlocked = true
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                AuthenticationResult.Failed -> Unit
                is AuthenticationResult.Error -> {
                    authenticationInProgress = false
                    authenticationError = result.message.ifBlank {
                        getString(R.string.biometric_auth_failed)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockScreen(error: String?, onUnlock: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                Icons.Default.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_locked_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                error ?: stringResource(R.string.app_locked_message),
                color = if (error == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text(stringResource(R.string.unlock)) }
        }
    }
}

@Composable
private fun AutoPanelApp(appViewModel: AppViewModel) {
    val isLoggedIn by appViewModel.isLoggedIn.collectAsStateWithLifecycle()

    if (isLoggedIn == null) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val navController = rememberNavController()
    val startDestination = if (isLoggedIn == true) HomeRoute else LoginRoute

    NavHost(navController = navController, startDestination = startDestination) {
        composable<LoginRoute> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(HomeRoute) {
                        popUpTo<LoginRoute> { inclusive = true }
                    }
                }
            )
        }
        composable<HomeRoute> {
            AutoPanelNavScaffold(
                onLogout = {
                    appViewModel.logout()
                    navController.navigate(LoginRoute) {
                        popUpTo<HomeRoute> { inclusive = true }
                    }
                }
            )
        }
    }
}
