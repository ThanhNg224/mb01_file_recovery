package com.meta.brain.file.recovery.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

}
