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
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.settings.SettingsScreen
import com.thetwo.app.summon.SummonScreen
import com.thetwo.app.summon.SummonViewModel

@Composable
fun AppNavHost(navController: NavHostController) {
    val sessionViewModel: AppSessionViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = AppDestination.Login.route,
    ) {
        composable(AppDestination.Login.route) {
            val viewModel: AuthViewModel = viewModel()
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    sessionViewModel.setLoginEmail(viewModel.currentEmail())
                    navController.navigate(AppDestination.CompanionSetup.route) {
                        popUpTo(AppDestination.Login.route) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(AppDestination.CompanionSetup.route) {
            val viewModel: CompanionSetupViewModel = viewModel()
            CompanionSetupScreen(
                viewModel = viewModel,
                onProfileReady = { profile ->
                    sessionViewModel.setCompanionProfile(profile)
                    chatViewModel.clearConversation(profile.nickname)
                    navController.navigate(AppDestination.Chat.route) {
                        popUpTo(AppDestination.CompanionSetup.route) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(AppDestination.Chat.route) {
            ChatScreen(
                viewModel = chatViewModel,
                sessionViewModel = sessionViewModel,
                onOpenSummon = { navController.navigate(AppDestination.Summon.route) },
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
            )
        }

        composable(AppDestination.Summon.route) {
            val viewModel: SummonViewModel = viewModel()
            SummonScreen(
                viewModel = viewModel,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.Settings.route) {
            SettingsScreen(
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
