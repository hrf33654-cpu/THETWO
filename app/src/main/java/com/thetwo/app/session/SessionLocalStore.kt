package com.thetwo.app.session

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.companion.CompanionProfile
import com.thetwo.app.network.AuthSession
import kotlinx.coroutines.flow.first
import java.io.File

class SessionLocalStore(
    context: Context,
    private val gson: Gson,
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = {
            File(context.filesDir, "datastore/session.preferences_pb")
        },
    )

    suspend fun read(): PersistedSessionState {
        val preferences = dataStore.data.first()
        val schemaVersion = preferences[schemaVersionKey] ?: 0
        val arPrivacyAccepted = preferences[arPrivacyAcceptedKey] ?: false

        if (schemaVersion != PersistedSessionState.CURRENT_SCHEMA_VERSION) {
            return PersistedSessionState(
                schemaVersion = PersistedSessionState.CURRENT_SCHEMA_VERSION,
                arPrivacyAccepted = arPrivacyAccepted,
            )
        }

        return PersistedSessionState(
            schemaVersion = schemaVersion,
            authSession = preferences[authSessionKey]?.decodeOrNull(),
            companionProfile = preferences[companionProfileKey]?.decodeOrNull(),
            recentCaptureReference = preferences[recentCaptureKey]?.decodeOrNull(),
            arPrivacyAccepted = arPrivacyAccepted,
        )
    }

    suspend fun writeAuthSession(session: AuthSession?) {
        dataStore.edit { preferences ->
            preferences[schemaVersionKey] = PersistedSessionState.CURRENT_SCHEMA_VERSION
            if (session == null) {
                preferences.remove(authSessionKey)
            } else {
                preferences[authSessionKey] = gson.toJson(session)
            }
        }
    }

    suspend fun writeCompanionProfile(profile: CompanionProfile?) {
        dataStore.edit { preferences ->
            preferences[schemaVersionKey] = PersistedSessionState.CURRENT_SCHEMA_VERSION
            if (profile == null) {
                preferences.remove(companionProfileKey)
            } else {
                preferences[companionProfileKey] = gson.toJson(profile)
            }
        }
    }

    suspend fun writeRecentCapture(reference: RecentCaptureReference?) {
        dataStore.edit { preferences ->
            preferences[schemaVersionKey] = PersistedSessionState.CURRENT_SCHEMA_VERSION
            if (reference == null) {
                preferences.remove(recentCaptureKey)
            } else {
                preferences[recentCaptureKey] = gson.toJson(reference)
            }
        }
    }

    suspend fun writeArPrivacyAccepted(accepted: Boolean) {
        dataStore.edit { preferences ->
            preferences[schemaVersionKey] = PersistedSessionState.CURRENT_SCHEMA_VERSION
            preferences[arPrivacyAcceptedKey] = accepted
        }
    }

    suspend fun clearAuthenticatedState() {
        dataStore.edit { preferences ->
            preferences[schemaVersionKey] = PersistedSessionState.CURRENT_SCHEMA_VERSION
            preferences.remove(authSessionKey)
            preferences.remove(companionProfileKey)
            preferences.remove(recentCaptureKey)
        }
    }

    private inline fun <reified T> String.decodeOrNull(): T? {
        return try {
            gson.fromJson(this, T::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    companion object {
        private val schemaVersionKey = intPreferencesKey("schema_version")
        private val authSessionKey = stringPreferencesKey("auth_session")
        private val companionProfileKey = stringPreferencesKey("companion_profile")
        private val recentCaptureKey = stringPreferencesKey("recent_capture")
        private val arPrivacyAcceptedKey = booleanPreferencesKey("ar_privacy_accepted")
    }
}
