package com.thetwo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.thetwo.app.navigation.AppNavHost
import com.thetwo.app.network.AppContainer

@Composable
fun THETWOApp() {
    val navController = rememberNavController()
    val appContainer = remember { AppContainer() }
    AppNavHost(
        navController = navController,
        appContainer = appContainer,
    )
}
