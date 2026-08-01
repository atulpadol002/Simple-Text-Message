package com.atul.messageapp.data.repository

import android.content.Context
import android.provider.Telephony
import com.atul.messageapp.data.model.DeletedMessage
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

    fun getConversationSnapshotMessages(
        threadId: Long
    ): List<DeletedMessage>? {
        val messages = mutableListOf<DeletedMessage>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.STATUS,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID}=?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            ) ?: return null

            cursor.use {
                val idIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms._ID
                )
                val threadIdIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.THREAD_ID
                )
                val addressIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.ADDRESS
                )
                val bodyIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.BODY
                )
                val dateIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.DATE
                )
                val sentDateIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.DATE_SENT
                )
                val typeIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.TYPE
                )
                val readIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.READ
                )
                val seenIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.SEEN
                )
                val statusIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.STATUS
                )
                val serviceCenterIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.SERVICE_CENTER
                )
                val subscriptionIdIndex = it.getColumnIndexOrThrow(
                    Telephony.Sms.SUBSCRIPTION_ID
                )

                while (it.moveToNext()) {
                    messages.add(
                        DeletedMessage(
                            originalMessageId = it.getLong(idIndex),
                            originalThreadId = it.getLong(threadIdIndex),
                            address = it.getString(addressIndex).orEmpty(),
                            body = it.getString(bodyIndex).orEmpty(),
                            date = it.getLong(dateIndex),
                            sentDate = if (it.isNull(sentDateIndex)) {
                                null
                            } else {
                                it.getLong(sentDateIndex)
                            },
                            type = it.getInt(typeIndex),
                            read = it.getInt(readIndex) == 1,
                            seen = it.getInt(seenIndex) == 1,
                            status = if (it.isNull(statusIndex)) {
                                null
                            } else {
                                it.getInt(statusIndex)
                            },
                            serviceCenter = if (
                                it.isNull(serviceCenterIndex)
                            ) {
                                null
                            } else {
                                it.getString(serviceCenterIndex)
                            },
                            subscriptionId = if (
                                it.isNull(subscriptionIdIndex)
                            ) {
                                null
                            } else {
                                it.getInt(subscriptionIdIndex)
                            }
                        )
                    )
                }
            }

            messages
        } catch (exception: SecurityException) {
            exception.printStackTrace()
            null
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }
    }
}
