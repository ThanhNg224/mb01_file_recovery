package com.meta.brain.file.recovery.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private companion object {
        val START_TOUCHES_KEY = intPreferencesKey("start_touches")
    }

    val startTouchesFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[START_TOUCHES_KEY] ?: 0
        }

    suspend fun incrementStartTouches(): Int {
        var newCount = 0
        dataStore.edit { preferences ->
            val currentCount = preferences[START_TOUCHES_KEY] ?: 0
            newCount = currentCount + 1
            preferences[START_TOUCHES_KEY] = newCount
        }
        return newCount
    }
}
