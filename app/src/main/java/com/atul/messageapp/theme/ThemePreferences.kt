package com.atul.messageapp.theme

import android.content.Context

class ThemePreferences(
    context: Context
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getThemeMode(): ThemeMode {
        val savedMode = preferences.getString(
            KEY_THEME_MODE,
            ThemeMode.SYSTEM.name
        )

        return try {
            ThemeMode.valueOf(
                savedMode ?: ThemeMode.SYSTEM.name
            )
        } catch (exception: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun saveThemeMode(
        themeMode: ThemeMode
    ) {
        preferences
            .edit()
            .putString(
                KEY_THEME_MODE,
                themeMode.name
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME =
            "message_app_preferences"

        private const val KEY_THEME_MODE =
            "theme_mode"
    }
}