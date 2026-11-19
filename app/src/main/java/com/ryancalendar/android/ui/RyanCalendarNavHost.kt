package com.ryancalendar.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ryancalendar.android.domain.util.DateUtils
import com.ryancalendar.android.ui.screens.CalendarScreen
import com.ryancalendar.android.ui.screens.CategoriesScreen
import com.ryancalendar.android.ui.screens.EventEditorScreen
import com.ryancalendar.android.ui.screens.LoginScreen
import com.ryancalendar.android.ui.screens.ProfileScreen
import com.ryancalendar.android.ui.screens.SearchScreen
import com.ryancalendar.android.ui.screens.StatsScreen
import com.ryancalendar.android.ui.screens.UpcomingScreen
import com.ryancalendar.android.ui.theme.AppColors
import java.time.LocalDate

private enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    CALENDAR("tab_calendar", "日程", Icons.Outlined.CalendarMonth),
    UPCOMING("tab_upcoming", "近期", Icons.Outlined.Notifications),
    STATS("tab_stats", "统计", Icons.Outlined.BarChart),
    PROFILE("tab_profile", "我的", Icons.Outlined.Person)
}

private object Routes {
    const val SEARCH = "search"
    const val CATEGORIES = "categories"
    const val EVENT_EDITOR = "event_editor?eventId={eventId}&date={date}"

    fun eventEditor(eventId: String?, date: LocalDate): String {
        val eid = eventId.orEmpty()
        return "event_editor?eventId=$eid&date=$date"
    }
}

@Composable
fun RyanCalendarRoot(viewModel: AppViewModel) {
    val authState by viewModel.authState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val loginLoading by viewModel.loginLoading.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (authState) {
        AuthState.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.CanvasBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载工作日程...", fontSize = 14.sp, color = AppColors.TextSecondary)
                }
            }
        }

        AuthState.Unauthenticated -> {
            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    LoginScreen(
                        initialServerUrl = serverUrl,
                        isLoading = loginLoading,
                        errorMessage = loginError,
                        onLogin = { url, user, pwd -> viewModel.login(url, user, pwd) }
                    )
                }
            }
        }

        AuthState.Authenticated -> {
            AuthenticatedShell(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@Composable
private fun AuthenticatedShell(
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedDate by viewModel.selectedDate.collectAsState()

    val bottomTabRoutes = remember { BottomTab.entries.map { it.route }.toSet() }
    val showBottomBar = currentRoute in bottomTabRoutes
    val showFab = currentRoute == BottomTab.CALENDAR.route || currentRoute == BottomTab.UPCOMING.route

    Scaffold(
        containerColor = AppColors.CanvasBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(Routes.eventEditor(null, selectedDate))
                    },
                    containerColor = AppColors.Primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建日程")
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = AppColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    BottomTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppColors.Primary,
                                selectedTextColor = AppColors.Primary,
                                indicatorColor = AppColors.PrimarySoft,
                                unselectedIconColor = AppColors.TextSecondary,
                                unselectedTextColor = AppColors.TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.CALENDAR.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomTab.CALENDAR.route) {
                CalendarScreen(
                    viewModel = viewModel,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenEventEditor = { eventId, date ->
                        navController.navigate(Routes.eventEditor(eventId, date))
                    }
                )
            }

            composable(BottomTab.UPCOMING.route) {
                UpcomingScreen(
                    viewModel = viewModel,
                    onOpenEvent = { eventId ->
                        navController.navigate(Routes.eventEditor(eventId, selectedDate))
                    }
                )
            }

            composable(BottomTab.STATS.route) {
                StatsScreen(viewModel = viewModel)
            }

            composable(BottomTab.PROFILE.route) {
                ProfileScreen(
                    viewModel = viewModel,
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSelectEvent = { ev ->
                        val d = DateUtils.parseOrNull(ev.startDate) ?: selectedDate
                        viewModel.selectDate(d)
                        navController.navigate(Routes.eventEditor(ev.id, d))
                    }
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.EVENT_EDITOR,
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("date") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val eid = entry.arguments?.getString("eventId")?.takeIf { it.isNotBlank() }
                val dateArg = entry.arguments?.getString("date")
                val initDate = DateUtils.parseOrNull(dateArg) ?: selectedDate
                EventEditorScreen(
                    viewModel = viewModel,
                    eventId = eid,
                    initialDate = initDate,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
