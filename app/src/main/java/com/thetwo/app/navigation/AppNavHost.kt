package com.thetwo.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.thetwo.app.auth.AuthViewModel
import com.thetwo.app.auth.LoginScreen
import com.thetwo.app.chat.ChatScreen
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.companion.CompanionSetupScreen
import com.thetwo.app.companion.CompanionSetupViewModel
import com.thetwo.app.network.AppContainer
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.settings.SettingsScreen
import com.thetwo.app.settings.SettingsViewModel
import com.thetwo.app.summon.SummonScreen
import com.thetwo.app.summon.SummonViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
) {
    val sessionViewModel: AppSessionViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            chatRepository = appContainer.chatRepository,
            companionRepository = appContainer.companionRepository,
            captureRepository = appContainer.captureRepository,
        ),
    )

    fun navigateToLogin() {
        sessionViewModel.clearAuthenticatedState()
        chatViewModel.resetSessionState()
        navController.navigate(AppDestination.Login.route) {
            popUpTo(navController.graph.id) {
                inclusive = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Login.route,
    ) {
        composable(AppDestination.Login.route) {
            val viewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.factory(appContainer.authRepository),
            )
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = { authSession ->
                    sessionViewModel.setAuthSession(authSession)
                    chatViewModel.resetSessionState()
                    if (authSession.profileCompleted) {
                        navController.navigate(AppDestination.Chat.route) {
                            popUpTo(AppDestination.Login.route) {
                                inclusive = true
                            }
                        }
                    } else {
                        navController.navigate(AppDestination.CompanionSetup.route) {
                            popUpTo(AppDestination.Login.route) {
                                inclusive = true
                            }
                        }
                    }
                },
            )
        }

        composable(AppDestination.CompanionSetup.route) {
            val viewModel: CompanionSetupViewModel = viewModel(
                factory = CompanionSetupViewModel.factory(appContainer.companionRepository),
            )
            CompanionSetupScreen(
                viewModel = viewModel,
                authSession = sessionViewModel.uiState.authSession,
                onProfileReady = { profile ->
                    sessionViewModel.setCompanionProfile(profile)
                    chatViewModel.clearConversation(profile.nickname)
                    navController.navigate(AppDestination.Chat.route) {
                        popUpTo(AppDestination.CompanionSetup.route) {
                            inclusive = true
                        }
                    }
                },
                onUnauthorized = ::navigateToLogin,
            )
        }

        composable(AppDestination.Chat.route) {
            ChatScreen(
                viewModel = chatViewModel,
                sessionViewModel = sessionViewModel,
                onOpenSummon = { navController.navigate(AppDestination.Summon.route) },
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                onProfileRequired = {
                    navController.navigate(AppDestination.CompanionSetup.route) {
                        popUpTo(AppDestination.Chat.route) {
                            inclusive = true
                        }
                    }
                },
                onUnauthorized = ::navigateToLogin,
            )
        }

        composable(AppDestination.Summon.route) {
            val viewModel: SummonViewModel = viewModel(
                factory = SummonViewModel.factory(appContainer.captureRepository),
            )
            SummonScreen(
                viewModel = viewModel,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onUnauthorized = ::navigateToLogin,
            )
        }

        composable(AppDestination.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    chatRepository = appContainer.chatRepository,
                    captureRepository = appContainer.captureRepository,
                    accountRepository = appContainer.accountRepository,
                ),
            )
            SettingsScreen(
                viewModel = viewModel,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                onUnauthorized = ::navigateToLogin,
                onAccountDeleted = ::navigateToLogin,
            )
        }
    }
}
