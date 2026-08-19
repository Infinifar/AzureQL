package com.qinglong.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qinglong.app.BuildConfig
import com.qinglong.app.config.ConfigScreen
import com.qinglong.app.home.HomeScreen
import com.qinglong.feature.env.EnvRoute
import com.qinglong.feature.env.EnvScreen
import com.qinglong.feature.script.ScriptScreen
import com.qinglong.feature.settings.SettingsScreen
import com.qinglong.feature.task.TaskRoute
import com.qinglong.feature.task.TaskScreen

private data class BottomNavItem(val route: Any, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(HomeRoute, "首页", Icons.Default.Home),
    BottomNavItem(TaskRoute, "任务", Icons.Default.Schedule),
    BottomNavItem(ScriptsRoute, "脚本", Icons.Default.Code),
    BottomNavItem(EnvRoute, "环境", Icons.Default.Layers),
    BottomNavItem(SettingsRoute, "设置", Icons.Default.Settings)
)

@Composable
fun QLNavScaffold(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hasRoute(item.route::class) == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding)
        ) {
            composable<HomeRoute> { HomeScreen() }
            composable<TaskRoute> { TaskScreen() }
            composable<ScriptsRoute> { ScriptScreen() }
            composable<EnvRoute> { EnvScreen() }
            composable<SettingsRoute> { SettingsScreen(onLogout = onLogout, clientVersion = BuildConfig.VERSION_NAME) }
            composable<ConfigRoute> { ConfigScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
