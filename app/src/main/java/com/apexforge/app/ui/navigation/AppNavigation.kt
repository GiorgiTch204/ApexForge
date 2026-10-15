package com.apexforge.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apexforge.app.data.local.WorkoutSessionEntity
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.ui.AppViewModel
import com.apexforge.app.ui.account.AccountScreen
import com.apexforge.app.ui.admin.AdminScreen
import com.apexforge.app.ui.components.ApexDivider
import com.apexforge.app.ui.formatClock
import com.apexforge.app.ui.history.HistoryScreen
import com.apexforge.app.ui.home.HomeScreen
import com.apexforge.app.ui.library.ExerciseDetailScreen
import com.apexforge.app.ui.library.LibraryScreen
import com.apexforge.app.ui.onboarding.OnboardingScreen
import com.apexforge.app.ui.profile.EditProfileScreen
import com.apexforge.app.ui.profile.ProfileScreen
import com.apexforge.app.ui.profile.SettingsScreen
import com.apexforge.app.ui.progress.ExerciseProgressScreen
import com.apexforge.app.ui.progress.ProgressScreen
import com.apexforge.app.ui.rememberElapsedSeconds
import com.apexforge.app.ui.routines.RoutineEditorScreen
import com.apexforge.app.ui.routines.RoutinesScreen
import com.apexforge.app.ui.water.WaterScreen
import com.apexforge.app.ui.workout.ActiveWorkoutScreen
import com.apexforge.app.ui.workout.WorkoutDetailScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"

    const val EXERCISE = "exercise/{id}"
    const val EXERCISE_PROGRESS = "exercise_progress/{id}"
    const val ROUTINE_EDIT = "routine/{id}"
    const val WORKOUT = "workout/{sessionId}"
    const val SUMMARY = "summary/{sessionId}?fresh={fresh}"
    const val HISTORY = "history"
    const val WATER = "water"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit_profile"
    const val ACCOUNT = "account"
    const val ADMIN = "admin"

    fun exercise(id: String) = "exercise/$id"
    fun exerciseProgress(id: String) = "exercise_progress/$id"
    fun routineEdit(id: String) = "routine/$id"
    fun workout(sessionId: String) = "workout/$sessionId"
    fun summary(sessionId: String, fresh: Boolean = false) = "summary/$sessionId?fresh=$fresh"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "Home", Icons.Rounded.Home),
    Workouts(Routes.WORKOUTS, "Workouts", Icons.Rounded.FitnessCenter),
    Library(Routes.LIBRARY, "Exercises", Icons.Rounded.GridView),
    Progress(Routes.PROGRESS, "Progress", Icons.Rounded.BarChart),
    Profile(Routes.PROFILE, "Profile", Icons.Rounded.Person),
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ApexApp(appViewModel: AppViewModel, prefs: UserPrefs) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val activeSession by appViewModel.activeSession.collectAsStateWithLifecycle()

    val tabRoutes = Tab.entries.map { it.route }
    val showTabs = route in tabRoutes
    val showMini = activeSession != null && route != null && route != Routes.ONBOARDING &&
        route != Routes.WORKOUT && route != Routes.SUMMARY

    // After "delete all data" the preferences reset, so send the user back to onboarding.
    LaunchedEffect(prefs.onboardingDone, route) {
        if (!prefs.onboardingDone && route != null && route != Routes.ONBOARDING) {
            navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (showMini) {
                    activeSession?.let { session ->
                        ActiveWorkoutBar(
                            session = session,
                            onResume = { navController.navigate(Routes.workout(session.id)) { launchSingleTop = true } },
                            padForNavBar = !showTabs,
                        )
                    }
                }
                if (showTabs) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { if (route != tab.route) navController.navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = if (prefs.onboardingDone) Routes.MAIN else Routes.ONBOARDING,
            modifier = Modifier.padding(inner),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 10 } },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    },
                )
            }

            navigation(startDestination = Routes.HOME, route = Routes.MAIN) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                        onOpenWater = { navController.navigate(Routes.WATER) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenLibrary = { navController.navigateTab(Routes.LIBRARY) },
                        onOpenRoutines = { navController.navigateTab(Routes.WORKOUTS) },
                        onOpenProfile = { navController.navigateTab(Routes.PROFILE) },
                        onOpenProgress = { navController.navigateTab(Routes.PROGRESS) },
                        onOpenExercise = { navController.navigate(Routes.exercise(it)) },
                    )
                }
                composable(Routes.WORKOUTS) {
                    RoutinesScreen(
                        onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                        onEditRoutine = { navController.navigate(Routes.routineEdit(it)) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(onOpenExercise = { navController.navigate(Routes.exercise(it)) })
                }
                composable(Routes.PROGRESS) {
                    ProgressScreen(
                        onOpenExerciseProgress = { navController.navigate(Routes.exerciseProgress(it)) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenWater = { navController.navigate(Routes.WATER) },
                        onOpenExercise = { navController.navigate(Routes.exercise(it)) },
                        onOpenRoutines = { navController.navigateTab(Routes.WORKOUTS) },
                    )
                }
            }

            composable(Routes.EXERCISE, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                ExerciseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { navController.navigate(Routes.exercise(it)) },
                    onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                    onOpenProgress = { navController.navigate(Routes.exerciseProgress(it)) },
                )
            }
            composable(Routes.EXERCISE_PROGRESS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                ExerciseProgressScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ROUTINE_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                RoutineEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.WORKOUT, arguments = listOf(navArgument("sessionId") { type = NavType.StringType })) {
                ActiveWorkoutScreen(
                    onBack = { navController.popBackStack() },
                    onFinished = { id ->
                        navController.navigate(Routes.summary(id, fresh = true)) {
                            popUpTo(Routes.WORKOUT) { inclusive = true }
                        }
                    },
                    onDiscarded = { navController.popBackStack() },
                )
            }
            composable(
                Routes.SUMMARY,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.StringType },
                    navArgument("fresh") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                WorkoutDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                    onOpenExercise = { navController.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSession = { navController.navigate(Routes.summary(it)) },
                )
            }
            composable(Routes.WATER) { WaterScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                )
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                )
            }
            composable(Routes.ADMIN) { AdminScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

/** Persistent "workout in progress" strip so an unfinished session is always one tap away. */
@Composable
private fun ActiveWorkoutBar(session: WorkoutSessionEntity, onResume: () -> Unit, padForNavBar: Boolean) {
    val elapsed by rememberElapsedSeconds(session.startedAt)
    Surface(
        onClick = onResume,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(if (padForNavBar) Modifier.navigationBarsPadding() else Modifier) {
            ApexDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text("Workout in progress", style = MaterialTheme.typography.labelMedium)
                    Text(session.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
                Text(formatClock(elapsed), style = MaterialTheme.typography.titleMedium)
                Text("Resume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
