package com.atul.messageapp.data.preferences

import android.content.Context

class RecentEmojiPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getRecentEmojis(): List<String> = preferences.getString(RECENT_EMOJIS_KEY, null)
        ?.split(SEPARATOR)
        ?.filter(String::isNotEmpty)
        .orEmpty()

    fun addEmoji(emoji: String): List<String> {
        val updated = (listOf(emoji) + getRecentEmojis().filterNot { it == emoji })
            .take(MAX_RECENT_EMOJIS)
        preferences.edit().putString(RECENT_EMOJIS_KEY, updated.joinToString(SEPARATOR)).apply()
        return updated
    }

    private companion object {
        const val PREFERENCES_NAME = "recent_emoji_preferences"
        const val RECENT_EMOJIS_KEY = "recent_emojis"
        const val SEPARATOR = "\u001F"
        const val MAX_RECENT_EMOJIS = 30
    }
}
