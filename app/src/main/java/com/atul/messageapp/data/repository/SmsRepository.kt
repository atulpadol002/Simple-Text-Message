package com.atul.messageapp.data.repository

import android.content.Context
import android.provider.Telephony
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.data.model.SmsMessage

class SmsRepository(
    private val context: Context
) {

    fun getConversations(): List<SmsConversation> {

        val latestConversationByThread =
            linkedMapOf<Long, SmsConversation>()

        val unreadCountByThread =
            mutableMapOf<Long, Int>()

        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        val sortOrder =
            "${Telephony.Sms.DATE} DESC"

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->

            val threadIdIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.THREAD_ID
                )

            val addressIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.ADDRESS
                )

            val bodyIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.BODY
                )

            val dateIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.DATE
                )

            val readIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.READ
                )

            while (cursor.moveToNext()) {

                val threadId =
                    cursor.getLong(threadIdIndex)

                val isRead =
                    cursor.getInt(readIndex) == 1

                if (!isRead) {
                    unreadCountByThread[threadId] =
                        unreadCountByThread
                            .getOrDefault(threadId, 0) + 1
                }

                if (threadId !in latestConversationByThread) {

                    latestConversationByThread[threadId] =
                        SmsConversation(
                            threadId = threadId,
                            address = cursor
                                .getString(addressIndex)
                                ?: "Unknown",
                            body = cursor
                                .getString(bodyIndex)
                                ?: "",
                            date = cursor
                                .getLong(dateIndex),
                            read = isRead,
                            unreadCount = 0
                        )
                }
            }
        }

        return latestConversationByThread
            .values
            .map { conversation ->

                val unreadCount =
                    unreadCountByThread
                        .getOrDefault(
                            conversation.threadId,
                            0
                        )

                conversation.copy(
                    read = unreadCount == 0,
                    unreadCount = unreadCount
                )
            }
    }

    fun getMessages(
        threadId: Long
    ): List<SmsMessage> {

        val messages =
            mutableListOf<SmsMessage>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val selection =
            "${Telephony.Sms.THREAD_ID} = ?"

        val selectionArgs =
            arrayOf(threadId.toString())

        val sortOrder =
            "${Telephony.Sms.DATE} ASC"

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms._ID
                )

            val threadIdIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.THREAD_ID
                )

            val addressIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.ADDRESS
                )

            val bodyIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.BODY
                )

            val dateIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.DATE
                )

            val typeIndex =
                cursor.getColumnIndexOrThrow(
                    Telephony.Sms.TYPE
                )

            while (cursor.moveToNext()) {

                messages.add(
                    SmsMessage(
                        id = cursor.getLong(idIndex),
                        threadId = cursor.getLong(
                            threadIdIndex
                        ),
                        address = cursor
                            .getString(addressIndex)
                            ?: "Unknown",
                        body = cursor
                            .getString(bodyIndex)
                            ?: "",
                        date = cursor
                            .getLong(dateIndex),
                        type = cursor
                            .getInt(typeIndex)
                    )
                )
            }
        }

        return messages
    }
}