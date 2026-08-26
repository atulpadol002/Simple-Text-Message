package com.ap.simpletextmessage.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class LanguagePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun selectedLanguage(): AppLanguage =
        AppLanguage.fromLanguageTag(preferences.getString(SELECTED_LANGUAGE_KEY, ""))

    fun saveSelectedLanguage(language: AppLanguage) {
        preferences.edit().putString(SELECTED_LANGUAGE_KEY, language.languageTag).apply()
    }

    fun isLanguageOnboardingShown(): Boolean =
        preferences.getBoolean(LANGUAGE_ONBOARDING_SHOWN_KEY, false)

    fun markLanguageOnboardingShown() {
        preferences.edit().putBoolean(LANGUAGE_ONBOARDING_SHOWN_KEY, true).apply()
    }

    fun applySelectedLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(LanguageFlowPolicy.localeTags(language))
        )
    }

    companion object {
        const val PREFERENCES_NAME = "language_preferences"
        const val SELECTED_LANGUAGE_KEY = "selected_language"
        const val LANGUAGE_ONBOARDING_SHOWN_KEY = "language_onboarding_shown"
    }
}
