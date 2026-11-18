package com.example.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.di.ClipFreeContainer
import com.example.domain.model.AccountSession
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClipAdjustScreen
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ClipAdjustViewModel
import com.example.ui.viewmodel.CreateViewModel
import com.example.ui.viewmodel.ProfileViewModel
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ProjectsRoute
@Serializable data object CreateRoute
@Serializable data object LibraryRoute
@Serializable data object ProfileRoute
@Serializable data class ClipAdjustRoute(val clipId: String)

private data class TopNavItem(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun ClipFreeApp(
    container: ClipFreeContainer,
    appViewModel: AppViewModel,
    widthSizeClass: WindowWidthSizeClass
) {
    val context = LocalContext.current
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
    val projects by appViewModel.projects.collectAsStateWithLifecycle()
    val allClips by appViewModel.allClips.collectAsStateWithLifecycle()
    val allExports by appViewModel.allExports.collectAsStateWithLifecycle()
    val allThumbnails by appViewModel.allThumbnails.collectAsStateWithLifecycle()
    val aiAvailability by appViewModel.aiAvailability.collectAsStateWithLifecycle()
    val authLoading by appViewModel.authLoading.collectAsStateWithLifecycle()
    val authError by appViewModel.authError.collectAsStateWithLifecycle()
    val toastMessage by appViewModel.toastMessage.collectAsStateWithLifecycle()

    if (session is AccountSession.Unauthenticated) {
        AuthScreen(
            isLoading = authLoading,
            errorMessage = authError,
            onGoogleSignIn = { appViewModel.signInWithGoogle(context) },
            onContinueGuest = { appViewModel.enterGuestMode() }
        )
        return
    }

    val sessionKey = when (val s = session) {
        is AccountSession.GoogleUser -> s.uid
        AccountSession.Guest -> "guest"
        AccountSession.Unauthenticated -> "unauth"
    }

    key(sessionKey) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        var libraryInitialTab by remember { mutableIntStateOf(0) }

        val createViewModel: CreateViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CreateViewModel(
                        appContext = container.applicationContext,
                        repository = container.projectRepository,
                        workManager = container.workManager,
                        preferencesManager = container.preferencesManager,
                        cloudSyncRepository = container.cloudSyncRepository,
                        aiRouter = container.aiProviderRouter
                    ) as T
                }
            }
        )

        val profileViewModel: ProfileViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(
                        appContext = container.applicationContext,
                        repository = container.projectRepository,
                        preferencesManager = container.preferencesManager,
                        aiRouter = container.aiProviderRouter
                    ) as T
                }
            }
        )

        LaunchedEffect(toastMessage) {
            toastMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                appViewModel.clearToast()
            }
        }

        val topItems = listOf(
            TopNavItem(HomeRoute, "Home", Icons.Rounded.Home, "nav_home"),
            TopNavItem(ProjectsRoute, "Projects", Icons.Rounded.Folder, "nav_projects"),
            TopNavItem(CreateRoute, "Create", Icons.Rounded.AutoAwesome, "nav_create"),
            TopNavItem(LibraryRoute, "Library", Icons.Rounded.VideoLibrary, "nav_library"),
            TopNavItem(ProfileRoute, "Profile", Icons.Rounded.Person, "nav_profile")
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val showChrome = currentDestination?.hasRoute<ClipAdjustRoute>() != true
        val useRail = widthSizeClass != WindowWidthSizeClass.Compact

        val navigateTopLevel: (Any) -> Unit = { target ->
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showChrome && !useRail) {
                    NavigationBar {
                        topItems.forEach { item ->
                            val selected = when (item.route) {
                                is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                is ProjectsRoute -> currentDestination?.hasRoute<ProjectsRoute>() == true
                                is CreateRoute -> currentDestination?.hasRoute<CreateRoute>() == true
                                is LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useRail) {
                    NavigationRail {
                        topItems.forEach { item ->
                            val selected = when (item.route) {
                                is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                is ProjectsRoute -> currentDestination?.hasRoute<ProjectsRoute>() == true
                                is CreateRoute -> currentDestination?.hasRoute<CreateRoute>() == true
                                is LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            session = session,
                            aiAvailability = aiAvailability,
                            projects = projects,
                            recentClips = allClips,
                            exports = allExports,
                            onUploadVideoClick = { navigateTopLevel(CreateRoute) },
                            onAutoCreateClick = { navigateTopLevel(CreateRoute) },
                            onOpenProject = { pid ->
                                createViewModel.openExistingProject(pid)
                                navigateTopLevel(CreateRoute)
                            },
                            onOpenClip = { cid ->
                                navController.navigate(ClipAdjustRoute(cid))
                            },
                            onOpenDownloads = {
                                libraryInitialTab = 1
                                navigateTopLevel(LibraryRoute)
                            },
                            onCancelProcessing = { _ ->
                                createViewModel.cancelActiveProcessing()
                            }
                        )
                    }

                    composable<ProjectsRoute> {
                        ProjectsScreen(
                            projects = projects,
                            onOpenProject = { pid ->
                                createViewModel.openExistingProject(pid)
                                navigateTopLevel(CreateRoute)
                            },
                            onRenameProject = { pid, name -> appViewModel.renameProject(pid, name) },
                            onDeleteProject = { pid -> appViewModel.deleteProject(pid) },
                            onCreateNew = { navigateTopLevel(CreateRoute) }
                        )
                    }

                    composable<CreateRoute> {
                        CreateScreen(
                            viewModel = createViewModel,
                            aiAvailability = aiAvailability,
                            onOpenClip = { cid ->
                                navController.navigate(ClipAdjustRoute(cid))
                            }
                        )
                    }

                    composable<LibraryRoute> {
                        LibraryScreen(
                            clips = allClips,
                            exports = allExports,
                            thumbnails = allThumbnails,
                            projects = projects,
                            initialTab = libraryInitialTab,
                            onOpenClip = { cid -> navController.navigate(ClipAdjustRoute(cid)) },
                            onOpenProject = { pid ->
                                createViewModel.openExistingProject(pid)
                                navigateTopLevel(CreateRoute)
                            },
                            onDeleteClip = { cid -> appViewModel.deleteClip(cid) },
                            onDeleteExport = { exp -> appViewModel.deleteExport(exp) },
                            onDeleteThumbnail = { t -> appViewModel.deleteThumbnail(t) },
                            onDeleteProject = { pid -> appViewModel.deleteProject(pid) },
                            onMessage = { msg -> appViewModel.showToast(msg) }
                        )
                    }

                    composable<ProfileRoute> {
                        ProfileScreen(
                            viewModel = profileViewModel,
                            session = session,
                            preferences = preferences,
                            aiAvailability = aiAvailability,
                            onSignInGoogle = { appViewModel.signInWithGoogle(context) },
                            onSignOut = { appViewModel.signOut() },
                            onDeleteAccount = { appViewModel.deleteGoogleAccount() },
                            onMessage = { msg -> appViewModel.showToast(msg) }
                        )
                    }

                    composable<ClipAdjustRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ClipAdjustRoute>()
                        val adjustVm: ClipAdjustViewModel = viewModel(
                            key = "adjust_${route.clipId}",
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return ClipAdjustViewModel(
                                        appContext = container.applicationContext,
                                        clipId = route.clipId,
                                        repository = container.projectRepository,
                                        aiRouter = container.aiProviderRouter
                                    ) as T
                                }
                            }
                        )
                        ClipAdjustScreen(
                            viewModel = adjustVm,
                            onBack = { navController.popBackStack() },
                            onMessage = { msg -> appViewModel.showToast(msg) }
                        )
                    }
                }
            }
        }
    }
}
