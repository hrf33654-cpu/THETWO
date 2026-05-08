package com.thetwo.app.navigation

sealed class AppDestination(val route: String) {
    data object Login : AppDestination("login")
    data object CompanionSetup : AppDestination("companion_setup")
    data object Chat : AppDestination("chat")
    data object Summon : AppDestination("summon")
    data object Settings : AppDestination("settings")
}
