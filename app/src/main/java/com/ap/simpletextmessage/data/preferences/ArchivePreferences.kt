package com.ap.simpletextmessage.data.preferences

import android.content.Context

class ArchivePreferences(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getArchivedThreadIds(): Set<Long> {
        return preferences
            .getStringSet(
                KEY_ARCHIVED_THREADS,
                emptySet()
            )
            .orEmpty()
            .mapNotNull { threadId ->
                threadId.toLongOrNull()
            }
            .toSet()
    }

    fun archiveConversation(
        threadId: Long
    ) = archiveConversations(setOf(threadId))

    fun archiveConversations(threadIds: Set<Long>) {
        if (threadIds.isEmpty()) return

        synchronized(ARCHIVE_WRITE_LOCK) {
            val archivedIds = getArchivedThreadIds().toMutableSet()
            if (archivedIds.addAll(threadIds)) saveArchivedThreadIds(archivedIds)
        }
    }

    fun unarchiveConversation(
        threadId: Long
    ) = unarchiveConversations(setOf(threadId))

    fun unarchiveConversations(threadIds: Set<Long>) {
        if (threadIds.isEmpty()) return

        synchronized(ARCHIVE_WRITE_LOCK) {
            val archivedIds = getArchivedThreadIds().toMutableSet()
            if (archivedIds.removeAll(threadIds)) saveArchivedThreadIds(archivedIds)
        }
    }

    fun removeArchivedThreadIds(
        threadIds: Set<Long>
    ) = unarchiveConversations(threadIds)

    fun isConversationArchived(
        threadId: Long
    ): Boolean {
        return getArchivedThreadIds()
            .contains(threadId)
    }

    private fun saveArchivedThreadIds(
        threadIds: Set<Long>
    ) {
        val stringIds =
            threadIds.map { threadId ->
                threadId.toString()
            }.toSet()

        preferences
            .edit()
            .putStringSet(
                KEY_ARCHIVED_THREADS,
                stringIds
            )
            .apply()
    }

    companion object {

        private const val PREFS_NAME =
            "archive_preferences"

        private const val KEY_ARCHIVED_THREADS =
            "archived_thread_ids"

        private val ARCHIVE_WRITE_LOCK = Any()
    }
}
