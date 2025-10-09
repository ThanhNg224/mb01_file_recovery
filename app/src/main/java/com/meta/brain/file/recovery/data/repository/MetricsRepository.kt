package com.meta.brain.file.recovery.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferencesKeys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val onboardingDone: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_DONE] ?: false
        }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_DONE] = done
        }
    }
}
