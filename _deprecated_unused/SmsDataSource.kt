package com.ap.messages.data.datasource

import android.content.ContentResolver
import android.content.Context
import android.provider.Telephony
import com.ap.messages.data.model.Message

class SmsDataSource(
    private val context: Context
) {

    fun getMessages(conversationId: Long): List<Message> {

        val messages = mutableListOf<Message>()

        val resolver: ContentResolver = context.contentResolver

        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(conversationId.toString())

        val cursor = resolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )

        cursor?.use {

            while (it.moveToNext()) {

                val id = it.getLong(
                    it.getColumnIndexOrThrow(Telephony.Sms._ID)
                )

                val threadId = it.getLong(
                    it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                )

                val address = it.getString(
                    it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                ) ?: ""

                val body = it.getString(
                    it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                ) ?: ""

                val date = it.getLong(
                    it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                )

                val type = it.getInt(
                    it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                )

                val read = it.getInt(
                    it.getColumnIndexOrThrow(Telephony.Sms.READ)
                )

                messages.add(
                    Message(
                        id = id,
                        conversationId = threadId,
                        phoneNumber = address,
                        body = body,
                        timestamp = date,
                        isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        isRead = read == 1
                    )
                )
            }
        }

        return messages
    }
}