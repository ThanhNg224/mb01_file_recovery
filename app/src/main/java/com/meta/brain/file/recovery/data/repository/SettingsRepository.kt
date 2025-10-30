package com.meta.brain.file.recovery.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.meta.brain.file.recovery.data.model.DateFormat
import com.meta.brain.file.recovery.data.model.Language
import com.meta.brain.file.recovery.data.model.Theme
import com.meta.brain.module.data.DataManager
import com.meta.brain.module.utils.Utility
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing app settings
 * Handles persistence of user preferences like theme, language, date format, etc.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_DATE_FORMAT = "date_format"
        private const val KEY_REMINDER = "reminder_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get current theme setting
     */
    fun getCurrentTheme(): Theme {
        val themeId = prefs.getString(KEY_THEME, Theme.AUTO) ?: Theme.AUTO
        return Theme.getThemeById(themeId)
    }

    /**
     * Save theme setting
     */
    fun saveTheme(theme: Theme) {
        prefs.edit { putString(KEY_THEME, theme.id) }
    }

    /**
     * Get current language setting
     */
    fun getCurrentLanguage(): Language {
        val languageCode = DataManager.user.language
        return Language.getLanguageByCode(languageCode)
    }

    /**
     * Save language setting
     */
    fun saveLanguage(language: Language) {
        DataManager.user.language = language.code
        DataManager.saveData(context)
        Utility.setLocale(context)
    }

    /**
     * Get current date format setting
     */
    fun getCurrentDateFormat(): DateFormat {
        val formatId = prefs.getString(KEY_DATE_FORMAT, DateFormat.DEFAULT_FORMAT_ID)
            ?: DateFormat.DEFAULT_FORMAT_ID
        return DateFormat.getDateFormatById(formatId)
    }

    /**
     * Save date format setting
     */
    fun saveDateFormat(dateFormat: DateFormat) {
        prefs.edit { putString(KEY_DATE_FORMAT, dateFormat.id) }
    }

    /**
     * Get reminder enabled state
     */
    fun isReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER, false)
    }

    /**
     * Save reminder enabled state
     */
    fun saveReminderEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMINDER, enabled) }
    }

    /**
     * Get app version name
     */
    fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    /**
     * Get app package name
     */
    fun getPackageName(): String {
        return context.packageName
    }
}

