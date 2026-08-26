package com.ap.simpletextmessage.data.repository

import android.content.Context
import com.ap.simpletextmessage.data.datasource.MessageDataSource
import com.ap.simpletextmessage.data.model.Message
import com.ap.simpletextmessage.sms.SmsSender
import com.ap.simpletextmessage.utils.isReplyCapableAddress

class MessageRepository(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val dataSource =
        MessageDataSource(appContext)

    private val smsSender =
        SmsSender(appContext)

    fun getMessages(
        conversationId: Long
    ): List<Message> {

        return dataSource.getMessages(
            conversationId
        )
    }

    fun getMessage(messageId: Long): Message? = dataSource.getMessage(messageId)

    fun insertOutgoingMessage(
        phoneNumber: String,
        body: String
    ): Long {

        if (!isReplyCapableAddress(phoneNumber)) return -1L

        return dataSource.insertOutgoingMessage(
            phoneNumber = phoneNumber,
            body = body
        )
    }

    fun getThreadIdForMessage(
        messageId: Long
    ): Long {

        return dataSource.getThreadIdForMessage(
            messageId
        )
    }

    fun sendSms(
        phoneNumber: String,
        message: String,
        messageId: Long = -1L,
        scheduledId: Long = -1L,
        onSentResult: (Boolean) -> Unit
    ): Boolean {

        if (!isReplyCapableAddress(phoneNumber)) return false

        return smsSender.sendSms(
            phoneNumber = phoneNumber,
            message = message,
            messageId = messageId,
            scheduledId = scheduledId,
            onSentResult = onSentResult
        )
    }

    fun markMessageSending(
        messageId: Long
    ): Boolean {

        return dataSource.markMessageSending(
            messageId
        )
    }

    fun markMessageSent(
        messageId: Long
    ): Boolean {

        return dataSource.markMessageSent(
            messageId
        )
    }

    fun markMessageFailed(
        messageId: Long
    ): Boolean {

        return dataSource.markMessageFailed(
            messageId
        )
    }

    fun markThreadAsRead(
        conversationId: Long
    ): Boolean {

        return dataSource.markThreadAsRead(
            conversationId
        )
    }
    fun deleteMessage(
        messageId: Long
    ): Boolean {

        return dataSource.deleteMessage(
            messageId = messageId
        )
    }
}
