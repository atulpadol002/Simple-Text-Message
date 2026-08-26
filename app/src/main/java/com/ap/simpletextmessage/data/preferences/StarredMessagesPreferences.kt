package com.ap.simpletextmessage.data.preferences

import android.content.Context

class StarredMessagesPreferences(
    context: Context
) {

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun getStarredMessageIds(): Set<Long> {

        return preferences
            .getStringSet(
                KEY_STARRED_MESSAGE_IDS,
                emptySet()
            )
            ?.mapNotNull { value ->

                value.toLongOrNull()
            }
            ?.toSet()
            ?: emptySet()
    }

    fun isMessageStarred(
        messageId: Long
    ): Boolean {

        return getStarredMessageIds()
            .contains(messageId)
    }

    fun starMessage(
        messageId: Long
    ): Boolean {

        if (messageId < 0L) {
            return false
        }

        val currentIds =
            getStarredMessageIds()
                .toMutableSet()

        if (!currentIds.add(messageId)) {
            return false
        }

        return saveIds(currentIds)
    }

    fun unstarMessage(
        messageId: Long
    ): Boolean {

        val currentIds =
            getStarredMessageIds()
                .toMutableSet()

        if (!currentIds.remove(messageId)) {
            return false
        }

        return saveIds(currentIds)
    }

    fun removeStarredMessageIds(
        messageIds: Set<Long>
    ): Boolean {

        if (messageIds.isEmpty()) {
            return true
        }

        val currentIds =
            getStarredMessageIds()
                .toMutableSet()

        if (!currentIds.removeAll(messageIds)) {
            return true
        }

        return saveIds(currentIds)
    }

    fun toggleStar(
        messageId: Long
    ): Boolean {

        return if (
            isMessageStarred(messageId)
        ) {

            unstarMessage(messageId)

        } else {

            starMessage(messageId)
        }
    }

    private fun saveIds(
        messageIds: Set<Long>
    ): Boolean {

        val stringIds =
            messageIds.map { messageId ->

                messageId.toString()
            }.toSet()

        return preferences
            .edit()
            .putStringSet(
                KEY_STARRED_MESSAGE_IDS,
                stringIds
            )
            .commit()
    }

    companion object {

        private const val PREFERENCES_NAME =
            "starred_messages_preferences"

        private const val KEY_STARRED_MESSAGE_IDS =
            "starred_message_ids"
    }
}
