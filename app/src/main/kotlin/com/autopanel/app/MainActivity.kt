package com.autopanel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.autopanel.feature.login.LoginScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                AutoPanelApp(appViewModel)
            }
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
