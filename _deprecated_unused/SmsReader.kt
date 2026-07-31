package com.atul.messageapp.sms

import android.content.Context
import android.provider.Telephony
import com.atul.messageapp.data.model.Conversation
import com.atul.messageapp.contact.ContactNameResolver

class SmsReader(
    private val context: Context
) {

    fun getConversations(): List<Conversation> {

        val conversations = mutableListOf<Conversation>()
        val contactResolver = ContactNameResolver(context)
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {

            val addedThreads = mutableSetOf<Long>()

            while (it.moveToNext()) {

                val threadId = it.getLong(
                    it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                )

                if (addedThreads.contains(threadId)) {
                    continue
                }

                addedThreads.add(threadId)

                val address = it.getString(
                    it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                ) ?: "Unknown"
                val contactName = contactResolver.getContactName(address)

                val body = it.getString(
                    it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                ) ?: ""

                val date = it.getLong(
                    it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                )

                conversations.add(

                    Conversation(
                        id = threadId,
                        phoneNumber = address,
                        name = contactName,
                        lastMessage = body,
                        lastMessageTime = date,
                        unreadCount = 0
                    )

                )

            }

        }

        return conversations

    }

}