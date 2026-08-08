package com.ap.messages.data.datasource

import android.content.Context
import android.provider.Telephony
import com.ap.messages.data.model.Conversation
import com.ap.messages.utils.ContactUtils

class ConversationDataSource(
    private val context: Context
) {

    fun getConversations(): List<Conversation> {

        val conversations = mutableListOf<Conversation>()
        val addedThreads = mutableSetOf<Long>()

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {

            while (it.moveToNext()) {

                val threadId = it.getLong(
                    it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                )

                if (addedThreads.contains(threadId))
                    continue

                addedThreads.add(threadId)

                val address = it.getString(
                    it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                ) ?: "Unknown"

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
                        name = ContactUtils.getContactName(
                            context,
                            address
                        ),
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