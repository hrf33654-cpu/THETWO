package com.thetwo.app.navigation

sealed class AppDestination(val route: String) {
    data object Launch : AppDestination("launch")
    data object Login : AppDestination("login")
    data object Verify : AppDestination("verify")
    data object CompanionSetup : AppDestination("companion_setup")
    data object Chat : AppDestination("chat")
    data object Summon : AppDestination("summon")
    data object CapturePreview : AppDestination("capture_preview")
    data object Settings : AppDestination("settings")
}
