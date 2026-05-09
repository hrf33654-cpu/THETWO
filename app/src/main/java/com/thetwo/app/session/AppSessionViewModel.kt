package com.thetwo.app.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.companion.CompanionProfile
import com.thetwo.app.network.AuthSession
import kotlinx.coroutines.launch

class AppSessionViewModel(
    private val sessionLocalStore: SessionLocalStore,
) : ViewModel() {
    var uiState by mutableStateOf(AppSessionState())
        private set

    fun setAuthSession(session: AuthSession) {
        val isDifferentUser = uiState.authSession?.userId != null && uiState.authSession?.userId != session.userId
        uiState = uiState.copy(
            authSession = session,
            companionProfile = if (isDifferentUser) null else uiState.companionProfile,
            recentCaptureReference = if (isDifferentUser) null else uiState.recentCaptureReference,
        )
        persist { writeAuthSession(session) }
        if (isDifferentUser) {
            persist {
                writeCompanionProfile(null)
                writeRecentCapture(null)
            }
        }
    }

    fun setCompanionProfile(profile: CompanionProfile) {
        uiState = uiState.copy(companionProfile = profile)
        persist { writeCompanionProfile(profile) }
    }

    fun setRecentCapture(reference: RecentCaptureReference) {
        uiState = uiState.copy(recentCaptureReference = reference)
        persist { writeRecentCapture(reference) }
    }

    fun clearRecentCapture() {
        uiState = uiState.copy(recentCaptureReference = null)
        persist { writeRecentCapture(null) }
    }

    fun acceptArPrivacy() {
        uiState = uiState.copy(arPrivacyAccepted = true)
        persist { writeArPrivacyAccepted(true) }
    }

    fun restorePersistedState(state: PersistedSessionState) {
        uiState = AppSessionState(
            authSession = state.authSession,
            companionProfile = state.companionProfile,
            recentCaptureReference = state.recentCaptureReference,
            arPrivacyAccepted = state.arPrivacyAccepted,
        )
    }

    fun clearAuthenticatedState() {
        uiState = uiState.copy(
            authSession = null,
            companionProfile = null,
            recentCaptureReference = null,
        )
        persist { clearAuthenticatedState() }
    }

    private fun persist(action: suspend SessionLocalStore.() -> Unit) {
        viewModelScope.launch {
            runCatching { sessionLocalStore.action() }
        }
    }

    companion object {
        fun factory(sessionLocalStore: SessionLocalStore): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppSessionViewModel(sessionLocalStore = sessionLocalStore)
            }
        }
    }
}
