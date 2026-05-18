package com.thetwo.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.thetwo.app.analytics.AnalyticsEvents
import com.thetwo.app.auth.AuthViewModel
import com.thetwo.app.auth.LoginScreen
import com.thetwo.app.auth.VerifyScreen
import com.thetwo.app.chat.ChatScreen
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.companion.CompanionSetupScreen
import com.thetwo.app.companion.CompanionSetupViewModel
import com.thetwo.app.launch.LaunchScreen
import com.thetwo.app.launch.LaunchTarget
import com.thetwo.app.launch.LaunchViewModel
import com.thetwo.app.network.AppContainer
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.settings.SettingsScreen
import com.thetwo.app.settings.SettingsViewModel
import com.thetwo.app.summon.CapturePreviewScreen
import com.thetwo.app.summon.SummonExperienceScreen
import com.thetwo.app.summon.SummonViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
) {
    val sessionViewModel: AppSessionViewModel = viewModel(
        factory = AppSessionViewModel.factory(appContainer.sessionLocalStore),
    )
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(
            repository = appContainer.authRepository,
            analyticsTracker = appContainer.analyticsTracker,
        ),
    )
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            chatRepository = appContainer.chatRepository,
            companionRepository = appContainer.companionRepository,
            captureRepository = appContainer.captureRepository,
            analyticsTracker = appContainer.analyticsTracker,
        ),
    )
    val summonViewModel: SummonViewModel = viewModel(
        factory = SummonViewModel.factory(
            captureRepository = appContainer.captureRepository,
            analyticsTracker = appContainer.analyticsTracker,
        ),
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            chatRepository = appContainer.chatRepository,
            captureRepository = appContainer.captureRepository,
            accountRepository = appContainer.accountRepository,
            analyticsTracker = appContainer.analyticsTracker,
        ),
    )

    fun navigateToRoot(destination: AppDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun handleUnauthorized(screen: String) {
        appContainer.analyticsTracker.track(
            event = AnalyticsEvents.SESSION_UNAUTHORIZED_CLEARED,
            properties = buildMap {
                put("screen", screen)
                sessionViewModel.uiState.authSession?.userId?.let { put("userId", it) }
            },
        )
        summonViewModel.clearCapturePreview()
        sessionViewModel.clearAuthenticatedState()
        chatViewModel.resetSessionState()
        navigateToRoot(AppDestination.Login)
    }

    fun handleLogout() {
        summonViewModel.clearCapturePreview()
        sessionViewModel.clearAuthenticatedState()
        chatViewModel.resetSessionState()
        navigateToRoot(AppDestination.Login)
    }

    fun handleAccountDeleted() {
        summonViewModel.clearCapturePreview()
        sessionViewModel.clearAuthenticatedState()
        chatViewModel.resetSessionState()
        navigateToRoot(AppDestination.Login)
    }

    fun resolveAuthSuccess() {
        val authSession = sessionViewModel.uiState.authSession ?: return
        chatViewModel.resetSessionState()
        if (authSession.profileCompleted) {
            navigateToRoot(AppDestination.Chat)
        } else {
            navigateToRoot(AppDestination.CompanionSetup)
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Launch.route,
    ) {
        composable(AppDestination.Launch.route) {
            val launchViewModel: LaunchViewModel = viewModel(
                factory = LaunchViewModel.factory(
                    authRepository = appContainer.authRepository,
                    companionRepository = appContainer.companionRepository,
                    captureRepository = appContainer.captureRepository,
                    sessionLocalStore = appContainer.sessionLocalStore,
                    analyticsTracker = appContainer.analyticsTracker,
                ),
            )
            LaunchScreen(
                viewModel = launchViewModel,
                onResolved = { resolution ->
                    sessionViewModel.restorePersistedState(resolution.restoredState)
                    if (resolution.clearAuthenticatedState) {
                        handleUnauthorized("launch")
                    } else {
                        when (resolution.target) {
                            LaunchTarget.LOGIN -> navigateToRoot(AppDestination.Login)
                            LaunchTarget.COMPANION_SETUP -> navigateToRoot(AppDestination.CompanionSetup)
                            LaunchTarget.CHAT -> navigateToRoot(AppDestination.Chat)
                        }
                    }
                },
            )
        }

        composable(AppDestination.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onCodeRequested = {
                    navController.navigate(AppDestination.Verify.route)
                },
            )
        }

        composable(AppDestination.Verify.route) {
            VerifyScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onLoginSuccess = { authSession ->
                    sessionViewModel.setAuthSession(authSession)
                    resolveAuthSuccess()
                },
            )
        }

        composable(AppDestination.CompanionSetup.route) {
            val companionViewModel: CompanionSetupViewModel = viewModel(
                factory = CompanionSetupViewModel.factory(
                    repository = appContainer.companionRepository,
                    analyticsTracker = appContainer.analyticsTracker,
                ),
            )
            CompanionSetupScreen(
                viewModel = companionViewModel,
                authSession = sessionViewModel.uiState.authSession,
                onProfileReady = { profile ->
                    sessionViewModel.setCompanionProfile(profile)
                    chatViewModel.clearConversation(profile.nickname)
                    navigateToRoot(AppDestination.Chat)
                },
                onUnauthorized = { handleUnauthorized("companion_setup") },
            )
        }

        composable(AppDestination.Chat.route) {
            ChatScreen(
                viewModel = chatViewModel,
                sessionViewModel = sessionViewModel,
                onOpenSummon = { navController.navigate(AppDestination.Summon.route) },
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                onProfileRequired = { navigateToRoot(AppDestination.CompanionSetup) },
                onUnauthorized = { handleUnauthorized("chat") },
            )
        }

        composable(AppDestination.Summon.route) {
            SummonExperienceScreen(
                viewModel = summonViewModel,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onUnauthorized = { handleUnauthorized("summon") },
                onOpenCapturePreview = { navController.navigate(AppDestination.CapturePreview.route) },
            )
        }

        composable(AppDestination.CapturePreview.route) {
            CapturePreviewScreen(
                viewModel = summonViewModel,
                onBack = { navController.popBackStack() },
                onBackToChat = {
                    summonViewModel.clearCapturePreview()
                    val popped = navController.popBackStack(AppDestination.Chat.route, false)
                    if (!popped) {
                        navigateToRoot(AppDestination.Chat)
                    }
                },
                onUnauthorized = { handleUnauthorized("capture_preview") },
                authSession = sessionViewModel.uiState.authSession,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
            )
        }

        composable(AppDestination.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onUnauthorized = { handleUnauthorized("settings") },
                onLogout = ::handleLogout,
                onAccountDeleted = ::handleAccountDeleted,
            )
        }
    }
}
