package com.thetwo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.thetwo.app.navigation.AppNavHost
import com.thetwo.app.network.AppContainer

@Composable
fun THETWOApp() {
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext
    val appContainer = remember(appContext) { AppContainer(appContext) }

    AppNavHost(
        navController = navController,
        appContainer = appContainer,
    )
}
