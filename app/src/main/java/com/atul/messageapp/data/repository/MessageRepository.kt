package com.atul.messageapp.data.repository

import android.content.Context
import com.atul.messageapp.data.datasource.MessageDataSource
import com.atul.messageapp.data.model.Message
import com.atul.messageapp.sms.SmsSender

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
        onSentResult: (Boolean) -> Unit
    ): Boolean {

        return smsSender.sendSms(
            phoneNumber = phoneNumber,
            message = message,
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
