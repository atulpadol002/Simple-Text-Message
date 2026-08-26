package com.ap.simpletextmessage.data.preferences

import android.content.Context
import android.telephony.PhoneNumberUtils

class ChatDraftPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun keyFor(threadId: Long, address: String): String =
        if (threadId > 0L) {
            "thread:$threadId"
        } else {
            val normalized = PhoneNumberUtils.normalizeNumber(address).ifBlank {
                address.trim().lowercase()
            }
            "address:$normalized"
        }

    fun getDraft(key: String): String = preferences.getString(key, "").orEmpty()

    fun setDraft(key: String, text: String) {
        preferences.edit().apply {
            if (text.isEmpty()) remove(key) else putString(key, text)
        }.apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "chat_drafts"
    }
}
