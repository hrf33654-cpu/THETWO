package com.thetwo.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.thetwo.app.navigation.AppNavHost

@Composable
fun THETWOApp() {
    val navController = rememberNavController()
    AppNavHost(navController = navController)
}
