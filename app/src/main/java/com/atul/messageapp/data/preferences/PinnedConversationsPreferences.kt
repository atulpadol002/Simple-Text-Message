package com.atul.messageapp.data.preferences

import android.content.Context

class PinnedConversationsPreferences(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getPinnedThreadIds(): Set<Long> = preferences
        .getStringSet(KEY_PINNED_THREADS, emptySet())
        .orEmpty()
        .mapNotNull(String::toLongOrNull)
        .toSet()

    fun setPinned(threadIds: Set<Long>, pinned: Boolean) {
        if (threadIds.isEmpty()) return
        val ids = getPinnedThreadIds().toMutableSet()
        if (pinned) ids.addAll(threadIds) else ids.removeAll(threadIds)
        save(ids)
    }

    fun removePinnedThreadIds(threadIds: Set<Long>) = setPinned(threadIds, false)

    private fun save(threadIds: Set<Long>) {
        preferences.edit()
            .putStringSet(KEY_PINNED_THREADS, threadIds.map(Long::toString).toSet())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "pinned_conversations_preferences"
        const val KEY_PINNED_THREADS = "pinned_thread_ids"
    }
}
