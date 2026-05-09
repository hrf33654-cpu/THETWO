package com.thetwo.app.launch

import com.thetwo.app.session.PersistedSessionState

enum class LaunchTarget {
    LOGIN,
    COMPANION_SETUP,
    CHAT,
}

data class LaunchResolution(
    val target: LaunchTarget,
    val restoredState: PersistedSessionState,
    val clearAuthenticatedState: Boolean = false,
)

data class LaunchUiState(
    val isRestoring: Boolean = false,
    val errorMessage: String? = null,
    val resolution: LaunchResolution? = null,
)
