package com.autopanel.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.autopanel.app.BuildConfig
import com.autopanel.app.config.ConfigScreen
import com.autopanel.app.home.HomeScreen
import com.autopanel.feature.backup.BackupRoute
import com.autopanel.feature.backup.BackupScreen
import com.autopanel.feature.backup.NetworkStorageSettingsRoute
import com.autopanel.feature.backup.NetworkStorageSettingsScreen
import com.autopanel.feature.dependency.DepRoute
import com.autopanel.feature.dependency.DepScreen
import com.autopanel.feature.dependency.DepSettingsRoute
import com.autopanel.feature.dependency.DependencySettingsScreen
import com.autopanel.feature.env.EnvScreen
import com.autopanel.feature.log.LogRoute
import com.autopanel.feature.log.LogScreen
import com.autopanel.feature.mcp.McpRoute
import com.autopanel.feature.mcp.McpSettingsScreen
import com.autopanel.feature.script.ScriptScreen
import com.autopanel.feature.settings.SettingsScreen
import com.autopanel.feature.settings.AccountManagementRoute
import com.autopanel.feature.settings.AccountManagementScreen
import com.autopanel.feature.task.TaskScreen
import kotlinx.coroutines.flow.distinctUntilChanged

private data class BottomNavItem(
    val page: Int,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(0, com.autopanel.app.R.string.nav_home, Icons.Default.Home),
    BottomNavItem(1, com.autopanel.app.R.string.nav_tasks, Icons.Default.Schedule),
    BottomNavItem(2, com.autopanel.app.R.string.nav_scripts, Icons.Default.Code),
    BottomNavItem(3, com.autopanel.app.R.string.nav_environment, Icons.Default.Layers),
    BottomNavItem(4, com.autopanel.app.R.string.nav_settings, Icons.Default.Settings)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoPanelNavScaffold(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val scriptOpenRequestId = remember { mutableLongStateOf(0L) }
    var openScriptPath by remember { mutableStateOf<String?>(null) }
    var targetMainPage by rememberSaveable { mutableIntStateOf(0) }
    val mainPagerState = rememberPagerState(
        initialPage = targetMainPage,
        pageCount = { bottomNavItems.size }
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isMainDestination = currentDestination?.hasRoute<HomeRoute>() == true

    LaunchedEffect(mainPagerState) {
        snapshotFlow { mainPagerState.settledPage }
            .distinctUntilChanged()
            .collect { targetMainPage = it }
    }

    LaunchedEffect(isMainDestination, targetMainPage) {
        if (isMainDestination && mainPagerState.currentPage != targetMainPage) {
            mainPagerState.animateScrollToPage(targetMainPage)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val label = stringResource(item.labelRes)
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = isMainDestination && mainPagerState.currentPage == item.page,
                        onClick = {
                            targetMainPage = item.page
                            if (!isMainDestination) {
                                navController.navigate(HomeRoute) {
                                    popUpTo<HomeRoute> { inclusive = false }
                                    launchSingleTop = true
                                }
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
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable<HomeRoute> {
                HorizontalPager(
                    state = mainPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> HomeScreen()
                        1 -> TaskScreen(
                            onOpenScript = { path ->
                                openScriptPath = path
                                scriptOpenRequestId.longValue += 1L
                                targetMainPage = 2
                            }
                        )
                        2 -> ScriptScreen(
                            openScriptPath = openScriptPath,
                            openRequestId = scriptOpenRequestId.longValue
                        )
                        3 -> EnvScreen()
                        4 -> SettingsScreen(
                            onLogout = onLogout,
                            onOpenAccounts = { navController.navigate(AccountManagementRoute) },
                            onOpenBackup = { navController.navigate(BackupRoute) },
                            onOpenDependencies = { navController.navigate(DepRoute) },
                            onOpenLogs = { navController.navigate(LogRoute) },
                            onOpenMcp = { navController.navigate(McpRoute) },
                            clientVersion = BuildConfig.VERSION_NAME
                        )
                        else -> error("Unsupported main page: $page")
                    }
                }
            }
            composable<BackupRoute> {
                BackupScreen(
                    onBack = { navController.popBackStack() },
                    onRestoreCompleted = onLogout,
                    onOpenNetworkStorageSettings = {
                        navController.navigate(NetworkStorageSettingsRoute)
                    }
                )
            }
            composable<AccountManagementRoute> {
                AccountManagementScreen(
                    onBack = { navController.popBackStack() },
                    onAddAccount = onLogout,
                    onAccountActivated = {
                        navController.navigate(HomeRoute) {
                            popUpTo<HomeRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSignInRequired = onLogout
                )
            }
            composable<NetworkStorageSettingsRoute> {
                NetworkStorageSettingsScreen(onBack = { navController.popBackStack() })
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
            composable<McpRoute> {
                McpSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable<ConfigRoute> { ConfigScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
