package com.ap.simpletextmessage.premium

import android.content.Context

object PremiumPopupSession {
    private const val PREFERENCES_NAME = "premium_paywall_popup"
    private const val LAST_SHOWN_SESSION_KEY = "last_shown_session"
    private val eligibleSessions = setOf(1, 3, 5, 7, 9, 11, 13, 15)

    fun shouldShow(context: Context, sessionNumber: Int): Boolean {
        if (sessionNumber !in eligibleSessions) return false
        val lastShown = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(LAST_SHOWN_SESSION_KEY, 0)
        return lastShown != sessionNumber
    }

    fun markShown(context: Context, sessionNumber: Int) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(LAST_SHOWN_SESSION_KEY, sessionNumber)
            .apply()
    }
}
