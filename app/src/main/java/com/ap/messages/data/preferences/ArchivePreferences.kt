package com.ap.messages.data.preferences

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
    ) {
        val archivedIds =
            getArchivedThreadIds()
                .toMutableSet()

        archivedIds.add(threadId)

        saveArchivedThreadIds(
            archivedIds
        )
    }

    fun unarchiveConversation(
        threadId: Long
    ) {
        val archivedIds =
            getArchivedThreadIds()
                .toMutableSet()

        if (!archivedIds.remove(threadId)) {
            return
        }

        saveArchivedThreadIds(
            archivedIds
        )
    }

    fun removeArchivedThreadIds(
        threadIds: Set<Long>
    ) {

        if (threadIds.isEmpty()) {
            return
        }

        val archivedIds =
            getArchivedThreadIds()
                .toMutableSet()

        if (!archivedIds.removeAll(threadIds)) {
            return
        }

        saveArchivedThreadIds(
            archivedIds
        )
    }

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
    }
}
