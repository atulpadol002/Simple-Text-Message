package com.ap.simpletextmessage.data.preferences

import android.content.Context
import com.ap.simpletextmessage.data.model.ScheduledSms
import com.ap.simpletextmessage.data.model.ScheduledSmsStatus
import org.json.JSONArray
import org.json.JSONObject

class ScheduledSmsPreferences(
    context: Context
) {

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

    fun getScheduledMessages():
            List<ScheduledSms> {

        val jsonString =
            preferences.getString(
                KEY_SCHEDULED_MESSAGES,
                null
            ) ?: return emptyList()

        return try {

            val jsonArray =
                JSONArray(jsonString)

            buildList {

                for (
                index in 0 until
                        jsonArray.length()
                ) {

                    val jsonObject =
                        jsonArray.getJSONObject(
                            index
                        )

                    add(
                        ScheduledSms(
                            id =
                                jsonObject.getLong(
                                    "id"
                                ),
                            contactName =
                                jsonObject.optString(
                                    "contactName",
                                    ""
                                ),
                            phoneNumber =
                                jsonObject.getString(
                                    "phoneNumber"
                                ),
                            message =
                                jsonObject.getString(
                                    "message"
                                ),
                            scheduledTime =
                                jsonObject.getLong(
                                    "scheduledTime"
                                ),
                            status = runCatching {
                                ScheduledSmsStatus.valueOf(
                                    jsonObject.optString("status", ScheduledSmsStatus.SCHEDULED.name)
                                )
                            }.getOrDefault(ScheduledSmsStatus.SCHEDULED)
                        )
                    )
                }
            }.sortedBy {
                it.scheduledTime
            }

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
            emptyList()
        }
    }

    fun saveScheduledMessage(
        scheduledSms: ScheduledSms
    ): Boolean = synchronized(
        PREFERENCES_LOCK
    ) {

        val messages =
            getScheduledMessages()
                .toMutableList()

        messages.removeAll {
            it.id == scheduledSms.id
        }

        messages.add(
            scheduledSms
        )

        return@synchronized saveMessages(
            messages
        )
    }

    fun deleteScheduledMessage(
        messageId: Long
    ): Unit = synchronized(
        PREFERENCES_LOCK
    ) {

        val messages =
            getScheduledMessages()
                .filterNot {
                    it.id == messageId
                }

        saveMessages(
            messages
        )

        clearMessageEditing(
            messageId
        )
    }

    fun getScheduledMessage(
        messageId: Long
    ): ScheduledSms? {

        return getScheduledMessages()
            .firstOrNull {
                it.id == messageId
            }
    }

    fun updateStatus(messageId: Long, status: ScheduledSmsStatus): Boolean = synchronized(
        PREFERENCES_LOCK
    ) {
        val messages = getScheduledMessages()
        if (messages.none { it.id == messageId }) return@synchronized false
        saveMessages(messages.map { message ->
            if (message.id == messageId) message.copy(status = status) else message
        })
    }

    fun getScheduledMessagesForNumber(
        phoneNumber: String
    ): List<ScheduledSms> {

        val normalizedTarget =
            normalizePhoneNumber(
                phoneNumber
            )

        return getScheduledMessages()
            .filter { scheduledSms ->

                normalizePhoneNumber(
                    scheduledSms.phoneNumber
                ) == normalizedTarget
            }
            .sortedBy {
                it.scheduledTime
            }
    }

    fun markMessageEditing(
        messageId: Long
    ): Boolean = synchronized(
        PREFERENCES_LOCK
    ) {

        val editingIds =
            getEditingMessageIds()
                .toMutableSet()

        editingIds.add(
            messageId.toString()
        )

        return@synchronized preferences
            .edit()
            .putStringSet(
                KEY_EDITING_MESSAGE_IDS,
                editingIds
            )
            .commit()
    }

    fun clearMessageEditing(
        messageId: Long
    ): Boolean = synchronized(
        PREFERENCES_LOCK
    ) {

        val editingIds =
            getEditingMessageIds()
                .toMutableSet()

        editingIds.remove(
            messageId.toString()
        )

        return@synchronized preferences
            .edit()
            .putStringSet(
                KEY_EDITING_MESSAGE_IDS,
                editingIds
            )
            .commit()
    }

    fun isMessageEditing(
        messageId: Long
    ): Boolean {

        return getEditingMessageIds()
            .contains(
                messageId.toString()
            )
    }

    private fun getEditingMessageIds():
            Set<String> {

        return preferences
            .getStringSet(
                KEY_EDITING_MESSAGE_IDS,
                emptySet()
            )
            ?.toSet()
            ?: emptySet()
    }

    private fun saveMessages(
        messages: List<ScheduledSms>
    ): Boolean {

        val jsonArray =
            JSONArray()

        messages
            .sortedBy {
                it.scheduledTime
            }
            .forEach { scheduledSms ->

                val jsonObject =
                    JSONObject().apply {

                        put(
                            "id",
                            scheduledSms.id
                        )

                        put(
                            "contactName",
                            scheduledSms
                                .contactName
                        )

                        put(
                            "phoneNumber",
                            scheduledSms
                                .phoneNumber
                        )

                        put(
                            "message",
                            scheduledSms
                                .message
                        )

                        put(
                            "scheduledTime",
                            scheduledSms
                                .scheduledTime
                        )

                        put("status", scheduledSms.status.name)
                    }

                jsonArray.put(
                    jsonObject
                )
            }

        return preferences
            .edit()
            .putString(
                KEY_SCHEDULED_MESSAGES,
                jsonArray.toString()
            )
            .commit()
    }

    private fun normalizePhoneNumber(
        phoneNumber: String
    ): String {

        val digits =
            phoneNumber.filter {
                it.isDigit()
            }

        return if (
            digits.length > 10
        ) {

            digits.takeLast(
                10
            )

        } else {

            digits
        }
    }

    companion object {

        private val PREFERENCES_LOCK =
            Any()

        private const val PREFS_NAME =
            "scheduled_sms_preferences"

        private const val KEY_SCHEDULED_MESSAGES =
            "scheduled_messages"

        private const val KEY_EDITING_MESSAGE_IDS =
            "editing_scheduled_message_ids"
    }
}
