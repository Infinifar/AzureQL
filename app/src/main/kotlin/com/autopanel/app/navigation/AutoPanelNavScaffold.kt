package com.autopanel.app.navigation

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
import com.autopanel.app.BuildConfig
import com.autopanel.app.config.ConfigScreen
import com.autopanel.app.home.HomeScreen
import com.autopanel.feature.backup.BackupRoute
import com.autopanel.feature.backup.BackupScreen
import com.autopanel.feature.dependency.DepRoute
import com.autopanel.feature.dependency.DepScreen
import com.autopanel.feature.dependency.DepSettingsRoute
import com.autopanel.feature.dependency.DependencySettingsScreen
import com.autopanel.feature.env.EnvRoute
import com.autopanel.feature.env.EnvScreen
import com.autopanel.feature.log.LogRoute
import com.autopanel.feature.log.LogScreen
import com.autopanel.feature.script.ScriptScreen
import com.autopanel.feature.settings.SettingsScreen
import com.autopanel.feature.task.TaskRoute
import com.autopanel.feature.task.TaskScreen

private data class BottomNavItem(val route: Any, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(HomeRoute, "首页", Icons.Default.Home),
    BottomNavItem(TaskRoute, "任务", Icons.Default.Schedule),
    BottomNavItem(ScriptsRoute, "脚本", Icons.Default.Code),
    BottomNavItem(EnvRoute, "环境", Icons.Default.Layers),
    BottomNavItem(SettingsRoute, "设置", Icons.Default.Settings)
)

@Composable
fun AutoPanelNavScaffold(onLogout: () -> Unit) {
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
            composable<SettingsRoute> {
                SettingsScreen(
                    onLogout = onLogout,
                    onOpenBackup = { navController.navigate(BackupRoute) },
                    onOpenDependencies = { navController.navigate(DepRoute) },
                    onOpenLogs = { navController.navigate(LogRoute) },
                    clientVersion = BuildConfig.VERSION_NAME
                )
            }
            composable<BackupRoute> {
                BackupScreen(
                    onBack = { navController.popBackStack() },
                    onRestoreCompleted = onLogout
                )
            }
            composable<DepRoute> {
                DepScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(DepSettingsRoute) }
                )
            }
            composable<DepSettingsRoute> {
                DependencySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable<LogRoute> {
                LogScreen(onBack = { navController.popBackStack() })
            }
            composable<ConfigRoute> { ConfigScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
