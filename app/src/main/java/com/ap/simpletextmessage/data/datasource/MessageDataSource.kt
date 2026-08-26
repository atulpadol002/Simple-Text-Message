package com.ap.simpletextmessage.data.datasource

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.ap.simpletextmessage.data.model.Message
import com.ap.simpletextmessage.data.model.MessageStatus

class MessageDataSource(
    private val context: Context
) {

    fun getMessage(messageId: Long): Message? {
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ
                ),
                "${Telephony.Sms._ID}=?", arrayOf(messageId.toString()), null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                Message(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                    conversationId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
                    phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty(),
                    body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty(),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                    isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
                    status = statusForType(type)
                )
            }
        } catch (_: Exception) { null }
    }

    fun getMessages(conversationId: Long): List<Message> {

        val messages = mutableListOf<Message>()

        return try {

            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                "${Telephony.Sms.THREAD_ID}=?",
                arrayOf(conversationId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )

            cursor?.use {

                while (it.moveToNext()) {

                    val id = it.getLong(
                        it.getColumnIndexOrThrow(Telephony.Sms._ID)
                    )

                    val body = it.getString(
                        it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    ) ?: ""

                    val address = it.getString(
                        it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    ) ?: ""

                    val date = it.getLong(
                        it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    )

                    val type = it.getInt(
                        it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                    )

                    val read = it.getInt(
                        it.getColumnIndexOrThrow(Telephony.Sms.READ)
                    ) == 1

                    val status = statusForType(type)

                    messages.add(
                        Message(
                            id = id,
                            conversationId = conversationId,
                            phoneNumber = address,
                            body = body,
                            timestamp = date,
                            isIncoming =
                            type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                            isRead = read,
                            status = status
                        )
                    )
                }
            }

            messages

        } catch (exception: SecurityException) {

            exception.printStackTrace()
            emptyList()

        } catch (exception: RuntimeException) {

            exception.printStackTrace()
            emptyList()
        }
    }

    fun insertOutgoingMessage(
        phoneNumber: String,
        body: String
    ): Long {

        return try {

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phoneNumber)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(
                    Telephony.Sms.TYPE,
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX
                )
            }

            val uri = context.contentResolver.insert(
                Telephony.Sms.CONTENT_URI,
                values
            )

            uri?.lastPathSegment
                ?.toLongOrNull()
                ?: -1L

        } catch (exception: SecurityException) {

            exception.printStackTrace()
            -1L

        } catch (exception: Exception) {

            exception.printStackTrace()
            -1L
        }
    }

    fun getThreadIdForMessage(
        messageId: Long
    ): Long {

        return try {

            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms._ID}=?",
                arrayOf(messageId.toString()),
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            Telephony.Sms.THREAD_ID
                        )
                    )
                } else {
                    0L
                }
            } ?: 0L

        } catch (exception: SecurityException) {

            exception.printStackTrace()
            0L

        } catch (exception: Exception) {

            exception.printStackTrace()
            0L
        }
    }

    fun markMessageSending(
        messageId: Long
    ): Boolean {

        return updateMessageType(
            messageId = messageId,
            type = Telephony.Sms.MESSAGE_TYPE_OUTBOX
        )
    }

    fun markMessageSent(
        messageId: Long
    ): Boolean {

        return updateMessageType(
            messageId = messageId,
            type = Telephony.Sms.MESSAGE_TYPE_SENT
        )
    }

    fun markMessageFailed(
        messageId: Long
    ): Boolean {

        return updateMessageType(
            messageId = messageId,
            type = Telephony.Sms.MESSAGE_TYPE_FAILED
        )
    }

    fun markThreadAsRead(
        conversationId: Long
    ): Boolean {

        return try {

            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }

            val rowsUpdated =
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0",
                    arrayOf(conversationId.toString())
                )

            rowsUpdated >= 0

        } catch (exception: SecurityException) {

            exception.printStackTrace()
            false

        } catch (exception: Exception) {

            exception.printStackTrace()
            false
        }
    }
    fun deleteMessage(
        messageId: Long
    ): Boolean {

        return try {

            val deletedRows =
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID}=?",
                    arrayOf(messageId.toString())
                )

            deletedRows > 0

        } catch (exception: SecurityException) {

            exception.printStackTrace()
            false

        } catch (exception: Exception) {

            exception.printStackTrace()
            false
        }
    }

    private fun updateMessageType(
        messageId: Long,
        type: Int
    ): Boolean {

        return try {

            val values = ContentValues().apply {
                put(Telephony.Sms.TYPE, type)
            }

            val rowsUpdated =
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID}=?",
                    arrayOf(messageId.toString())
                )

            rowsUpdated > 0

        } catch (exception: SecurityException) {

            exception.printStackTrace()
            false

        } catch (exception: Exception) {

            exception.printStackTrace()
            false
        }
    }

    private fun statusForType(type: Int): MessageStatus = when (type) {
        Telephony.Sms.MESSAGE_TYPE_OUTBOX,
        Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
        Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
        Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
        else -> MessageStatus.NONE
    }
}
