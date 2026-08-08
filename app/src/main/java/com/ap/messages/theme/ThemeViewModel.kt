package com.ap.messages.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ap.messages.theme.ThemeMode
import com.ap.messages.theme.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val themePreferences =
        ThemePreferences(application)

    private val _themeMode =
        MutableStateFlow(
            themePreferences.getThemeMode()
        )

    val themeMode: StateFlow<ThemeMode> =
        _themeMode.asStateFlow()

    fun changeTheme(
        themeMode: ThemeMode
    ) {
        _themeMode.value = themeMode

        themePreferences.saveThemeMode(
            themeMode
        )
    }
}